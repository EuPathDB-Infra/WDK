package org.gusdb.wdk.model.answer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.db.platform.DBPlatform;
import org.gusdb.fgputil.db.pool.DatabaseInstance;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.answer.stream.PagedAnswerRecordStream;
import org.gusdb.wdk.model.answer.stream.RecordStream;
import org.gusdb.wdk.model.dbms.ResultList;
import org.gusdb.wdk.model.dbms.SqlResultList;
import org.gusdb.wdk.model.filter.Filter;
import org.gusdb.wdk.model.filter.FilterOption;
import org.gusdb.wdk.model.filter.FilterOptionList;
import org.gusdb.wdk.model.filter.FilterSummary;
import org.gusdb.wdk.model.query.Column;
import org.gusdb.wdk.model.query.Query;
import org.gusdb.wdk.model.query.QueryInstance;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.model.record.RecordInstance;
import org.gusdb.wdk.model.record.TableField;
import org.gusdb.wdk.model.record.attribute.AttributeField;
import org.gusdb.wdk.model.record.attribute.ColumnAttributeField;
import org.gusdb.wdk.model.record.attribute.QueryColumnAttributeField;
import org.gusdb.wdk.model.user.User;
import org.json.JSONObject;

/**
 * <p>
 * A list of {@link RecordInstance}s representing one page of the answer to a {@link Question}. The
 * constructor of the Answer provides a handle ( {@link QueryInstance}) on the {@link ResultList} that is the
 * list of primary keys for the all the records (not * just one page) that are the answer to the
 * {@link Question}. The {@link ResultList} also has a column that contains the row number (RESULT_TABLE_I) so
 * that a list of primary keys for a single page can be efficiently accessed.
 * </p>
 * 
 * <p>
 * The AnswerValue is lazy in that it only constructs the set of {@link RecordInstance}s for the page when the
 * first RecordInstance is requested.
 * </p>
 * 
 * <p>
 * The initial request triggers the creation of skeletal {@link RecordInstance}s for the page. They contain
 * only primary keys (these being acquired from the {@link ResultList}).
 * </p>
 * 
 * <p>
 * These skeletal {@link RecordInstance}s are also lazy in that they only run an attributes {@link Query} when
 * an attribute provided by that query is requested. When they do run an attribute query, its
 * {@link QueryInstance} is put into joinMode. This means that the attribute query joins with the table
 * containing the primary keys, and, in one database query, generates rows containing the attribute values for
 * all the {@link RecordInstance}s in the page.
 * </p>
 * 
 * <p>
 * similar lazy loading can be applied to table {@link Query} too.
 * </p>
 * 
 * <p>
 * The method {@link AnswerValue#integrateAttributesQuery} is invoked by the first RecordInstance in the page
 * upon the first request for an attribute provided by an attributes query. The query is a join with the list
 * of primary keys, and so has a row for each {@link RecordInstance} in the page, and columns that provide the
 * attribute values (plus RESULT_TABLE_I). The values in the rows are integrated into the corresponding
 * {@link RecordInstance} (now no longer skeletal). {@link AnswerValue#integrateAttributesQuery} may be called
 * a number of times, depending upon how many attribute queries the {@link RecordClass} contains.
 * </p>
 * 
 * <p>
 * Attribute queries are guaranteed to provide one row for each {@link RecordInstance} in the page. An
 * exception is thrown otherwise.
 * </p>
 * 
 * <p>
 * During a standard load of an AnswerValue, we do the following:
 * 1. Apply filter to the IDs in the cache (FilterInstance takes SQL for IDs, wraps to filter, and returns)
 * 2. Apply sorting (AnswerValue takes SQL from filter, join with appropriate attribute queries, wrap to sort, and returns)
 * 3. Apply paging (add rownum, etc. to SQL)
 * Then run SQL!!  Creates template RecordInstances (non populated with attributes)
 * 4. Apply SQL from step 3, join with attribute queries to build results to return to user
 * Attribute fetch is lazy-loaded, but cache all attributes from attribute query (could be big e.g. BFMV), even if don't need all of them
 * 
 * <p>
 * Created: Fri June 4 13:01:30 2004 EDT
 * </p>
 * 
 * @author David Barkan
 * @version $Revision$ $Date$ $Author$
 */
public class AnswerValue {

  private static final Logger LOG = Logger.getLogger(AnswerValue.class);

  // ------------------------------------------------------------------
  // Instance variables
  // ------------------------------------------------------------------

  // basic information about this answer (i.e. the answer spec) and values derived from them
  protected User _user;
  private Question _question;
  private AnswerFilterInstance _filter;
  private FilterOptionList _filterOptions;
  private FilterOptionList _viewFilterOptions;
  private QueryInstance<?> _idsQueryInstance;
  private String _checksum;

  // sorting and paging for this particular answer
  private Map<String, Boolean> _sortingMap;
  private int _startIndex;
  private int _endIndex;

  // values generated and cached from the above
  private String _sortedIdSql;
  protected ResultSizeFactory _resultSizeFactory;
  private AnswerValueAttributes _attributes;

  // ------------------------------------------------------------------
  // Constructors
  // ------------------------------------------------------------------

  /**
   * @param question
   *          The <code>Question</code> to which this is the <code>Answer</code> .
   * @param idsQueryInstance
   *          The <co de>QueryInstance</code> that provides a handle on the ResultList containing all primary
   *          keys that are the result for the question (not just one page worth).
   * @param startIndex
   *          The index of the first <code>RecordInstance</code> in the page. (>=1)
   * @param endIndex
   *          The index of the last <code>RecordInstance</code> in the page, inclusive.
   */
  public AnswerValue(User user, Question question, QueryInstance<?> idsQueryInstance, int startIndex,
      int endIndex, Map<String, Boolean> sortingMap, AnswerFilterInstance filter) {
    LOG.debug("AnswerValue being created for question: " + question.getDisplayName());
    _user = user;
    _question = question;
    _attributes = new AnswerValueAttributes(_user, _question);
    _resultSizeFactory = new ResultSizeFactory(this);
    _idsQueryInstance = idsQueryInstance;
    _startIndex = startIndex;
    _endIndex = endIndex;

    // get sorting columns
    if (sortingMap == null)
      sortingMap = question.getSortingAttributeMap();
    _sortingMap = sortingMap;

    // get the (old) filter instance
    _filter = filter;

    LOG.debug("AnswerValue created for question: " + question.getDisplayName());
  }

  /**
   * Copy constructor
   * 
   * @param answerValue the answer value to be copied
   */
  public AnswerValue(AnswerValue answerValue) {
    this(answerValue, answerValue._startIndex, answerValue._endIndex);
  }

  /**
   * A copy constructor with start- and end-index modification
   * 
   * @param answerValue source answer value
   * @param startIndex 1-based start index (inclusive)
   * @param endIndex end index (inclusive), or a negative value for all records
   */
  private AnswerValue(AnswerValue answerValue, int startIndex, int endIndex) {
    _startIndex = startIndex;
    _endIndex = endIndex;

    _user = answerValue._user;
    _idsQueryInstance = answerValue._idsQueryInstance;
    _question = answerValue._question;
    _attributes = new AnswerValueAttributes(_user, _question);

    // Note: do not copy result size data (i.e. _resultSizesByFilter and
    //   _resultSizesByProject); they are essentially caches and should be
    //   rebuilt by each new AnswerValue
    _resultSizeFactory = new ResultSizeFactory(this);

    _sortingMap = new LinkedHashMap<String, Boolean>(answerValue._sortingMap);
    _filter = answerValue._filter;
    if (answerValue._filterOptions != null) _filterOptions = new FilterOptionList(answerValue._filterOptions);
    if (answerValue._viewFilterOptions != null) _viewFilterOptions = new FilterOptionList(answerValue._viewFilterOptions);

    LOG.debug("AnswerValue created by copying another AnswerValue");
  }

  /**
   * @param startIndex 1-based start index (inclusive)
   * @param endIndex end index (inclusive), or a negative value for all records
   * @return copy of this answer value with new paging
   */
  public AnswerValue cloneWithNewPaging(int startIndex, int endIndex) {
    return new AnswerValue(this, startIndex, endIndex);
  }

  public User getUser() {
    return _user;
  }

  public Question getQuestion() {
    return _question;
  }

  public QueryInstance<?> getIdsQueryInstance() {
    return _idsQueryInstance;
  }

  public AnswerValueAttributes getAttributes() {
    return _attributes;
  }

  public ResultSizeFactory getResultSizeFactory() {
    return _resultSizeFactory;
  }

  /**
   * @return Map where key is param display name and value is param value
   */
  public Map<String, String> getParamDisplays() {
    Map<String, String> displayParamsMap = new LinkedHashMap<String, String>();
    Map<String, String> paramsMap = _idsQueryInstance.getParamStableValues();
    Param[] params = _question.getParams();
    for (int i = 0; i < params.length; i++) {
      Param param = params[i];
      displayParamsMap.put(param.getPrompt(), paramsMap.get(param.getName()));
    }
    return displayParamsMap;
  }

  /**
   * the checksum of the iq query, plus the filter information on the answer.
   * 
   * @return
   * @throws WdkUserException
   */
  public String getChecksum() throws WdkModelException, WdkUserException {
    if (_checksum == null) {
      JSONObject jsContent = new JSONObject();
      jsContent.put("query-checksum", _idsQueryInstance.getChecksum());

      // add the old filter into the content; the old filter will be deprecated in the future releases, and
      // this line will be removed.
      if (_filter != null)
        jsContent.put("old-filter", _filter.getName());

      // new filters have been applied, get the content for it
      if (_filterOptions != null)
        jsContent.put("filters", _filterOptions.getJSON());

      // encrypt the content to make step-independent checksum
      _checksum = Utilities.encrypt(jsContent.toString());
    }
    return _checksum;
  }

  /**
   * Convenience method to provide a page of DynamicRecordInstances.  Future code
   * should explicitly create a DynamicRecordInstanceMap from this answer value.
   * 
   * @return page of dynamic records as an array
   */
  public RecordInstance[] getRecordInstances() throws WdkModelException, WdkUserException {
    DynamicRecordInstanceList dynamicMap = new DynamicRecordInstanceList(this);
    return dynamicMap.values().toArray(new RecordInstance[dynamicMap.size()]);
  }

  // FIXME!!!  Look at how this is called- we should not be using PagedAnswerRecordStream!
  /**
   * Iterate through all the records of the answer
   * 
   * @return record stream of this answer value
   */
  public RecordStream getFullAnswer() {
    return new PagedAnswerRecordStream(this, 200);
  }

  /**
   * This method returns a paged attribute sql query.  It is now public because the FileBasedRecordStream object
   * uses it to acquire a sql statement to execute.
   * @param attributeQuery
   * @return
   * @throws WdkModelException
   * @throws WdkUserException
   */
  public String getPagedAttributeSql(Query attributeQuery, boolean sortPage) throws WdkModelException, WdkUserException {
    // get the paged SQL of id query
    String idSql = getPagedIdSql();

    // combine the id query with attribute query
    String attributeSql = getAttributeSql(attributeQuery);
    StringBuilder sql = new StringBuilder(" /* the desired attributes, for a page of sorted results */ "
        + " SELECT aq.* FROM (");
    sql.append(idSql);
    sql.append(") pidq, (/* attribute query that returns attributes in a page */ ").append(attributeSql).append(
        ") aq WHERE ");

    boolean firstColumn = true;
    for (String column : _question.getRecordClass().getPrimaryKeyDefinition().getColumnRefs()) {
      if (firstColumn)
        firstColumn = false;
      else
        sql.append(" AND ");
      sql.append("aq.").append(column).append(" = pidq.").append(column);
    }
    if (sortPage) {
      // sort by underlying idq row_index if requested
      sql.append(" ORDER BY pidq.row_index");
    }
    return sql.toString();
  }

  public ResultList getTableFieldResultList(TableField tableField) throws WdkModelException, WdkUserException {
    WdkModel wdkModel = _question.getWdkModel();
    // has to get a clean copy of the attribute query, without pk params appended
    Query tableQuery = tableField.getUnwrappedQuery();

    /*logger.debug("integrate table query from answer: " + tableQuery.getFullName());
    for (Param param : tableQuery.getParams()) {
       logger.debug("param: " + param.getName());
    }
    */
    // get and run the paged table query sql
    String sql = getPagedTableSql(tableQuery);

    DatabaseInstance platform = wdkModel.getAppDb();
    DataSource dataSource = platform.getDataSource();
    ResultSet resultSet = null;
    try {
      LOG.debug("SQL for TableField '" + tableField.getName() + "': " + sql);
      resultSet = SqlUtils.executeQuery(dataSource, sql, tableQuery.getFullName() + "_table-paged");
    }
    catch (SQLException e) {
      throw new WdkModelException(e);
    }

    return new SqlResultList(resultSet);
  }

  String getPagedTableSql(Query tableQuery) throws WdkModelException, WdkUserException {
    // get the paged SQL of id query
    String idSql = getPagedIdSql();

    // combine the id query with attribute query
    // make an instance from the original attribute query, and attribute
    // query has only one param, user_id. Note that the original
    // attribute query is different from the attribute query held by the
    // recordClass.
    Map<String, String> params = new LinkedHashMap<String, String>();
    QueryInstance<?> queryInstance = tableQuery.makeInstance(_user, params, true, 0,
        new LinkedHashMap<String, String>());
    String tableSql = queryInstance.getSql();
    DBPlatform platform = _question.getWdkModel().getAppDb().getPlatform();
    String tableSqlWithRowIndex = "(SELECT tq.*, " + platform.getRowNumberColumn() + " as row_index FROM (" + tableSql + ") tq ";
    StringBuffer sql = new StringBuffer("SELECT tqi.* FROM (");
    sql.append(idSql);
    sql.append(") pidq, ").append(tableSqlWithRowIndex).append(") tqi WHERE ");

    boolean firstColumn = true;
    for (String column : _question.getRecordClass().getPrimaryKeyDefinition().getColumnRefs()) {
      if (firstColumn)
        firstColumn = false;
      else
        sql.append(" AND ");
      sql.append("tqi.").append(column).append(" = pidq.").append(column);
    }
    sql.append(" ORDER BY pidq.row_index, tqi.row_index");

    // replace the id_sql macro.  this sql must include filters (but not view filters)
    String sqlWithIdSql = sql.toString().replace(Utilities.MACRO_ID_SQL, getPagedIdSql(true, true));
    return sqlWithIdSql.replace(Utilities.MACRO_ID_SQL_NO_FILTERS, "(" + getNoFiltersIdSql() + ")");
  }

  public String getAttributeSql(Query attributeQuery) throws WdkModelException, WdkUserException {
    String queryName = attributeQuery.getFullName();
    Query dynaQuery = _question.getDynamicAttributeQuery();
    String sql;
    if (dynaQuery != null && queryName.equals(dynaQuery.getFullName())) {
      // the dynamic query doesn't have sql defined, the sql will be
      // constructed from the id query cache table.
      sql = _idsQueryInstance.getSql();
    }
    else {
      // make an instance from the original attribute query, and attribute
      // query has only one param, user_id. Note that the original
      // attribute query is different from the attribute query held by the
      // recordClass.
      Map<String, String> params = new LinkedHashMap<String, String>();
      QueryInstance<?> attributeQueryInstance;
      try {
        attributeQueryInstance = attributeQuery.makeInstance(_user, params, true, 0,
            new LinkedHashMap<String, String>());
      }
      catch (WdkUserException ex) {
        throw new WdkModelException(ex);
      }
      sql = attributeQueryInstance.getSql();

      // replace the id sql macro.  the injected sql must include filters (but not view filters)
      sql = sql.replace(Utilities.MACRO_ID_SQL, getIdSql(null, true));
      // replace the no-filters id sql macro.  the injected sql must NOT include filters (but should return any dynamic columns)
      sql = sql.replace(Utilities.MACRO_ID_SQL_NO_FILTERS,  "(" + getNoFiltersIdSql() + ")");
    }
    return sql;
  }

  protected String getNoFiltersIdSql() throws WdkModelException, WdkUserException {
    return _idsQueryInstance.getSql();
  }

  public String getSortedIdSql() throws WdkModelException, WdkUserException {
      if (_sortedIdSql == null) _sortedIdSql = getSortedIdSql(false);
      return _sortedIdSql;
  }

  private String getSortedIdSql(boolean excludeViewFilters) throws WdkModelException, WdkUserException {

    String[] pkColumns = _question.getRecordClass().getPrimaryKeyDefinition().getColumnRefs();

    // get id sql
    String idSql = getIdSql(null, excludeViewFilters);

    // get sorting attribute queries
    Map<String, String> attributeSqls = new LinkedHashMap<String, String>();
    List<String> orderClauses = new ArrayList<String>();
    prepareSortingSqls(attributeSqls, orderClauses);

    StringBuffer sql = new StringBuffer("/* the ID query results, sorted */ SELECT ");
    boolean firstColumn = true;
    for (String pkColumn : pkColumns) {
      if (firstColumn)
        firstColumn = false;
      else
        sql.append(", ");
      sql.append("idq." + pkColumn);
    }

    sql.append(" FROM " + idSql + " idq");
    // add all tables involved
    for (String shortName : attributeSqls.keySet()) {
      sql.append(", (").append(attributeSqls.get(shortName)).append(") ");
      sql.append(shortName);
    }

    // add primary key join conditions
    boolean firstClause = true;
    for (String shortName : attributeSqls.keySet()) {
      for (String column : pkColumns) {
        if (firstClause) {
          sql.append(" WHERE ");
          firstClause = false;
        }
        else
          sql.append(" AND ");

        sql.append("idq.").append(column);
        sql.append(" = ");
        sql.append(shortName).append(".").append(column);
      }
    }

    // add order clause
    // always append primary key columns as the last sorting columns,
    // otherwise Oracle may generate unstable results through pagination
    // when the sorted columns are not unique.
    sql.append(" ORDER BY ");
    for (String clause : orderClauses) {
      sql.append(clause).append(", ");
    }
    firstClause = true;
    for (String column : pkColumns) {
      if (firstClause)
        firstClause = false;
      else
        sql.append(", ");
      sql.append("idq.").append(column);
    }

    LOG.debug("sorted id sql constructed.");
    return sql.toString();
  }

  public String getPagedIdSql() throws WdkModelException, WdkUserException {
      return getPagedIdSql(false, false);
  }

  private String getPagedIdSql(boolean excludeViewFilters, boolean includeRowIndex) throws WdkModelException, WdkUserException {
    String sortedIdSql = getSortedIdSql(excludeViewFilters);
    DatabaseInstance platform = _question.getWdkModel().getAppDb();
    String sql = platform.getPlatform().getPagedSql(sortedIdSql, _startIndex, _endIndex, includeRowIndex);

    // add comments to the sql
    sql = " /* a page of sorted ids */ " + sql;

    LOG.debug("paged id sql constructed.");

    return sql;
  }

  public String getIdSql() throws WdkModelException, WdkUserException {
      return getIdSql(null, false);
  }

  protected String getIdSql(String excludeFilter, boolean excludeViewFilters) throws WdkModelException, WdkUserException {

    String innerSql = _idsQueryInstance.getSql();

    // add comments to id sql
    innerSql = " /* the ID query */" + innerSql;

    int assignedWeight = _idsQueryInstance.getAssignedWeight();
    // apply old filter
    if (_filter != null) {
      innerSql = _filter.applyFilter(_user, innerSql, assignedWeight);
      innerSql = " /* old filter applied on id query */ " + innerSql;
    }

    // apply "new" filters
    if (_filterOptions != null) {
      // logger.debug("applyFilters(): found filterOptions to apply to the ID SQL: excludeFilter: " +
      // excludeFilter);
      innerSql = applyFilters(innerSql, _filterOptions, excludeFilter);
      innerSql = " /* new filter applied on id query */ " + innerSql;
    }
    // apply view filters if requested
    boolean viewFiltersApplied = (_viewFilterOptions != null && _viewFilterOptions.getSize() > 0);
    if (viewFiltersApplied && !excludeViewFilters) {
      LOG.info("apply viewFilters(): excludeFilter: " + excludeFilter + " " +
          _viewFilterOptions.getFilterOptions().keySet() + " " + this);
      innerSql = applyFilters(innerSql, _viewFilterOptions, excludeFilter);
      LOG.debug("innersql: " + innerSql);
      innerSql = " /* new view filter applied on id query */ " + innerSql;
    }

    innerSql = "(" + innerSql + ")";
    LOG.debug("AnswerValue: ID SQL constructed with all filters:\n");

    return innerSql;

  }

  private String applyFilters(String innerSql, FilterOptionList filterOptions, String excludeFilter)
      throws WdkModelException, WdkUserException {

    if (filterOptions != null) {
      for (FilterOption filterOption : filterOptions.getFilterOptions().values()) {
        //logger.debug("applying FilterOption:" + filterOption.getJSON().toString(2));
        if (excludeFilter == null || !filterOption.getKey().equals(excludeFilter)) {
          if (!filterOption.isDisabled()) {
            Filter filter = _question.getFilter(filterOption.getKey());
            innerSql = filter.getSql(this, innerSql, filterOption.getValue());
          }
        }
      }
    }
    return innerSql;
  }

  private void prepareSortingSqls(Map<String, String> sqls, Collection<String> orders)
      throws WdkModelException, WdkUserException {
    Map<String, AttributeField> fields = _question.getAttributeFieldMap();
    Map<String, String> querySqls = new LinkedHashMap<String, String>();
    Map<String, String> queryNames = new LinkedHashMap<String, String>();
    Map<String, String> orderClauses = new LinkedHashMap<String, String>();
    WdkModel wdkModel = _question.getWdkModel();
    LOG.debug("sorting map: " + _sortingMap);
    final String idQueryNameStub = "answer_id_query";
    queryNames.put(idQueryNameStub, "idq");
    for (String fieldName : _sortingMap.keySet()) {
      AttributeField field = fields.get(fieldName);
      if (field == null) continue;
      boolean ascend = _sortingMap.get(fieldName);
      Map<String, ColumnAttributeField> dependents = field.getColumnAttributeFields();
      for (ColumnAttributeField dependent : dependents.values()) {

        // set default values for PK and simple columns
        String queryName = idQueryNameStub;
        String sortingColumn = dependent.getName();
        boolean ignoreCase = false;
        
        // handle query columns
        if (dependent instanceof QueryColumnAttributeField) {
          Column column = ((QueryColumnAttributeField)dependent).getColumn();
          //logger.debug("field [" + fieldName + "] depends on column [" + column.getName() + "]");
          Query query = column.getQuery();
          queryName = query.getFullName();
          // cannot use the attribute query from record, need to get it
          // back from wdkModel, since the query has pk params appended
          query = (Query) wdkModel.resolveReference(queryName);

          // handle query
          if (!queryNames.containsKey(queryName)) {
            // query not processed yet, process it
            String shortName = "aq" + queryNames.size();
            String sql = getAttributeSql(query);

            // add comments to sql
            sql = " /* attribute query used for sorting: " + queryName + " */ " + sql;
            queryNames.put(queryName, shortName);
            querySqls.put(queryName, sql);
          }

          // handle column
          String customSortingColumn = column.getSortingColumn();
          if (customSortingColumn != null) {
            sortingColumn = customSortingColumn;
          }
          ignoreCase = column.isIgnoreCase();
        }

        if (!orderClauses.containsKey(sortingColumn)) {
          // dependent not processed, process it
          StringBuilder clause = new StringBuilder();
          if (ignoreCase) clause.append("lower(");
          clause.append(queryNames.get(queryName));
          clause.append(".");
          clause.append(sortingColumn);
          if (ignoreCase) clause.append(")");
          clause.append(ascend ? " ASC" : " DESC");
          orderClauses.put(sortingColumn, clause.toString());
        }
      }
    }

    // fill the map of short name and sqls
    for (String queryName : queryNames.keySet()) {
      if (querySqls.containsKey(queryName)) {
        // generating a map from short name (e.g. aq1) to attribute query SQL
        sqls.put(queryNames.get(queryName), querySqls.get(queryName));
      }
    }
    orders.addAll(orderClauses.values());
  }

  public int getEndIndex() {
    return _endIndex;
  }

  public int getStartIndex() {
    return _startIndex;
  }

  public int getPageSize() throws WdkModelException, WdkUserException {
    int resultSize = _resultSizeFactory.getResultSize();
    return (_endIndex == -1 ? resultSize : Math.min(_endIndex, resultSize)) - _startIndex + 1;
  }

  public String getResultMessage() {
    return _idsQueryInstance.getResultMessage();
  }

  public Map<String, Boolean> getSortingMap() {
    return new LinkedHashMap<String, Boolean>(_sortingMap);
  }

  /**
   * Set a new sorting map
   * 
   * @param sortingMap
   * @throws WdkModelException 
   */
  public void setSortingMap(Map<String, Boolean> sortingMap) throws WdkModelException {
    if (sortingMap == null) {
      sortingMap = _question.getSortingAttributeMap();
    }
    // make sure all sorting columns exist
    StringBuilder buffer = new StringBuilder("set sorting: ");
    Map<String, AttributeField> attributes = _question.getAttributeFieldMap();
    Map<String, Boolean> validMap = new LinkedHashMap<String, Boolean>();
    for (String attributeName : sortingMap.keySet()) {
      buffer.append(attributeName + "=" + sortingMap.get(attributeName) + ", ");
      // if a sorting attribute is invalid, instead of throwing out an
      // exception, ignore it.
      if (!attributes.containsKey(attributeName)) {
        // throw new
        // WdkModelException("the assigned sorting attribute ["
        // + attributeName + "] doesn't exist in the answer of "
        // + "question " + question.getFullName());
        LOG.debug("Invalid sorting attribute: User #" + _user.getUserId() + ", question: '" +
            _question.getFullName() + "', attribute: '" + attributeName + "'");
      }
      else {
        validMap.put(attributeName, sortingMap.get(attributeName));
      }
    }
    LOG.debug(buffer);
    _sortingMap.clear();
    _sortingMap.putAll(validMap);
    _sortedIdSql = null;
  }

  /**
   * This method is redundant with getAllIds(), consider deprecate either one of them.
   * 
   * @return returns a list of all primary key values.
   * @throws WdkUserException
   */
  public Object[][] getPrimaryKeyValues() throws WdkModelException, WdkUserException {
    String[] columns = _question.getRecordClass().getPrimaryKeyDefinition().getColumnRefs();
    List<Object[]> buffer = new ArrayList<Object[]>();

    ResultList resultList;
    if (_filter == null)
      resultList = _idsQueryInstance.getResults();
    else
      resultList = _filter.getResults(this);

    while (resultList.next()) {
      Object[] pkValues = new String[columns.length];
      for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
        pkValues[columnIndex] = resultList.get(columns[columnIndex]);
      }
      buffer.add(pkValues);
    }
    Object[][] ids = new String[buffer.size()][columns.length];
    buffer.toArray(ids);
    return ids;
  }

  public AnswerFilterInstance getFilter() {
    return _filter;
  }

  public void setFilterInstance(String filterName) {
    if (filterName != null) {
      RecordClass recordClass = _question.getRecordClass();
      setFilterInstance(recordClass.getFilterInstance(filterName));
    }
    else
      _filter = null;
  }

  public void setFilterInstance(AnswerFilterInstance filter) {
    _filter = filter;
    reset();
  }

  private void reset() {
    _sortedIdSql = null;
    _checksum = null;
    _resultSizeFactory.clear();
  }

  /**
   * Get a list of all the primary key tuples of all the records in the answer. It is a shortcut of iterating
   * through all the pages and get the primary keys.
   * 
   * This method is redundant with getPrimaryKeyValues(), consider deprecate either one of them.
   * 
   * @return
   * @throws WdkUserException
   */
  public List<String[]> getAllIds() throws WdkModelException, WdkUserException {
    String idSql = getSortedIdSql();
    String[] pkColumns = _question.getRecordClass().getPrimaryKeyDefinition().getColumnRefs();
    List<String[]> pkValues = new ArrayList<String[]>();
    WdkModel wdkModel = _question.getWdkModel();
    DataSource dataSource = wdkModel.getAppDb().getDataSource();
    ResultSet resultSet = null;
    try {
      resultSet = SqlUtils.executeQuery(dataSource, idSql, _idsQueryInstance.getQuery().getFullName() + "__all-ids");
      while (resultSet.next()) {
        String[] values = new String[pkColumns.length];
        for (int i = 0; i < pkColumns.length; i++) {
          Object value = resultSet.getObject(pkColumns[i]);
          values[i] = (value == null) ? null : value.toString();
        }
        pkValues.add(values);
      }
    }
    catch (SQLException ex) {
      throw new WdkModelException(ex);
    }
    finally {
      SqlUtils.closeResultSetAndStatement(resultSet, null);
    }
    return pkValues;
  }

  public void setPageIndex(int startIndex, int endIndex) {
    _startIndex = startIndex;
    _endIndex = endIndex;
    reset();
  }

  public void setFilterOptions(FilterOptionList filterOptions) {
    _filterOptions = filterOptions;
    reset();
  }

  public void setViewFilterOptions(FilterOptionList viewFilterOptions) {
    LOG.info("setting options: " + this + " " + viewFilterOptions);
    _viewFilterOptions = viewFilterOptions;
    reset();
  }

  public FilterOptionList getFilterOptions() {
    return _filterOptions;
  }

  public FilterOptionList getViewFilterOptions() {
    return _viewFilterOptions;
  }

  public FilterSummary getFilterSummary(String filterName) throws WdkModelException, WdkUserException {
    // need to exclude the given filter from the idSql, so that the selection of the current filter won't
    // affect the background;
    String idSql = getIdSql(filterName, false);
    Filter filter = _question.getFilter(filterName);
    return filter.getSummary(this, idSql);
  }
}
