package org.gusdb.wdk.model.query;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.Timer;
import org.gusdb.fgputil.validation.ValidObjectFactory.RunnableObj;
import org.gusdb.wdk.model.AttributeMetaQueryHandler;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkModelText;
import org.gusdb.wdk.model.query.param.DatasetParam;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.query.spec.QueryInstanceSpec;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.record.attribute.AttributeFieldDataType;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.isNull;
import static java.util.function.Predicate.not;
import static org.gusdb.wdk.model.AttributeMetaQueryHandler.getDynamicallyDefinedAttributes;

/**
 * An SqlQuery is used to access data from a database with SQL, and if the SQL
 * is relatively slow, it can be optionally cached for better performance.
 * <p>
 * the param can be embedded into SQL template in such form: $$param_name$$. the
 * param_name is the name of the param, and it doesn't have the paramSet name
 * prefix.
 * <p>
 * You can also define macros in the model and those macros will be substituted
 * into the SQL template. The difference between param and macro is that the
 * value of the macro is defined in the model and substituted into the SQL
 * template at initialization time, it will become a part of the SQL template;
 * while the value of param is provided by the user at run time, and it is
 * substituted into SQL template to produce the final SQL, but it doesn't change
 * the SQL template itself.
 *
 * @author Jerric Gao
 */
public class SqlQuery extends Query {

  private static final Logger LOG = Logger.getLogger(SqlQuery.class);

  private List<WdkModelText> sqlList;
  private String _sql;
  private List<WdkModelText> sqlMacroList;
  private Map<String, String> sqlMacroMap;
  private boolean clobRow;
  private boolean _useDBLink;

  /**
   * A flag to check if the cached has been set. if not set, the value from
   * parent querySet will be used.
   */
  private boolean setCache;
  private boolean isCacheable;

  private String _attributeMetaQueryRef;
  private List<WdkModelText> dependentTableList;
  private Map<String, String> dependentTableMap;

  public SqlQuery() {
    super();
    clobRow = false;
    sqlList = new ArrayList<>();
    sqlMacroList = new ArrayList<>();
    sqlMacroMap = new LinkedHashMap<>();
    dependentTableList = new ArrayList<>();
    dependentTableMap = new LinkedHashMap<>();
  }

  public SqlQuery(SqlQuery query) {
    super(query);
    this.clobRow = query.clobRow;
    this._sql = query._sql;
    this.isCacheable = query.isCacheable;
    this._useDBLink = query._useDBLink;
    this.setCache = query.setCache;

    if (query.sqlList != null) this.sqlList = new ArrayList<>(query.sqlList);
    if (query.sqlMacroMap != null)
      this.sqlMacroMap = new LinkedHashMap<>(query.sqlMacroMap);
    if (query.sqlMacroList != null)
      this.sqlMacroList = new ArrayList<>(query.sqlMacroList);
    if (query.dependentTableMap != null)
      this.dependentTableMap = new LinkedHashMap<>(query.dependentTableMap);
    if (query.dependentTableList != null)
      this.dependentTableList = new ArrayList<>(query.dependentTableList);
  }

  @Override
  protected SqlQueryInstance makeInstance(RunnableObj<QueryInstanceSpec> spec) throws WdkModelException {
    return new SqlQueryInstance(spec);
  }

  public void addSql(WdkModelText sql) {
    this.sqlList.add(sql);
  }

  public void addSqlParamValue(WdkModelText sqlMacro) {
    this.sqlMacroList.add(sqlMacro);
  }

  public void addSqlParamValue(String macro, String value) {
    this.sqlMacroMap.put(macro, value);
  }

  public String getSql() {
    return replaceMacros(_sql);
  }

  public boolean isUseDBLink() {
    return _useDBLink;
  }

  public void setUseDBLink(boolean useDBLink) {
    _useDBLink = useDBLink;
  }

  /**
   * @return whether this query should be cached
   */
  @Override
  public boolean getIsCacheable() {
    // check if global caching is turned off, if off, then return false
    if (!_wdkModel.getModelConfig().isCaching()) return false;
    // check if this query's value has been set; if not, use QuerySet's value
    if (!setCache) return getQuerySet().isCacheable();
    // otherwise, use value assigned to this query
    return isCacheable;
  }

  /**
   * @param isCacheable
   *          the cached to set
   */
  public void setIsCacheable(boolean isCacheable) {
    this.isCacheable = isCacheable;
    setCache = true;
  }

  /**
   * Sets an optional reference to a meta columns query
   */
  public void setAttributeMetaQueryRef(String attributeMetaQueryRef) {
    _attributeMetaQueryRef = attributeMetaQueryRef;
  }

  /**
   * this method is called by other WDK objects. It is not called by the model
   * xml parser.
   */
  public void setSql(String sql) {
    // append new line to the end, in case the last line is a comment;
    // otherwise, all modified sql will fail.
    this._sql = sql + "\n";
  }

  @Override
  protected void appendChecksumJSON(JSONObject jsQuery, boolean extra)
      throws JSONException {
    if (extra) {
      // add macro into the content
      String[] macroNames = new String[sqlMacroMap.size()];
      sqlMacroMap.keySet().toArray(macroNames);
      Arrays.sort(macroNames);
      JSONObject jsMacros = new JSONObject();
      for (String macroName : macroNames) {
        jsMacros.put(macroName, sqlMacroMap.get(macroName));
      }
      jsQuery.put("macros", jsMacros);

      // add sql
      String sql = getSql().replaceAll("\\s+", " ");
      jsQuery.put("sql", sql);
    }
  }

  @Override
  public void excludeResources(String projectId) throws WdkModelException {
    super.excludeResources(projectId);

    // exclude sql
    for (WdkModelText sql : sqlList) {
      if (sql.include(projectId)) {
        sql.excludeResources(projectId);
        this.setSql(sql.getText());
        break;
      }
    }
    sqlList = null;

    // exclude sql
    for (WdkModelText dependentTable : dependentTableList) {
      if (dependentTable.include(projectId)) {
        dependentTable.excludeResources(projectId);
        String table = dependentTable.getText();
        this.dependentTableMap.put(table, table);
      }
    }
    dependentTableList = null;

    // exclude macros
    for (WdkModelText macro : sqlMacroList) {
      if (macro.include(projectId)) {
        macro.excludeResources(projectId);
        String name = macro.getName();
        if (sqlMacroMap.containsKey(name))
          throw new WdkModelException("The macro " + name
              + " is duplicated in query " + getFullName());

        sqlMacroMap.put(macro.getName(), macro.getText());
      }
    }
    sqlMacroList = null;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.gusdb.wdk.model.query.Query#resolveReferences(org.gusdb.wdk.model
   * .WdkModel)
   */
  @Override
  public void resolveQueryReferences(WdkModel wdkModel)
      throws WdkModelException {
    // apply the sql macros into sql
    if (this._sql == null)
      throw new WdkModelException("null sql in " + getQuerySet().getName()
          + "." + getName());

    // don't replace the sql here. the macros have to be replaced on the fly
    // in order to inject overridden macros from question.
    String sql = replaceMacros(this._sql);

    // verify the all param macros have been replaced
    Matcher matcher = Pattern.compile("&&([^&]+)&&").matcher(sql);
    if (matcher.find())
      throw new WdkModelException("SqlParamValue macro " + matcher.group(1)
          + " found in <sql> of query " + getFullName()
          + ", but it's not defined.");
  }

  private String replaceMacros(String sql) {
    for (String paramName : sqlMacroMap.keySet()) {
      String pattern = "&&" + paramName + "&&";
      String value = sqlMacroMap.get(paramName);
      // escape the & $ \ chars in the value
      sql = sql.replaceAll(pattern, Matcher.quoteReplacement(value));
    }
    return sql;
  }

  @Override
  public SqlQuery clone() {
    return new SqlQuery(this);
  }

  /**
   * This is a way to declare the query returns clob columns. This property is
   * used when we generate download cache from table queries. If the
   * concatenated size of the column values exceeds the DBMS limit for string
   * columns, this flag should be set to true, so that the result can be casted
   * into a CLOB. However, since writing and reading clobs are much slower than
   * a normal string column, the flag should be set to false if CLOB is not
   * needed.
   *
   * @return the clobRow
   */
  public boolean isClobRow() {
    return clobRow;
  }

  /**
   * @param clobRow
   *          the clobRow to set
   */
  public void setClobRow(boolean clobRow) {
    this.clobRow = clobRow;
  }

  public void addDependentTable(WdkModelText dependentTable) {
    this.dependentTableList.add(dependentTable);
  }

  /**
   * This is a way to declare the tables the SQL depends on without having to
   * parse the SQL. This feature is used in generating download cache.
   */
  public String[] getDependentTables() {
    String[] array = new String[dependentTableMap.size()];
    dependentTableMap.keySet().toArray(array);
    return array;
  }

  @Override
  public void resolveReferences(WdkModel wdkModel) throws WdkModelException {
    if (_resolved) return;
    super.resolveReferences(wdkModel);

    // set the dblink flag if any of the params is a datasetParam;
    for (Param param : getParams()) {
      if (param instanceof DatasetParam) {
        _useDBLink = true;
        break;
      }
    }

    // Continue only if an attribute meta query reference is provided
    if (_attributeMetaQueryRef != null) {
      Timer timer = new Timer();
      for (Map<String,Object> row : getDynamicallyDefinedAttributes(_attributeMetaQueryRef, wdkModel)) {
        Column column = new Column();
        // Need to set this here since this column originates from the database
        column.setQuery(SqlQuery.this);
        AttributeMetaQueryHandler.populate(column, row);
        columnMap.put(column.getName(), column);
      }
      LOG.debug("Took " + timer.getElapsedString() + " to resolve AttributeMetaQuery: " + _attributeMetaQueryRef);
    }
  }

  @Override
  public Map<String, AttributeFieldDataType> resolveColumnTypes() throws WdkModelException {
    var types = new LinkedHashMap<String, AttributeFieldDataType>();
    var sql = applyParams(getSql(), paramMap.values().iterator());

    // DB column name casing may not match xml name casing.
    var names = columnMap.keySet()
      .stream()
      .collect(Collectors.toMap(String::toLowerCase, Function.identity()));

    if (isNull(sql) || sql.isBlank())
      return handleEmptySql();

    try (
      var con = _wdkModel.getAppDb().getDataSource().getConnection();
      var ps = con.prepareStatement(sql)
    ) {
      var meta = ps.getMetaData();
      var cols = meta.getColumnCount();

      for (int i = 1; i <= cols; i++) {
        var name = names.get(meta.getColumnName(i).toLowerCase());
        var type = meta.getColumnType(i);

        if (name != null)
          types.put(name, AttributeFieldDataType.fromSqlType(type));
      }
    } catch (SQLException e) {
      return handleColumnTypeException(e);
    }

    return types;
  }

  private String applyParams(String sql, Iterator<Param> params) {
    if (!params.hasNext())
      return sql;
    var param = params.next();
    var value = param.getParamHandler().toEmptyInternalValue();
    return applyParams(param.replaceSql(sql, value), params);
  }

  private Map<String, AttributeFieldDataType> handleEmptySql()
  throws WdkModelException {
    if (getName().endsWith(Question.DYNAMIC_QUERY_SUFFIX))
      // TODO: What should actually be done with this?  Is this why query
      //       instance was needed?
      return super.resolveColumnTypes();
    else
      throw new WdkModelException("Empty SQL in query " + getFullName());

  }

  private static final Pattern MACRO = Pattern.compile("##[A-Z_]+##");
  private static final Pattern PARAM = Pattern.compile("\\${2}\\w+\\${2}");

  private Map<String, AttributeFieldDataType> handleColumnTypeException(
    final SQLException ex
  ) throws WdkModelException {
    var sql = getSql();
    var macro = MACRO.matcher(sql);
    var param = PARAM.matcher(sql);

    // If this query does not contain an unparsed macro or param, then it was
    // just bad SQL
    if (!macro.find() && !param.find())
      throw new WdkModelException(String.format("sqlQuery %s is not valid SQL",
        getFullName()), ex);

    var invalid = columnMap.values().stream()
      .anyMatch(not(Column::wasTypeSet));

    if (invalid) {
      throw new WdkModelException(String.format(
        "Due to one or more macros and/or params the \"columnType\" value must "
          + "be set for each column in the sqlQuery \"%s\".\n\n"
          + "Macros: %s\n"
          + "Sql: " + sql,
        getFullName(),
        Stream.concat(
          macro.reset().results().map(MatchResult::group),
          param.reset().results().map(MatchResult::group)
        )
          .filter(not(String::isBlank))
          .collect(Collectors.joining(", "))),
      ex);
    }

    return super.resolveColumnTypes();
  }
}
