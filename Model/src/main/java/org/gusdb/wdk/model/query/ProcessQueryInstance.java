package org.gusdb.wdk.model.query;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.db.platform.DBPlatform;
import org.gusdb.wdk.model.ServiceResolver;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.dbms.ArrayResultList;
import org.gusdb.wdk.model.dbms.CacheFactory;
import org.gusdb.wdk.model.dbms.ResultList;
import org.gusdb.wdk.model.user.User;
import org.gusdb.wsf.client.WsfClient;
import org.gusdb.wsf.client.WsfClientFactory;
import org.gusdb.wsf.client.WsfResponseListener;
import org.gusdb.wsf.common.WsfException;
import org.gusdb.wsf.common.WsfRequest;
import org.gusdb.wsf.common.WsfUserException;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This process query instance calls the web service, retrieves the result, and cache them into the cache
 * table. If the result generated by the service is too big, the service will send it in multiple packets, and
 * the process query instance will retrieve all the packets in order.
 * 
 * @author Jerric Gao
 */
public class ProcessQueryInstance extends QueryInstance {

  private static final Logger logger = Logger.getLogger(ProcessQueryInstance.class);

  private static WsfClientFactory _wsfClientFactory = ServiceResolver.resolve(WsfClientFactory.class);

  private ProcessQuery query;
  private int signal;

  public ProcessQueryInstance(User user, ProcessQuery query, Map<String, String> values, boolean validate,
      int assignedWeight, Map<String, String> context) throws WdkModelException, WdkUserException {
    super(user, query, values, validate, assignedWeight, context);
    this.query = query;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.QueryInstance#appendSJONContent(org.json.JSONObject )
   */
  @Override
  protected void appendSJONContent(JSONObject jsInstance) throws JSONException {
    jsInstance.put("signal", signal);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.QueryInstance#insertToCache(java.sql.Connection , java.lang.String)
   */
  @Override
  public void insertToCache(String tableName, int instanceId) throws WdkModelException, WdkUserException {
    logger.debug("inserting process query result to cache...");
    List<Column> columns = Arrays.asList(query.getColumns());
    Set<String> columnNames = query.getColumnMap().keySet();

    // prepare the sql
    String sql = buildCacheInsertSql(tableName, instanceId, columns, columnNames);

    // get results and time process
    long startTime = System.currentTimeMillis();
    DataSource dataSource = wdkModel.getAppDb().getDataSource();
    PreparedStatement psInsert = null;
    try {
      // make a prepared statement, and invoke the wsf;
      psInsert = SqlUtils.getPreparedStatement(dataSource, sql);
      int batchSize = query.getCacheInsertBatchSize();
      ProcessQueryResponseListener listener = new ProcessQueryResponseListener(columns, psInsert, batchSize);
      invokeWsf(listener);
      this.resultMessage = listener.getMessage();
      // currently, the attachments from the WSF are ignored.

      // finish up the last batch
      psInsert.executeBatch();
    }
    catch (SQLException ex) {
      throw new WdkModelException("Unable to insert record into cache.", ex);
    }
    finally {
      SqlUtils.closeStatement(psInsert);
    }
    logger.debug("Process query cache insertion finished, and took " +
        ((System.currentTimeMillis() - startTime) / 1000D) + " seconds");
  }

  private void invokeWsf(WsfResponseListener listener) throws WdkModelException, WdkUserException {
    long start = System.currentTimeMillis();

    // prepare request.
    WsfRequest request = new WsfRequest();
    request.setPluginClass(query.getProcessName());
    request.setProjectId(wdkModel.getProjectId());

    // prepare parameters
    Map<String, String> paramValues = getParamInternalValues();
    HashMap<String, String> params = new HashMap<String, String>();
    for (String name : paramValues.keySet()) {
      params.put(name, paramValues.get(name));
    }
    request.setParams(params);

    // prepare columns
    Map<String, Column> columns = query.getColumnMap();
    String[] columnNames = new String[columns.size()];
    Map<String, Integer> indices = new LinkedHashMap<String, Integer>();
    columns.keySet().toArray(columnNames);
    for (int i = 0; i < columnNames.length; i++) {
      // if the wsName is defined, reassign it to the columns
      Column column = columns.get(columnNames[i]);
      if (column.getWsName() != null)
        columnNames[i] = column.getWsName();
      indices.put(column.getName(), i);
    }
    request.setOrderedColumns(columnNames);
    request.setContext(context);

    // create client
    WsfClient client;
    try {
      if (query.isLocal()) {
        logger.debug("Using local service...");
        client = _wsfClientFactory.newClient(listener);
      }
      else {
        logger.debug("Using remote service at " + query.getWebServiceUrl() + "...");
        client = _wsfClientFactory.newClient(listener, new URI(query.getWebServiceUrl()));
      }

      // invoke the WSF, and set signal.
      logger.debug("Invoking " + request.getPluginClass() + "...");
      this.signal = client.invoke(request);
    }
    catch (WsfUserException ex) {
      throw new WdkUserException(ex);
    }
    catch (WsfException | URISyntaxException ex) {
      throw new WdkModelException(ex);
    }
    long end = System.currentTimeMillis();
    logger.debug("Client took " + ((end - start) / 1000.0) + " seconds.");
  }

  private String buildCacheInsertSql(String tableName, int instanceId, List<Column> columns,
      Set<String> columnNames) {

    String weightColumn = Utilities.COLUMN_WEIGHT;

    StringBuilder sql = new StringBuilder("INSERT INTO " + tableName);
    sql.append(" (").append(CacheFactory.COLUMN_INSTANCE_ID + ", ");
    sql.append(CacheFactory.COLUMN_ROW_ID);

    // have to move clobs to the end of bind variables or Oracle will complain
    for (Column column : columns) {
      if (column.getType() != ColumnType.CLOB)
        sql.append(", ").append(column.getName());
    }
    for (Column column : columns) {
      if (column.getType() == ColumnType.CLOB)
        sql.append(", ").append(column.getName());
    }

    // insert name of weight as the last column, if it doesn't exist
    if (query.isHasWeight() && !columnNames.contains(weightColumn))
      sql.append(", ").append(weightColumn);

    sql.append(") VALUES (");
    sql.append(instanceId).append(", ?");
    for (int i = 0; i < columns.size(); i++) {
      sql.append(", ?");
    }

    // insert weight to the last column, if doesn't exist
    if (query.isHasWeight() && !columnNames.contains(weightColumn))
      sql.append(", ").append(assignedWeight);

    return sql.append(")").toString();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.QueryInstance#getUncachedResults(org.gusdb. wdk.model.Column[],
   * java.lang.Integer, java.lang.Integer)
   */
  @Override
  protected ResultList getUncachedResults() throws WdkModelException, WdkUserException {
    // prepare columns
    Map<String, Column> columns = query.getColumnMap();
    String[] columnNames = new String[columns.size()];
    Map<String, Integer> indices = new LinkedHashMap<String, Integer>();
    for (int i = 0; i < columnNames.length; i++) {
      // if the wsName is defined, reassign it to the columns
      Column column = columns.get(columnNames[i]);
      columnNames[i] = column.getWsName();
      indices.put(column.getName(), i);
    }

    // add weight if needed
    // String weightColumn = Utilities.COLUMN_WEIGHT;
    // if (query.isHasWeight() && !columns.containsKey(weightColumn)) {
    // indices.put(weightColumn, indices.size());
    // for (int i = 0; i < content.length; i++) {
    // content[i] = ArrayUtil.append(content[i], Integer.toString(assignedWeight));
    // }
    // }

    ArrayResultList resultList = new ArrayResultList(indices);
    resultList.setHasWeight(query.isHasWeight());
    resultList.setAssignedWeight(assignedWeight);

    invokeWsf(resultList);
    this.resultMessage = resultList.getMessage();

    logger.debug("WSQI Result Message:" + resultMessage);
    logger.info("Result Array size = " + resultList.getSize());
    return resultList;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.QueryInstance#getSql()
   */
  @Override
  public String getSql() throws WdkModelException, WdkUserException {
    // always get sql that queries on the cached result
    return getCachedSql();
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.QueryInstance#createCache(java.sql.Connection, java.lang.String, int)
   */
  @Override
  public void createCache(String tableName, int instanceId) throws WdkModelException, WdkUserException {
    logger.debug("creating process query cache...");
    DBPlatform platform = query.getWdkModel().getAppDb().getPlatform();
    Column[] columns = query.getColumns();

    StringBuffer sqlTable = new StringBuffer("CREATE TABLE " + tableName + " (");

    // define the instance id column
    String numberType = platform.getNumberDataType(12);
    sqlTable.append(CacheFactory.COLUMN_INSTANCE_ID + " " + numberType + " NOT NULL, ");
    sqlTable.append(CacheFactory.COLUMN_ROW_ID + " " + numberType + " NOT NULL");
    if (query.isHasWeight())
      sqlTable.append(", " + Utilities.COLUMN_WEIGHT + " " + numberType);

    // define the rest of the columns
    for (Column column : columns) {
      // weight column is already added to the sql.
      if (column.getName().equals(Utilities.COLUMN_WEIGHT) && query.isHasWeight())
        continue;

      int width = column.getWidth();
      ColumnType type = column.getType();

      String strType;
      if (type == ColumnType.BOOLEAN) {
        strType = platform.getBooleanDataType();
      }
      else if (type == ColumnType.CLOB) {
        strType = platform.getClobDataType();
      }
      else if (type == ColumnType.DATE) {
        strType = platform.getDateDataType();
      }
      else if (type == ColumnType.FLOAT) {
        strType = platform.getFloatDataType(width);
      }
      else if (type == ColumnType.NUMBER) {
        strType = platform.getNumberDataType(width);
      }
      else if (type == ColumnType.STRING) {
        strType = platform.getStringDataType(width);
      }
      else {
        throw new WdkModelException("Unknown data type [" + type + "] of column [" + column.getName() + "]");
      }

      sqlTable.append(", " + column.getName() + " " + strType);
    }
    sqlTable.append(")");

    try {
      DataSource dataSource = wdkModel.getAppDb().getDataSource();
      SqlUtils.executeUpdate(dataSource, sqlTable.toString(), query.getFullName() + "__create-cache-table");
    }
    catch (SQLException e) {
      throw new WdkModelException("Unable to create cache table.", e);
    }
    // also insert the result into the cache
    insertToCache(tableName, instanceId);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.query.QueryInstance#getResultSize()
   */
  @Override
  public int getResultSize() throws WdkModelException, WdkUserException {
    if (!isCached()) {
      int count = 0;
      ResultList resultList = getResults();
      while (resultList.next()) {
        count++;
      }
      return count;
    }
    else
      return super.getResultSize();
  }

}
