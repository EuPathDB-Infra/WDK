package org.gusdb.wdk.model.answer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.db.pool.DatabaseInstance;
import org.gusdb.wdk.model.TreeNode;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.dbms.ResultFactory;
import org.gusdb.wdk.model.dbms.ResultList;
import org.gusdb.wdk.model.dbms.SqlResultList;
import org.gusdb.wdk.model.query.BooleanQueryInstance;
import org.gusdb.wdk.model.query.Column;
import org.gusdb.wdk.model.query.Query;
import org.gusdb.wdk.model.query.QueryInstance;
import org.gusdb.wdk.model.query.SqlQuery;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.record.FieldScope;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.model.record.RecordInstance;
import org.gusdb.wdk.model.record.TableField;
import org.gusdb.wdk.model.record.TableValue;
import org.gusdb.wdk.model.record.attribute.AttributeCategoryTree;
import org.gusdb.wdk.model.record.attribute.AttributeField;
import org.gusdb.wdk.model.record.attribute.AttributeValue;
import org.gusdb.wdk.model.record.attribute.ColumnAttributeField;
import org.gusdb.wdk.model.record.attribute.ColumnAttributeValue;
import org.gusdb.wdk.model.record.attribute.PrimaryKeyAttributeField;
import org.gusdb.wdk.model.record.attribute.PrimaryKeyAttributeValue;
import org.gusdb.wdk.model.report.Reporter;
import org.gusdb.wdk.model.report.TabularReporter;
import org.gusdb.wdk.model.user.User;

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
 * Created: Fri June 4 13:01:30 2004 EDT
 * </p>
 * 
 * @author David Barkan
 * @version $Revision$ $Date$ $Author$
 * 
 * 
 */
public class AnswerValue {

  private static class FilterSizeTask implements Runnable {

    private final AnswerValue answer;
    private final ConcurrentMap<String, Integer> sizes;
    private final String filterName;

    public FilterSizeTask(AnswerValue answer, ConcurrentMap<String, Integer> sizes, String filterName) {
      this.answer = answer;
      this.sizes = sizes;
      this.filterName = filterName;
    }

    @Override
    public void run() {
      try {
        int size = answer.getFilterSize(filterName);
        sizes.put(filterName, size);
      }
      catch (WdkModelException | WdkUserException ex) {
        sizes.put(filterName, -1);
      }
    }

  }

  private static final int THREAD_POOL_SIZE = 4;
  private static final int THREAD_POOL_TIMEOUT = 5; // timeout thread pool, in minutes

  private static final Logger logger = Logger.getLogger(AnswerValue.class);

  // ------------------------------------------------------------------
  // Instance variables
  // ------------------------------------------------------------------

  private User user;

  private ResultFactory resultFactory;
  private Question question;

  private QueryInstance idsQueryInstance;

  private int startIndex;
  private int endIndex;

  private String sortedIdSql;

  private Map<PrimaryKeyAttributeValue, RecordInstance> pageRecordInstances;

  private Integer resultSize; // size of total result
  private Map<String, Integer> resultSizesByFilter = new LinkedHashMap<String, Integer>();
  private Map<String, Integer> resultSizesByProject;

  private Map<String, Boolean> sortingMap;

  private AnswerFilterInstance filter;

  // ------------------------------------------------------------------
  // Constructor
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
  public AnswerValue(User user, Question question, QueryInstance idsQueryInstance, int startIndex,
      int endIndex, Map<String, Boolean> sortingMap, AnswerFilterInstance filter) {
    this.user = user;
    this.question = question;
    this.resultFactory = question.getWdkModel().getResultFactory();
    this.idsQueryInstance = idsQueryInstance;
    this.startIndex = startIndex;
    this.endIndex = endIndex;

    // get sorting columns
    if (sortingMap == null)
      sortingMap = question.getSortingAttributeMap();
    this.sortingMap = sortingMap;

    // get the view
    this.filter = filter;

    logger.debug("Answer created.");
    // new Exception().printStackTrace();
  }

  /**
   * A copy constructor, and
   * 
   * @param answerValue
   * @param startIndex
   * @param endIndex
   */
  public AnswerValue(AnswerValue answerValue, int startIndex, int endIndex) {
    this.startIndex = startIndex;
    this.endIndex = endIndex;

    this.user = answerValue.user;
    this.idsQueryInstance = answerValue.idsQueryInstance;
    this.question = answerValue.question;
    this.resultFactory = answerValue.resultFactory;
    this.resultSize = answerValue.resultSize;
    this.resultSizesByFilter = new LinkedHashMap<String, Integer>(answerValue.resultSizesByFilter);
    if (answerValue.resultSizesByProject != null)
      this.resultSizesByProject = new LinkedHashMap<String, Integer>(answerValue.resultSizesByProject);

    this.sortingMap = new LinkedHashMap<String, Boolean>(answerValue.sortingMap);
    this.filter = answerValue.filter;
  }

  // ------------------------------------------------------------------
  // Public Methods
  // ------------------------------------------------------------------

  /**
   * provide property that user's term for question
   */
  /**
   * @return
   */
  public Question getQuestion() {
    return this.question;
  }

  public User getUser() {
    return this.user;
  }

  /**
   * @return
   * @throws WdkModelException
   * @throws WdkUserException
   */
  public int getPageSize() throws WdkModelException, WdkUserException {
    initPageRecordInstances();
    return pageRecordInstances.size();
  }

  public int getPageCount() throws WdkModelException, WdkUserException {
    int total = getResultSize();
    int pageSize = endIndex - startIndex + 1;
    int pageCount = (int) Math.round(Math.ceil((float) total / pageSize));
    logger.debug("#Pages: " + pageCount + ",\t#Total: " + total + ",\t#PerPage: " + pageSize);
    return pageCount;
  }

  public int getResultSize() throws WdkModelException, WdkUserException {
    logger.debug("getting result size: cache=" + resultSize + ", isCached=" + idsQueryInstance.isCached());
    if (resultSize == null || !idsQueryInstance.isCached()) {
      if (filter == null) {
        resultSize = idsQueryInstance.getResultSize();
      }
      else {
        resultSize = getFilterSize(filter.getName());
        resultSizesByFilter.put(filter.getName(), resultSize);
      }
    }
    return resultSize;
  }

  public Map<String, Integer> getResultSizesByProject() throws WdkModelException, WdkUserException {
    if (resultSizesByProject == null) {
      resultSizesByProject = new LinkedHashMap<String, Integer>();

      // make sure the project_id is defined in the record
      PrimaryKeyAttributeField primaryKey = question.getRecordClass().getPrimaryKeyAttributeField();
      if (!primaryKey.hasColumn(Utilities.COLUMN_PROJECT_ID)) {
        String projectId = question.getWdkModel().getProjectId();
        // no project_id defined in the record, use the full size
        resultSizesByProject.put(projectId, getResultSize());
      }
      else {
        // need to run the query first
        ResultList resultList;
        // for portal
        String message = idsQueryInstance.getResultMessage();
        if (filter == null)
          resultList = idsQueryInstance.getResults();
        else
          resultList = filter.getResults(this);

        try {
          boolean hasMessage = (message != null && message.length() > 0);
          if (hasMessage) {
            String[] sizes = message.split(",");
            for (String size : sizes) {
              String[] parts = size.split(":");
              if (parts.length > 1 && parts[1].matches("^\\d++$")) {
                resultSizesByProject.put(parts[0], Integer.parseInt(parts[1]));
              }
              else {
                // make sure if the message is not expected, the
                // correct result size can still be retrieved
                // from
                // cached result.
                hasMessage = false;
              }
            }
          }
          // if the previous step fails, make sure the result size can
          // still be calculated from cache.
          if (!hasMessage) {
            while (resultList.next()) {
              if (!hasMessage) {
                // also count by project
                String project = resultList.get(Utilities.COLUMN_PROJECT_ID).toString();
                int subCounter = 0;
                if (resultSizesByProject.containsKey(project))
                  subCounter = resultSizesByProject.get(project);
                // if subContent < 0, it is an error code. don't
                // change it.
                if (subCounter >= 0)
                  resultSizesByProject.put(project, ++subCounter);
              }
            }
          }
        }
        finally {
          resultList.close();
        }
      }
    }
    return resultSizesByProject;

  }

  public boolean isDynamic() {
    return getQuestion().isDynamic();
  }

  /**
   * @return Map where key is param display name and value is param value
   */
  public Map<String, String> getParamDisplays() {
    Map<String, String> displayParamsMap = new LinkedHashMap<String, String>();
    Map<String, String> paramsMap = idsQueryInstance.getParamStableValues();
    Param[] params = question.getParams();
    for (int i = 0; i < params.length; i++) {
      Param param = params[i];
      displayParamsMap.put(param.getPrompt(), paramsMap.get(param.getName()));
    }
    return displayParamsMap;
  }

  public QueryInstance getIdsQueryInstance() {
    return idsQueryInstance;
  }

  /**
   * @return
   * @throws WdkModelException
   * @throws WdkUserException
   */
  public RecordInstance[] getRecordInstances() throws WdkModelException, WdkUserException {
    initPageRecordInstances();

    RecordInstance[] array = new RecordInstance[pageRecordInstances.size()];
    pageRecordInstances.values().toArray(array);
    return array;
  }

  public RecordInstance getRecordInstance(PrimaryKeyAttributeValue primaryKey) {
    return pageRecordInstances.get(primaryKey);
  }

  public String getQueryChecksum(boolean extra) throws WdkModelException {
    return idsQueryInstance.getQuery().getChecksum(extra);
  }

  /**
   * the checksum of the iq query, plus the filter information on the answer.
   * 
   * @return
   * @throws WdkUserException
   */
  public String getChecksum() throws WdkModelException, WdkUserException {
    String checksum = idsQueryInstance.getChecksum();
    if (filter != null)
      checksum += ":" + filter.getName();
    return checksum;
  }

  /**
   * the answer's key is the checksum of the answer, plus the filter, if any.
   * 
   * @return
   * @throws WdkUserException
   */
  public String getAnswerStringKey() throws WdkModelException, WdkUserException {
    return getChecksum();
  }

  // ///////////////////////////////////////////////////////////////////
  // print methods
  // ///////////////////////////////////////////////////////////////////

  public String printAsRecords() throws WdkModelException, WdkUserException {
    String newline = System.getProperty("line.separator");
    StringBuffer buf = new StringBuffer();

    initPageRecordInstances();

    for (RecordInstance recordInstance : pageRecordInstances.values()) {
      buf.append(recordInstance.print());
      buf.append("---------------------" + newline);
    }
    return buf.toString();
  }

  /**
   * print summary attributes, one per line Note: not sure why this is needed
   * 
   * @throws WdkUserException
   * 
   */
  public String printAsSummary() throws WdkModelException, WdkUserException {
    StringBuffer buf = new StringBuffer();

    initPageRecordInstances();

    for (RecordInstance recordInstance : pageRecordInstances.values()) {
      buf.append(recordInstance.printSummary());
    }
    return buf.toString();
  }

  /**
   * print summary attributes in tab delimited table with header of attr. names
   * 
   * @throws WdkUserException
   * 
   */
  public String printAsTable() throws WdkModelException, WdkUserException {
    String newline = System.getProperty("line.separator");
    StringBuffer buf = new StringBuffer();

    initPageRecordInstances();

    // print summary info
    buf.append("# of Records: " + getResultSize() + ",\t# of Pages: " + getPageCount() +
        ",\t# Records per Page: " + getPageSize() + newline);

    if (pageRecordInstances.size() == 0)
      return buf.toString();

    Map<String, AttributeField> attributes = getSummaryAttributeFieldMap();
    for (String nextAttName : attributes.keySet()) {
      buf.append(nextAttName + "\t");
    }
    buf.append(newline);
    for (RecordInstance recordInstance : pageRecordInstances.values()) {
      // only print
      for (String nextAttName : attributes.keySet()) {
        // make data row
        AttributeValue value = recordInstance.getAttributeValue(nextAttName);
        // only print part of the string
        String str = value.getBriefDisplay();
        buf.append(str + "\t");
      }
      buf.append(newline);
    }
    return buf.toString();
  }

  /**
   * Creates a reporter that covers all rows in the answer.
   * 
   * @param reporterName
   * @param config
   * @return
   * @throws WdkUserException
   */
  public Reporter createReport(String reporterName, Map<String, String> config) throws WdkModelException,
      WdkUserException {
    // get the full answer
    int endI = getResultSize();
    return createReport(reporterName, config, 1, endI);
  }

  public Reporter createReport(String reporterName, Map<String, String> config, int startI, int endI)
      throws WdkModelException {
    // get Reporter
    Map<String, ReporterRef> rptMap = question.getRecordClass().getReporterMap();
    ReporterRef rptRef = rptMap.get(reporterName);
    if (rptRef == null)
      throw new WdkModelException("The reporter " + reporterName + " is " + "not registered for " +
          question.getRecordClass().getFullName());
    String rptImp = rptRef.getImplementation();
    if (rptImp == null)
      throw new WdkModelException("The reporter " + reporterName + " is " + "not registered for " +
          question.getRecordClass().getFullName());

    try {
      Class<?> rptClass = Class.forName(rptImp);
      Class<?>[] paramClasses = { AnswerValue.class, int.class, int.class };
      Constructor<?> constructor = rptClass.getConstructor(paramClasses);

      Object[] params = { this, startI, endI };
      Reporter reporter = (Reporter) constructor.newInstance(params);
      reporter.setProperties(rptRef.getProperties());
      reporter.configure(config);
      reporter.setWdkModel(rptRef.getWdkModel());
      return reporter;
    }
    catch (ClassNotFoundException ex) {
      throw new WdkModelException(ex);
    }
    catch (InstantiationException ex) {
      throw new WdkModelException(ex);
    }
    catch (IllegalAccessException ex) {
      throw new WdkModelException(ex);
    }
    catch (SecurityException ex) {
      throw new WdkModelException(ex);
    }
    catch (NoSuchMethodException ex) {
      throw new WdkModelException(ex);
    }
    catch (IllegalArgumentException ex) {
      throw new WdkModelException(ex);
    }
    catch (InvocationTargetException ex) {
      throw new WdkModelException(ex);
    }
  }

  /**
   * Iterate through all the pages of the answer, and each page is represented by an AnswerValue object.
   * 
   * @return
   * @throws WdkUserException
   */
  public Iterable<AnswerValue> getFullAnswers() throws WdkModelException, WdkUserException {
    // user tabular reporter as answer iterator
    int resultSize = this.getResultSize();
    TabularReporter reporter = new TabularReporter(this, 1, resultSize);
    return reporter;
  }

  // ------------------------------------------------------------------
  // Package Methods
  // ------------------------------------------------------------------

  /**
   * Integrate into the page's RecordInstances the attribute values from a particular attributes query. The
   * attributes query result includes only rows for this page.
   * 
   * The query is obtained from Column, and the query should not be modified.
   * 
   * @throws WdkUserException
   * 
   */
  public void integrateAttributesQuery(Query attributeQuery) throws WdkModelException, WdkUserException {
    initPageRecordInstances();

    WdkModel wdkModel = question.getWdkModel();
    // has to get a clean copy of the attribute query, without pk params
    // appended
    attributeQuery = (Query) wdkModel.resolveReference(attributeQuery.getFullName());

    logger.debug("filling attribute values from answer " + attributeQuery.getFullName());
    for (Column column : attributeQuery.getColumns()) {
      logger.trace("column: '" + column.getName() + "'");
    }
    if (attributeQuery instanceof SqlQuery)
      logger.debug("SQL: \n" + ((SqlQuery) attributeQuery).getSql());

    String sql = getPagedAttributeSql(attributeQuery);
    int count = 0;

    // get and run the paged attribute query sql
    DatabaseInstance platform = wdkModel.getAppDb();
    DataSource dataSource = platform.getDataSource();

    ResultList resultList = null;
    try {
      resultList = new SqlResultList(SqlUtils.executeQuery(dataSource, sql, attributeQuery.getFullName() + "__attr-paged"));
  
      // fill in the column attributes
      PrimaryKeyAttributeField pkField = question.getRecordClass().getPrimaryKeyAttributeField();
      Map<String, AttributeField> fields = question.getAttributeFieldMap();
      
      while (resultList.next()) {
        // get primary key
        Map<String, Object> pkValues = new LinkedHashMap<String, Object>();
        for (String column : pkField.getColumnRefs()) {
          pkValues.put(column, resultList.get(column));
        }
        PrimaryKeyAttributeValue primaryKey = new PrimaryKeyAttributeValue(pkField, pkValues);
        RecordInstance record = pageRecordInstances.get(primaryKey);
  
        if (record == null) {
          StringBuffer error = new StringBuffer();
          error.append("Paged attribute query [");
          error.append(attributeQuery.getFullName());
          error.append("] returns rows that doesn't match the paged ");
          error.append("records. (");
          for (String pkName : pkValues.keySet()) {
            error.append(pkName).append(" = ");
            error.append(pkValues.get(pkName)).append(", ");
          }
          error.append(").\nPaged Attribute SQL:\n").append(sql);
          error.append("\n").append("Paged ID SQL:\n").append(getPagedIdSql());
          throw new WdkModelException(error.toString());
        }
  
        // fill in the column attributes
        for (String columnName : attributeQuery.getColumnMap().keySet()) {
          AttributeField field = fields.get(columnName);
          if (field != null && (field instanceof ColumnAttributeField)) {
            // valid attribute field, fill it in
            Object objValue = resultList.get(columnName);
            ColumnAttributeValue value = new ColumnAttributeValue((ColumnAttributeField) field, objValue);
            record.addAttributeValue(value);
          }
        }
        count++;
      }
    }
    catch (SQLException e) {
      throw new WdkModelException(e);
    }
    finally {
      if (resultList != null) resultList.close();
    }

    if (count != pageRecordInstances.size()) {
      throw new WdkModelException("the integrated attribute query " +
          "doesn't return the same number of records in the current " + "page. Paged attribute sql:\n" + sql);
    }
    logger.debug("Attribute query [" + attributeQuery.getFullName() + "] integrated.");
  }

  // ------------------------------------------------------------------
  // Private Methods
  // ------------------------------------------------------------------

  private String getPagedAttributeSql(Query attributeQuery) throws WdkModelException, WdkUserException {
    // get the paged SQL of id query
    String idSql = getPagedIdSql();

    PrimaryKeyAttributeField pkField = question.getRecordClass().getPrimaryKeyAttributeField();

    // combine the id query with attribute query
    String attributeSql = getAttributeSql(attributeQuery);
    StringBuffer sql = new StringBuffer(" /* the desired attributes, for a page of sorted results */ "
        + " SELECT aq.* FROM (");
    sql.append(idSql);
    sql.append(") pidq, (/* attribute query that returns attributes in a page */ ").append(attributeSql).append(
        ") aq WHERE ");

    boolean firstColumn = true;
    for (String column : pkField.getColumnRefs()) {
      if (firstColumn)
        firstColumn = false;
      else
        sql.append(" AND ");
      sql.append("aq.").append(column).append(" = pidq.").append(column);
    }
    return sql.toString();
  }

  public void integrateTableQuery(TableField tableField) throws WdkModelException, WdkUserException {
    initPageRecordInstances();

    WdkModel wdkModel = question.getWdkModel();
    // has to get a clean copy of the attribute query, without pk params
    // appended
    Query tableQuery = tableField.getQuery();
    tableQuery = (Query) wdkModel.resolveReference(tableQuery.getFullName());

    logger.debug("integrate table query from answer: " + tableQuery.getFullName());
    for (Param param : tableQuery.getParams()) {
      logger.debug("param: " + param.getName());
    }

    // get and run the paged attribute query sql
    String sql = getPagedTableSql(tableQuery);

    DatabaseInstance platform = wdkModel.getAppDb();
    DataSource dataSource = platform.getDataSource();
    ResultSet resultSet = null;
    try {
      resultSet = SqlUtils.executeQuery(dataSource, sql, tableQuery.getFullName() + "_table-paged");
    }
    catch (SQLException e) {
      throw new WdkModelException(e);
    }
    ResultList resultList = new SqlResultList(resultSet);

    // initialize table values
    for (RecordInstance record : pageRecordInstances.values()) {
      PrimaryKeyAttributeValue primaryKey = record.getPrimaryKey();
      TableValue tableValue = new TableValue(user, primaryKey, tableField, true);
      record.addTableValue(tableValue);
    }

    // make table values
    PrimaryKeyAttributeField pkField = question.getRecordClass().getPrimaryKeyAttributeField();
    while (resultList.next()) {
      // get primary key
      Map<String, Object> pkValues = new LinkedHashMap<String, Object>();
      for (String column : pkField.getColumnRefs()) {
        pkValues.put(column, resultList.get(column));
      }
      PrimaryKeyAttributeValue primaryKey = new PrimaryKeyAttributeValue(pkField, pkValues);
      RecordInstance record = pageRecordInstances.get(primaryKey);
      primaryKey.setValueContainer(record);

      if (record == null) {
        StringBuffer error = new StringBuffer();
        error.append("Paged table query [" + tableQuery.getFullName());
        error.append("] returned rows that doesn't match the paged ");
        error.append("records. (");
        for (String pkName : pkValues.keySet()) {
          Object pkValue = pkValues.get(pkName);
          error.append(pkName + " = " + pkValue + ", ");
        }
        error.append(").\nPaged table SQL:\n" + sql);
        error.append("\n" + "Paged ID SQL:\n" + getPagedIdSql());
        throw new WdkModelException(error.toString());
      }

      TableValue tableValue = record.getTableValue(tableField.getName());
      // initialize a row in table value
      tableValue.initializeRow(resultList);
    }
    logger.debug("Table query [" + tableQuery.getFullName() + "] integrated.");
  }

  private String getPagedTableSql(Query tableQuery) throws WdkModelException, WdkUserException {
    // get the paged SQL of id query
    String idSql = getPagedIdSql();

    PrimaryKeyAttributeField pkField = question.getRecordClass().getPrimaryKeyAttributeField();

    // combine the id query with attribute query
    // make an instance from the original attribute query, and attribute
    // query has only one param, user_id. Note that the original
    // attribute query is different from the attribute query held by the
    // recordClass.
    Map<String, String> params = new LinkedHashMap<String, String>();
    String userId = Integer.toString(user.getUserId());
    params.put(Utilities.PARAM_USER_ID, userId);
    QueryInstance queryInstance = tableQuery.makeInstance(user, params, true, 0,
        new LinkedHashMap<String, String>());
    String tableSql = queryInstance.getSql();
    StringBuffer sql = new StringBuffer("SELECT tq.* FROM (");
    sql.append(idSql);
    sql.append(") pidq, (").append(tableSql).append(") tq WHERE ");

    boolean firstColumn = true;
    for (String column : pkField.getColumnRefs()) {
      if (firstColumn)
        firstColumn = false;
      else
        sql.append(" AND ");
      sql.append("tq.").append(column).append(" = pidq.").append(column);
    }

    // replace the id_sql macro
    return sql.toString().replace(Utilities.MACRO_ID_SQL, idSql);
  }

  public String getAttributeSql(Query attributeQuery) throws WdkModelException, WdkUserException {
    String queryName = attributeQuery.getFullName();
    Query dynaQuery = question.getDynamicAttributeQuery();
    String idSql = idsQueryInstance.getSql();
    String sql;
    if (dynaQuery != null && queryName.equals(dynaQuery.getFullName())) {
      // the dynamic query doesn't have sql defined, the sql will be
      // constructed from the id query cache table.
      sql = idSql;
    }
    else {
      // make an instance from the original attribute query, and attribute
      // query has only one param, user_id. Note that the original
      // attribute query is different from the attribute query held by the
      // recordClass.
      Map<String, String> params = new LinkedHashMap<String, String>();
      String userId = Integer.toString(user.getUserId());
      params.put(Utilities.PARAM_USER_ID, userId);
      QueryInstance queryInstance;
      try {
        queryInstance = attributeQuery.makeInstance(user, params, true, 0,
            new LinkedHashMap<String, String>());
      }
      catch (WdkUserException ex) {
        throw new WdkModelException(ex);
      }
      sql = queryInstance.getSql();

      // replace the id_sql macro
      sql = sql.replace(Utilities.MACRO_ID_SQL, idSql);
    }
    return sql;
  }

  public String getSortedIdSql() throws WdkModelException, WdkUserException {
    if (sortedIdSql != null)
      return sortedIdSql;

    String[] pkColumns = question.getRecordClass().getPrimaryKeyAttributeField().getColumnRefs();

    // get id sql
    String idSql = getIdSql();

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
    sortedIdSql = sql.toString();

    logger.debug("sorted id sql constructed.");
    return sortedIdSql;
  }

  private String getPagedIdSql() throws WdkModelException, WdkUserException {
    String sortedIdSql = getSortedIdSql();
    DatabaseInstance platform = question.getWdkModel().getAppDb();
    String sql = platform.getPlatform().getPagedSql(sortedIdSql, startIndex, endIndex);

    // add comments to the sql
    sql = " /* a page of sorted ids */ " + sql;

    logger.debug("paged id sql constructed.");

    return sql;
  }

  public String getIdSql() throws WdkModelException, WdkUserException {
    String innerSql = idsQueryInstance.getSql();

    // add comments to id sql
    innerSql = " /* the ID query */" + innerSql;

    int assignedWeight = idsQueryInstance.getAssignedWeight();
    // apply filter
    if (filter != null) {
      innerSql = filter.applyFilter(user, innerSql, assignedWeight);
      innerSql = " /* filter applied on id query */ " + innerSql;
    }
    innerSql = "(" + innerSql + ")";

    logger.debug("id sql constructed.");

    return innerSql;
  }

  private void prepareSortingSqls(Map<String, String> sqls, Collection<String> orders)
      throws WdkModelException, WdkUserException {
    Map<String, AttributeField> fields = question.getAttributeFieldMap();
    Map<String, String> querySqls = new LinkedHashMap<String, String>();
    Map<String, String> queryNames = new LinkedHashMap<String, String>();
    Map<String, String> orderClauses = new LinkedHashMap<String, String>();
    WdkModel wdkModel = question.getWdkModel();
    logger.debug("sorting map: " + sortingMap);
    for (String fieldName : sortingMap.keySet()) {
      AttributeField field = fields.get(fieldName);
      if (field == null)
        continue;
      boolean ascend = sortingMap.get(fieldName);
      Map<String, ColumnAttributeField> dependents = field.getColumnAttributeFields();
      for (ColumnAttributeField dependent : dependents.values()) {
        Column column = dependent.getColumn();
        logger.debug("field [" + fieldName + "] depends on column [" + column.getName() + "]");
        Query query = column.getQuery();
        String queryName = query.getFullName();
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
        String sortingColumn = column.getSortingColumn();
        if (sortingColumn == null)
          sortingColumn = column.getName();
        boolean ignoreCase = column.isIgnoreCase();
        if (!orderClauses.containsKey(sortingColumn)) {
          // dependent not processed, process it
          StringBuffer clause = new StringBuffer();
          if (ignoreCase)
            clause.append("lower(");
          clause.append(queryNames.get(queryName));
          clause.append(".");
          clause.append(sortingColumn);
          if (ignoreCase)
            clause.append(")");
          clause.append(ascend ? " ASC" : " DESC");
          orderClauses.put(sortingColumn, clause.toString());
        }
      }
    }

    // fill the map of short name and sqls
    for (String queryName : queryNames.keySet()) {
      String shortName = queryNames.get(queryName);
      String sql = querySqls.get(queryName);
      sqls.put(shortName, sql);
    }
    orders.addAll(orderClauses.values());
  }

  /**
   * If not already initialized, initialize the page's record instances, setting each with its id (either just
   * primary key or that and project, if using a federated data source).
   * 
   * @throws WdkUserException
   * 
   */
  private void initPageRecordInstances() throws WdkModelException, WdkUserException {
    if (pageRecordInstances != null)
      return;

    logger.debug("Initializing paged records......");
    this.pageRecordInstances = new LinkedHashMap<PrimaryKeyAttributeValue, RecordInstance>();

    String sql = getPagedIdSql();
    WdkModel wdkModel = question.getWdkModel();
    DatabaseInstance platform = wdkModel.getAppDb();
    DataSource dataSource = platform.getDataSource();
    ResultSet resultSet;
    try {
      resultSet = SqlUtils.executeQuery(dataSource, sql, idsQueryInstance.getQuery().getFullName() +
          "__id-paged");
    }
    catch (SQLException e) {
      throw new WdkModelException(e);
    }
    ResultList resultList = new SqlResultList(resultSet);
    RecordClass recordClass = question.getRecordClass();
    PrimaryKeyAttributeField pkField = recordClass.getPrimaryKeyAttributeField();
    while (resultList.next()) {
      // get primary key. the primary key is supposed to be translated to
      // the current ones from the id query, and no more translation
      // needed.
      //
      // If this assumption is false, then we need to join the alias query
      // into the paged id query as well.
      Map<String, Object> pkValues = new LinkedHashMap<String, Object>();
      for (String column : pkField.getColumnRefs()) {
        Object value = resultList.get(column);
        pkValues.put(column, value);
      }
      RecordInstance record = new RecordInstance(this, pkValues);
      pageRecordInstances.put(record.getPrimaryKey(), record);
    }

    // check if the number of records is expected
    int resultSize = getResultSize();
    int expected = Math.min(endIndex, resultSize) - startIndex + 1;

    if (expected != pageRecordInstances.size()) {
      StringBuffer buffer = new StringBuffer();
      for (String name : getSummaryAttributeFieldMap().keySet()) {
        if (buffer.length() > 0)
          buffer.append(", ");
        buffer.append(name);
      }
      logger.debug("resultSize: " + resultSize + ", start: " + startIndex + ", end: " + endIndex);
      logger.debug("expected: " + expected + ", actual: " + pageRecordInstances.size());
      logger.debug("Paged ID SQL:\n" + sql);
      throw new WdkModelException("The number of results returned " + "by the id query " +
          idsQueryInstance.getQuery().getFullName() +
          " changes when it is joined to the query (or queries) " + "for attribute set (" + buffer + ").\n" +
          "id query: " + expected + " records\n" + "join(id query, attribute query): " +
          pageRecordInstances.size() + " records\n" +
          "Check that the ID query returns no nulls or duplicates, " + "and that the attribute-query join " +
          "does not change the row count.");
    }

    logger.debug("Paged records initialized.");
  }

  /**
   * @return Returns the endRecordInstanceI.
   */
  public int getEndIndex() {
    return endIndex;
  }

  /**
   * @return Returns the startRecordInstanceI.
   */
  public int getStartIndex() {
    return startIndex;
  }

  public String getResultMessage() {
    return idsQueryInstance.getResultMessage();
  }

  public Map<String, Boolean> getSortingMap() {
    return new LinkedHashMap<String, Boolean>(sortingMap);
  }

  /**
   * Set a new sorting map
   * 
   * @param sortingMap
   */
  public void setSortingMap(Map<String, Boolean> sortingMap) {
    if (sortingMap == null) {
      sortingMap = question.getSortingAttributeMap();
    }
    // make sure all sorting columns exist
    StringBuilder buffer = new StringBuilder("set sorting: ");
    Map<String, AttributeField> attributes = question.getAttributeFieldMap();
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
        logger.debug("Invalid sorting attribute: User #" + user.getUserId() + ", question: '" +
            question.getFullName() + "', attribute: '" + attributeName + "'");
      }
      else {
        validMap.put(attributeName, sortingMap.get(attributeName));
      }
    }
    logger.debug(buffer);
    this.sortingMap.clear();
    this.sortingMap.putAll(validMap);

    this.sortedIdSql = null;
    this.pageRecordInstances = null;
  }

  public List<AttributeField> getDisplayableAttributes() {
    Map<String, AttributeField> map = getDisplayableAttributeMap();
    return new ArrayList<AttributeField>(map.values());
  }

  /**
   * The displayable includes all attributes that is not internal. It also contains all the summary attributes
   * that are currently displayed.
   * 
   * @return
   */
  public Map<String, AttributeField> getDisplayableAttributeMap() {
    Map<String, AttributeField> displayAttributes = new LinkedHashMap<String, AttributeField>();
    Map<String, AttributeField> attributes = question.getAttributeFieldMap(FieldScope.NON_INTERNAL);
    // Map<String, AttributeField> summaryAttributes =
    // this.getSummaryAttributeFieldMap();
    for (String attriName : attributes.keySet()) {
      AttributeField attribute = attributes.get(attriName);

      // skip the attributes that are already displayed
      // if (summaryAttributes.containsKey(attriName)) continue;

      displayAttributes.put(attriName, attribute);
    }
    return displayAttributes;
  }

  public TreeNode getDisplayableAttributeTree() throws WdkModelException {
    return convertAttributeTree(question.getAttributeCategoryTree(FieldScope.NON_INTERNAL));
  }

  public TreeNode getReportMakerAttributeTree() throws WdkModelException {
    return convertAttributeTree(question.getAttributeCategoryTree(FieldScope.REPORT_MAKER));
  }

  private TreeNode convertAttributeTree(AttributeCategoryTree rawAttributeTree) throws WdkModelException {
    TreeNode root = rawAttributeTree.toTreeNode("category root", "Attribute Categories");
    List<String> currentlySelectedFields = new ArrayList<String>();
    for (AttributeField field : getSummaryAttributeFieldMap().values()) {
      currentlySelectedFields.add(field.getName());
    }
    root.turnOnSelectedLeaves(currentlySelectedFields);
    root.setDefaultLeaves(new ArrayList<String>(question.getSummaryAttributeFieldMap().keySet()));
    return root;
  }

  // private Map<String, AttributeField> summaryFieldMap;
  // this.summaryFieldMap = new LinkedHashMap<String, AttributeField>();

  public Map<String, AttributeField> getSummaryAttributeFieldMap() throws WdkModelException {

    // get preferred attribs from user and initialize map
    String[] userPrefAttributes = user.getSummaryAttributes(question.getFullName());
    Map<String, AttributeField> summaryFields = new LinkedHashMap<String, AttributeField>();

    // always put the primary key as the first attribute
    PrimaryKeyAttributeField pkField = question.getRecordClass().getPrimaryKeyAttributeField();
    summaryFields.put(pkField.getName(), pkField);

    // add remainder of attributes to map and return
    Map<String, AttributeField> allFields = question.getAttributeFieldMap();
    for (String attributeName : userPrefAttributes) {
      AttributeField field = allFields.get(attributeName);
      if (field != null)
        summaryFields.put(attributeName, field);
    }
    return summaryFields;
  }

  /**
   * This method is redundant with getAllIds(), consider deprecate either one of them.
   * 
   * @return returns a list of all primary key values.
   * @throws WdkUserException
   */
  public Object[][] getPrimaryKeyValues() throws WdkModelException, WdkUserException {
    String[] columns = question.getRecordClass().getPrimaryKeyAttributeField().getColumnRefs();
    List<Object[]> buffer = new ArrayList<Object[]>();

    ResultList resultList;
    if (filter == null)
      resultList = idsQueryInstance.getResults();
    else
      resultList = filter.getResults(this);

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

  public Map<String, Integer> getFilterSizes() {
    RecordClass recordClass = question.getRecordClass();
    AnswerFilterInstance[] filters = recordClass.getFilters();
    ConcurrentMap<String, Integer> sizes = new ConcurrentHashMap<>(filters.length);

    // use a thread pool to get filter sizes in parallel
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    for (AnswerFilterInstance filter : filters) {
      executor.execute(new FilterSizeTask(this, sizes, filter.getName()));
    }

    // wait for executor to finish.
    executor.shutdown();
    try {
      if (!executor.awaitTermination(THREAD_POOL_TIMEOUT, TimeUnit.MINUTES)) {
        executor.shutdownNow();
      }
    }
    catch (InterruptedException ex) {
      executor.shutdownNow();
    }

    return sizes;
  }

  public int getFilterSize(String filterName) throws WdkModelException, WdkUserException {
    Integer size = resultSizesByFilter.get(filterName);
    if (size != null && idsQueryInstance.isCached()) {
      return size;
    }
    try {
      RecordClass recordClass = question.getRecordClass();

      String innerSql = idsQueryInstance.getSql();
      int assignedWeight = idsQueryInstance.getAssignedWeight();

      // ignore invalid filters
      AnswerFilterInstance filter = recordClass.getFilter(filterName);
      if (filter != null)
        innerSql = filter.applyFilter(user, innerSql, assignedWeight);

      StringBuffer sql = new StringBuffer("SELECT count(*) FROM ");
      sql.append("(").append(innerSql).append(") f");

      WdkModel wdkModel = question.getWdkModel();
      DataSource dataSource = wdkModel.getAppDb().getDataSource();
      Object result = SqlUtils.executeScalar(dataSource, sql.toString(),
          idsQueryInstance.getQuery().getFullName() + "__" + filterName + "-filter-size");
      size = Integer.parseInt(result.toString());

      resultSizesByFilter.put(filterName, size);

      return size;
    }
    catch (SQLException e) {
      throw new WdkModelException("Unable to get filter size for filter " + filterName, e);
    }
  }

  /**
   * @return the filter
   */
  public AnswerFilterInstance getFilter() {
    return filter;
  }

  public void setFilter(String filterName) {
    if (filterName != null) {
      RecordClass recordClass = question.getRecordClass();
      setFilter(recordClass.getFilter(filterName));
    }
    else
      this.filter = null;
  }

  /**
   * @param filter
   *          the filter to set
   */
  public void setFilter(AnswerFilterInstance filter) {
    this.filter = filter;
    reset();
  }

  private void reset() {
    sortedIdSql = null;
    pageRecordInstances = null;
    resultSize = null;
    resultSizesByFilter.clear();
    resultSizesByProject = null;
  }

  /**
   * Get a list of all the primary key tuples of all the records in the answer. It is a shortcut of iterating
   * through all the pages and get the primary keys.
   * 
   * This method is redundant with getPrimaryKeyValues()(), consider deprecate either one of them.
   * 
   * @return
   * @throws WdkUserException
   */
  public List<String[]> getAllIds() throws WdkModelException, WdkUserException {
    String idSql = getSortedIdSql();
    PrimaryKeyAttributeField pkField = question.getRecordClass().getPrimaryKeyAttributeField();
    String[] pkColumns = pkField.getColumnRefs();
    List<String[]> pkValues = new ArrayList<String[]>();
    WdkModel wdkModel = question.getWdkModel();
    DataSource dataSource = wdkModel.getAppDb().getDataSource();
    ResultSet resultSet = null;
    try {
      resultSet = SqlUtils.executeQuery(dataSource, idSql, idsQueryInstance.getQuery().getFullName() +
          "__all-ids");
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
      SqlUtils.closeResultSetAndStatement(resultSet);
    }
    return pkValues;
  }

  public boolean isUseBooleanFilter() {
    if (idsQueryInstance instanceof BooleanQueryInstance) {
      return ((BooleanQueryInstance) idsQueryInstance).isUseBooleanFilter();
    }
    else
      return false;
  }

  public void setPageIndex(int startIndex, int endIndex) {
    this.startIndex = startIndex;
    this.endIndex = endIndex;
    this.sortedIdSql = null;
    this.pageRecordInstances = null;
  }
}
