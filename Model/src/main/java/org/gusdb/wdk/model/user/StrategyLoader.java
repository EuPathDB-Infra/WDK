package org.gusdb.wdk.model.user;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.gusdb.fgputil.FormatUtil.NL;
import static org.gusdb.fgputil.FormatUtil.join;
import static org.gusdb.fgputil.db.SqlUtils.fetchNullableBoolean;
import static org.gusdb.fgputil.db.SqlUtils.fetchNullableInteger;
import static org.gusdb.fgputil.db.SqlUtils.fetchNullableLong;
import static org.gusdb.fgputil.functional.Functions.fSwallow;
import static org.gusdb.fgputil.functional.Functions.getMapFromList;
import static org.gusdb.wdk.model.user.StepFactory.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.ListBuilder;
import org.gusdb.fgputil.Tuples.TwoTuple;
import org.gusdb.fgputil.db.platform.DBPlatform;
import org.gusdb.fgputil.db.runner.SQLRunner;
import org.gusdb.fgputil.functional.Functions;
import org.gusdb.fgputil.validation.ValidationLevel;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.answer.spec.AnswerSpec;
import org.gusdb.wdk.model.user.Step.StepBuilder;
import org.gusdb.wdk.model.user.Strategy.StrategyBuilder;
import org.json.JSONObject;

public class StrategyLoader {

  private static final Logger LOG = Logger.getLogger(StrategyLoader.class);

  private static final String STEP_COLUMNS = join(
      asList(STEP_TABLE_COLUMNS).stream()
      .map(col -> "st." + col)
      .collect(toList()), ", ");

  private static final List<String> STRAT_COLUMNS =
      asList(STRATEGY_TABLE_COLUMNS).stream()
      .filter(col -> !COLUMN_STRATEGY_ID.equals(col))
      .collect(toList());

  private static final String VALUED_STRAT_COLUMNS = mappedColumnSelection(STRAT_COLUMNS, col -> "sr." + col + " as " + toStratCol(col));
  private static final String NULLED_STRAT_COLUMNS = mappedColumnSelection(STRAT_COLUMNS, col -> "NULL as " + toStratCol(col));

  // macros to fill in searches
  private static final String USER_SCHEMA_MACRO = "$$USER_SCHEMA$$";
  private static final String PROJECT_ID_MACRO = "$$PROJECT_ID$$";
  private static final String IS_DELETED_VALUE_MACRO = "$$IS_DELETED_BOOLEAN_VALUE$$";
  private static final String SEARCH_CONDITIONS_MACRO = "$$SEARCH_CONDITIONS$$";

  // to find steps
  private static final String FIND_STEPS_SQL =
      "(" +
      "  select " + STEP_COLUMNS + ", " + VALUED_STRAT_COLUMNS +
      "  from " + USER_SCHEMA_MACRO + TABLE_STRATEGY + " sr," +
      "       " + USER_SCHEMA_MACRO + TABLE_STEP + " st_find," +
      "       " + USER_SCHEMA_MACRO + TABLE_STEP + " st" +
      "  where st_find." + COLUMN_STRATEGY_ID + " is not null" +
      "    and sr." + COLUMN_STRATEGY_ID + " = st_find." + COLUMN_STRATEGY_ID +
      "    and st." + COLUMN_STRATEGY_ID + " = st_find." + COLUMN_STRATEGY_ID +
      "    and sr." + COLUMN_PROJECT_ID + " = '" + PROJECT_ID_MACRO + "'" +
      "    and sr." + COLUMN_IS_DELETED + " = " + IS_DELETED_VALUE_MACRO +
      "    and st." + COLUMN_IS_DELETED + " = " + IS_DELETED_VALUE_MACRO +
      "    " + SEARCH_CONDITIONS_MACRO +
      ")" +
      " union all" +
      "(" +
      "  select " + STEP_COLUMNS + ", " + NULLED_STRAT_COLUMNS +
      "  from " + USER_SCHEMA_MACRO + TABLE_STEP + " st" +
      "  where st." + COLUMN_STRATEGY_ID + " is null" +
      "    and st." + COLUMN_PROJECT_ID + " = '" + PROJECT_ID_MACRO + "'" +
      "    and st." + COLUMN_IS_DELETED + " = " + IS_DELETED_VALUE_MACRO +
      "    " + SEARCH_CONDITIONS_MACRO +
      ")" +
      " order by " + COLUMN_STRATEGY_ID;

  // to find strategies
  private static final String FIND_STRATEGIES_SQL =
      "select " + STEP_COLUMNS + ", " + VALUED_STRAT_COLUMNS +
      "  from " + USER_SCHEMA_MACRO + TABLE_STRATEGY + " sr," +
      "       " + USER_SCHEMA_MACRO + TABLE_STEP + " st" +
      "  where st." + COLUMN_STRATEGY_ID + " = sr." + COLUMN_STRATEGY_ID +
      "    and sr." + COLUMN_PROJECT_ID + " = '" + PROJECT_ID_MACRO + "'" +
      "    and sr." + COLUMN_IS_DELETED + " = " + IS_DELETED_VALUE_MACRO +
      "    and st." + COLUMN_IS_DELETED + " = " + IS_DELETED_VALUE_MACRO +
      "    " + SEARCH_CONDITIONS_MACRO +
      "  order by " + COLUMN_STRATEGY_ID;

  private static final Comparator<? super Step> STEP_COMPARATOR_LAST_RUN_TIME_DESC =
      (s1, s2) -> s2.getLastRunTime().compareTo(s1.getLastRunTime());
  private static final Comparator<? super Strategy> STRATEGY_COMPARATOR_LAST_MOD_TIME_DESC =
      (s1, s2) -> s2.getLastModifiedTime().compareTo(s1.getLastModifiedTime());

  private final WdkModel _wdkModel;
  private final DataSource _userDbDs;
  private final DBPlatform _userDbPlatform;
  private final String _userSchema;
  private final UserFactory _userFactory;
  private final ValidationLevel _validationLevel;

  public StrategyLoader(WdkModel wdkModel, ValidationLevel validationLevel) {
    _wdkModel = wdkModel;
    _userDbDs = wdkModel.getUserDb().getDataSource();
    _userDbPlatform = wdkModel.getUserDb().getPlatform();
    _userSchema = wdkModel.getModelConfig().getUserDB().getUserSchema();
    _userFactory = wdkModel.getUserFactory();
    _validationLevel = validationLevel;
  }

  private String sqlBoolean(boolean boolValue) {
    return _userDbPlatform.convertBoolean(boolValue).toString();
  }

  private String prepareSql(String sql) {
    return sql
        .replace(USER_SCHEMA_MACRO, _userSchema)
        .replace(PROJECT_ID_MACRO, _wdkModel.getProjectId())
        .replace(IS_DELETED_VALUE_MACRO, sqlBoolean(false));
  }

  private SearchResult doSearch(String sql) throws WdkModelException {
    return doSearch(sql, new Object[0], new Integer[0]);
  }

  private SearchResult doSearch(String sql, Object[] paramValues, Integer[] paramTypes) throws WdkModelException {
    List<StrategyBuilder> strategies = new ArrayList<>();
    List<StepBuilder> orphanSteps = new ArrayList<>();
    try {
      new SQLRunner(_userDbDs, sql, "search-steps-strategies").executeQuery(paramValues, paramTypes, rs -> {
        StrategyBuilder currentStrategy = null;
        while(rs.next()) {
          // read a row
          long nextStrategyId = rs.getLong(COLUMN_STRATEGY_ID);
          if (rs.wasNull()) {
            // this step row has no strategy ID
            if (currentStrategy != null) {
              // save off current strategy and reset
              strategies.add(currentStrategy);
              currentStrategy = null;
            }
            // read orphan step and save off
            orphanSteps.add(readStep(rs));
          }
          else {
            // this step row has a strategy ID
            if (currentStrategy != null) {
              // check to see if this row part of current strategy or beginning of next one
              if (currentStrategy.getStrategyId() == nextStrategyId) {
                // part of current; add step to current strategy
                currentStrategy.addStep(readStep(rs));
              }
              else {
                // beginning of next strategy; save off current strat then read and make next strat current
                strategies.add(currentStrategy);
                currentStrategy = readStrategy(rs); // will also read/add step
              }
            }
            else {
              // no current strategy to save off; start new one with this step row
              currentStrategy = readStrategy(rs); // will also read/add step
            }
          }
        }
        // check for leftover strategy to save
        if (currentStrategy != null) {
          strategies.add(currentStrategy);
        }
        return this;
      });
      // all data loaded; build steps and strats at the specified validation level
      UserCache userCache = new UserCache(_userFactory);
      List<Strategy> builtStrategies = new ArrayList<>();
      MalformedStrategyList malstructuredStrategies = new MalformedStrategyList();
      for (StrategyBuilder stratBuilder : strategies) {
        try {
          builtStrategies.add(stratBuilder.build(userCache, _validationLevel));
        }
        catch (InvalidStrategyStructureException e) {
          malstructuredStrategies.add(new TwoTuple<>(stratBuilder, e));
        }
      }
      // only build orphan steps; attached steps will be built by their strategy
      List<Step> builtOrphanSteps = Functions.mapToList(orphanSteps,
          fSwallow(builder -> builder.build(userCache, _validationLevel, Optional.empty())));
      return new SearchResult(builtStrategies, builtOrphanSteps, malstructuredStrategies);
    }
    catch (Exception e) {
      LOG.error("Unable to execute search with SQL: " + NL + sql + NL + "and params [" + FormatUtil.join(paramValues, ",") + "].");
      return WdkModelException.unwrap(e);
    }
  }

  private StrategyBuilder readStrategy(ResultSet rs) throws SQLException {

    long strategyId = rs.getLong(COLUMN_STRATEGY_ID);
    long userId = rs.getLong(toStratCol(COLUMN_USER_ID));

    StrategyBuilder strat = Strategy.builder(_wdkModel, userId, strategyId)
        .setProjectId(rs.getString(toStratCol(COLUMN_PROJECT_ID)))
        .setVersion(rs.getString(toStratCol(COLUMN_VERSION)))
        .setCreatedTime(rs.getTimestamp(toStratCol(COLUMN_CREATE_TIME)))
        .setDeleted(rs.getBoolean(toStratCol(COLUMN_IS_DELETED)))
        .setRootStepId(rs.getLong(toStratCol(COLUMN_ROOT_STEP_ID)))
        .setSaved(rs.getBoolean(toStratCol(COLUMN_IS_SAVED)))
        .setLastRunTime(rs.getTimestamp(toStratCol(COLUMN_LAST_VIEWED_TIME)))
        .setLastModifiedTime(rs.getTimestamp(toStratCol(COLUMN_LAST_MODIFIED_TIME)))
        .setDescription(rs.getString(toStratCol(COLUMN_DESCRIPTION)))
        .setSignature(rs.getString(toStratCol(COLUMN_SIGNATURE)))
        .setName(rs.getString(toStratCol(COLUMN_NAME)))
        .setSavedName(rs.getString(toStratCol(COLUMN_SAVED_NAME)))
        .setIsPublic(fetchNullableBoolean(rs, toStratCol(COLUMN_IS_PUBLIC), false)); // null = false (not public)

    return strat.addStep(readStep(rs));
  }

  private StepBuilder readStep(ResultSet rs) throws SQLException {

    long stepId = rs.getLong(COLUMN_STEP_ID);
    long userId = rs.getLong(COLUMN_USER_ID);
    String displayPrefs = rs.getString(COLUMN_DISPLAY_PREFS);

    return Step.builder(_wdkModel, userId, stepId)
        .setStrategyId(Optional.ofNullable(fetchNullableLong(rs, COLUMN_STRATEGY_ID, null)))
        .setProjectId(rs.getString(COLUMN_PROJECT_ID))
        .setProjectVersion(rs.getString(COLUMN_PROJECT_VERSION))
        .setCreatedTime(rs.getTimestamp(COLUMN_CREATE_TIME))
        .setLastRunTime(rs.getTimestamp(COLUMN_LAST_RUN_TIME))
        .setEstimatedSize(rs.getInt(COLUMN_ESTIMATE_SIZE))
        .setDeleted(rs.getBoolean(COLUMN_IS_DELETED))
        // no longer load these; Step will figure out which params hold these values
        //.setPreviousStepId(fetchNullableLong(rs, COLUMN_LEFT_CHILD_ID, null))
        //.setChildStepId(fetchNullableLong(rs, COLUMN_RIGHT_CHILD_ID, null))
        .setCustomName(rs.getString(COLUMN_CUSTOM_NAME))
        .setCollapsedName(rs.getString(COLUMN_COLLAPSED_NAME))
        .setCollapsible(rs.getBoolean(COLUMN_IS_COLLAPSIBLE))
        .setAnswerSpec(
            AnswerSpec.builder(_wdkModel)
            .setQuestionFullName(rs.getString(COLUMN_QUESTION_NAME))
            .setLegacyFilterName(Optional.ofNullable(rs.getString(COLUMN_ANSWER_FILTER)))
            .setAssignedWeight(fetchNullableInteger(rs, COLUMN_ASSIGNED_WEIGHT, 0))
            .setDbParamFiltersJson(new JSONObject(_userDbPlatform.getClobData(rs, COLUMN_DISPLAY_PARAMS)))
        )
        .setDisplayPrefs(displayPrefs == null ? new JSONObject() : new JSONObject(displayPrefs));
  }

  public Optional<Step> getStepById(long stepId) throws WdkModelException {
    String sql = prepareSql(FIND_STEPS_SQL
        .replace(SEARCH_CONDITIONS_MACRO, "and st." + COLUMN_STEP_ID + " = " + stepId));
    SearchResult result = doSearch(sql);
    LOG.info("Found following result searching for step with ID " + stepId + ": " + result);
    return result.findFirstOverallStep(st -> st.getStepId() == stepId);
  }

  public Optional<Strategy> getStrategyById(long strategyId) throws WdkModelException {
    String sql = prepareSql(FIND_STRATEGIES_SQL
        .replace(SEARCH_CONDITIONS_MACRO, "and sr." + COLUMN_STRATEGY_ID + " = " + strategyId));
    return doSearch(sql).getOnlyStrategy("with strategy ID = " + strategyId);
  }

  List<Strategy> getPublicStrategies() throws WdkModelException {
    String sql = prepareSql(FIND_STRATEGIES_SQL
        .replace(SEARCH_CONDITIONS_MACRO, "and sr." + COLUMN_IS_PUBLIC + " = " + sqlBoolean(true)));
    return descModTimeSort(doSearch(sql).getStrategies());
  }

  Map<Long, Step> getSteps(Long userId) throws WdkModelException {
    String sql = prepareSql(FIND_STEPS_SQL
        .replace(SEARCH_CONDITIONS_MACRO, "and st." + COLUMN_USER_ID + " = " + userId));
    List<Step> steps = doSearch(sql).findAllSteps(step -> true);
    // sort steps by last run time, descending
    steps.sort(STEP_COMPARATOR_LAST_RUN_TIME_DESC);
    return toStepMap(steps);
  }

  /**
   * Find strategies matching the given criteria.
   *
   * @param userId id of the user who owns the strategy
   * @param saved  TRUE = return only saved strategies, FALSE = return only
   *               unsaved strategies.
   * @param recent TRUE = filter strategies to only those viewed within the past
   *               24 hours.
   *
   * @return A list of Strategy instances matching the search criteria.
   */
  List<Strategy> getStrategies(long userId, boolean saved, boolean recent)
      throws WdkModelException {
    String baseSql = prepareSql(FIND_STRATEGIES_SQL);
    String baseConditions =
        "and sr." + COLUMN_USER_ID + " = " + userId +
        " and sr." + COLUMN_IS_SAVED + " = " + _userDbPlatform.convertBoolean(saved);
    List<Strategy> strategies = (recent ?

        // search using recently viewed condition and related statement param
        doSearch(
            baseSql.replace(SEARCH_CONDITIONS_MACRO, baseConditions +
                " and sr." + COLUMN_LAST_VIEWED_TIME + " >= ?"),
            new Object[] { getRecentTimestamp() }, new Integer[] { Types.TIMESTAMP }) :

        // search using only user id and is-saved conditions
        doSearch(baseSql.replace(SEARCH_CONDITIONS_MACRO, baseConditions))

    ).getStrategies();

    // sort by last modified time, descending
    return descModTimeSort(strategies);
  }

  Map<Long, Strategy> getAllStrategies(MalformedStrategyList malformedStrategies) throws WdkModelException {
    return getStrategies(
        prepareSql(FIND_STRATEGIES_SQL.replace(SEARCH_CONDITIONS_MACRO, "")),
        malformedStrategies);
  }

  Map<Long, Strategy> getStrategies(long userId, MalformedStrategyList malformedStrategies) throws WdkModelException {
    return getStrategies(
        prepareSql(FIND_STRATEGIES_SQL.replace(SEARCH_CONDITIONS_MACRO, "and sr." + COLUMN_USER_ID + " = " + userId)),
        malformedStrategies);
  }

  private Map<Long, Strategy> getStrategies(String searchSql,
      MalformedStrategyList malformedStrategies) throws WdkModelException {
    SearchResult result = doSearch(searchSql);
    malformedStrategies.addAll(result.getMalformedStrategies());
    return toStrategyMap(descModTimeSort(result.getStrategies()));
  }

  Optional<Strategy> getStrategyBySignature(String strategySignature) throws WdkModelException {
    String sql = prepareSql(FIND_STRATEGIES_SQL
        .replace(SEARCH_CONDITIONS_MACRO, "and sr." + COLUMN_SIGNATURE + " = ?"));
    return doSearch(sql, new Object[]{ strategySignature }, new Integer[]{ Types.VARCHAR })
        .getOnlyStrategy("with strategy signature = " + strategySignature);

  }

  private static Timestamp getRecentTimestamp() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DATE, -1);
    return new Timestamp(calendar.getTimeInMillis());
  }

  private static Map<Long,Step> toStepMap(List<Step> steps) {
    return getMapFromList(steps, step -> new TwoTuple<Long,Step>(step.getStepId(), step));
  }

  private static Map<Long,Strategy> toStrategyMap(List<Strategy> strategies) {
    return getMapFromList(strategies, strat -> new TwoTuple<Long,Strategy>(strat.getStrategyId(), strat));
  }

  // add prefix to strategy table columns since some share names with step table columns
  private static String toStratCol(String col) {
    return "strat_" + col;
  }

  private static String mappedColumnSelection(List<String> colNames, Function<String,String> mapper) {
    return join(colNames.stream().map(mapper).collect(toList()), ", ");
  }

  private static List<Strategy> descModTimeSort(List<Strategy> strategies) {
    strategies.sort(STRATEGY_COMPARATOR_LAST_MOD_TIME_DESC);
    return strategies;
  }

  public static class MalformedStrategyList extends
    ArrayList<TwoTuple<StrategyBuilder, InvalidStrategyStructureException>> {}

  private static class SearchResult {

    private final List<Strategy> _strategies;
    private final List<Step> _orphanSteps;
    private final MalformedStrategyList _malformedStrategies;

    public SearchResult(
        List<Strategy> strategies,
        List<Step> orphanSteps,
        MalformedStrategyList malformedStrategies) {
      _strategies = strategies;
      _orphanSteps = orphanSteps;
      _malformedStrategies = malformedStrategies;
    }

    public List<Step> findAllSteps(Predicate<Step> pred) {
      ListBuilder<Step> result = new ListBuilder<>(findOrphanSteps(pred));
      for (Strategy strat : _strategies) {
        result.addIf(pred, strat.getAllSteps());
      }
      return result.toList();
    }

    public Optional<Step> findFirstOverallStep(Predicate<Step> pred) {
      List<Step> found = findAllSteps(pred);
      return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    public List<Step> findOrphanSteps(Predicate<Step> pred) {
      return _orphanSteps.stream().filter(pred).collect(toList());
    }

    public MalformedStrategyList getMalformedStrategies() {
      return _malformedStrategies;
    }

    public List<Strategy> getStrategies() {
      return findStrategies(strategy -> true);
    }

    public List<Strategy> findStrategies(Predicate<Strategy> pred) {
      return _strategies.stream().filter(pred).collect(toList());
    }

    public Optional<Strategy> getOnlyStrategy(String conditionMessage) throws WdkModelException {
      switch (_strategies.size()) {
        case 0: return Optional.empty();
        case 1: return Optional.of(_strategies.get(0));
        default: throw new WdkModelException("Found >1 strategy " + conditionMessage);
      }
    }

    @Override
    public String toString() {
      return new JSONObject()
          .put("orphanSteps", _orphanSteps.stream()
              .map(Step::getStepId)
              .collect(Collectors.toList()))
          .put("strategies", _strategies.stream()
              .map(strat -> new JSONObject()
                  .put("id", strat.getStrategyId())
                  .put("stepIds", strat.getAllSteps().stream()
                      .map(Step::getStepId)
                      .collect(Collectors.toList()))))
          .put("malformedStrategies", _malformedStrategies.stream()
              .map(tup -> tup.getFirst().getStrategyId())
              .collect(Collectors.toList()))
          .toString(2);
    }
  }
}
