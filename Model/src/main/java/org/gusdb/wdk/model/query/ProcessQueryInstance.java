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
import org.gusdb.fgputil.collection.ReadOnlyMap;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.db.platform.DBPlatform;
import org.gusdb.wdk.model.ServiceResolver;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.dbms.CacheFactory;
import org.gusdb.wdk.model.dbms.ResultList;
import org.gusdb.wdk.model.user.User;
import org.gusdb.wsf.client.ClientModelException;
import org.gusdb.wsf.client.ClientRequest;
import org.gusdb.wsf.client.ClientUserException;
import org.gusdb.wsf.client.WsfClient;
import org.gusdb.wsf.client.WsfClientFactory;
import org.gusdb.wsf.client.WsfResponseListener;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This process query instance calls the web service, retrieves the result, and cache them into the cache
 * table. If the result generated by the service is too big, the service will send it in multiple packets, and
 * the process query instance will retrieve all the packets in order.
 * 
 * @author Jerric Gao
 */
public class ProcessQueryInstance extends QueryInstance<ProcessQuery> {

  private static final Logger LOG = Logger.getLogger(ProcessQueryInstance.class);

  private static WsfClientFactory WSF_CLIENT_FACTORY = ServiceResolver.resolve(WsfClientFactory.class);

  private int _signal;

  /**
   * @param user user to execute query as
   * @param query query to create instance for
   * @param paramValues stable values of all params in the query's context
   * @param assignedWeight weight of the query
   * @throws WdkModelException
   */
  ProcessQueryInstance(User user, ProcessQuery query, ReadOnlyMap<String, String> paramValues,
      int assignedWeight) throws WdkModelException {
    super(user, query, paramValues, assignedWeight);
  }

  @Override
  protected void appendJSONContent(JSONObject jsInstance) throws JSONException {
    jsInstance.put("signal", _signal);
  }

  @Override
  public void insertToCache(String tableName, long instanceId) throws WdkModelException {
    LOG.debug("inserting process query result to cache...");
    List<Column> columns = Arrays.asList(_query.getColumns());
    Set<String> columnNames = _query.getColumnMap().keySet();

    // prepare the sql
    String sql = buildCacheInsertSql(tableName, instanceId, columns, columnNames);

    // get results and time process
    long startTime = System.currentTimeMillis();
    DataSource dataSource = _wdkModel.getAppDb().getDataSource();
    PreparedStatement psInsert = null;
    try {
      // make a prepared statement, and invoke the wsf;
      psInsert = SqlUtils.getPreparedStatement(dataSource, sql);
      int batchSize = _query.getCacheInsertBatchSize();
      ProcessQueryResponseListener listener = new ProcessQueryResponseListener(columns, psInsert, batchSize);
      invokeWsf(listener);
      _resultMessage = listener.getMessage();
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
    LOG.debug("Process query cache insertion finished, and took " +
        ((System.currentTimeMillis() - startTime) / 1000D) + " seconds");
    
    executePostCacheUpdateSql(tableName, instanceId);
  }

  private void invokeWsf(WsfResponseListener listener) throws WdkModelException {
    long start = System.currentTimeMillis();

    // prepare request.
    ClientRequest request = new ClientRequest();
    request.setPluginClass(_query.getProcessName());
    request.setProjectId(_wdkModel.getProjectId());

    // prepare parameters
    Map<String, String> paramValues = getParamInternalValues();
    HashMap<String, String> params = new HashMap<String, String>();
    for (String name : paramValues.keySet()) {
      params.put(name, paramValues.get(name));
    }
    request.setParams(params);

    // prepare columns
    Map<String, Column> columns = _query.getColumnMap();
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
    request.setContext(_context);

    // create client
    WsfClient client;
    try {
      if (_query.isLocal()) {
        LOG.debug("Using local service...");
        client = WSF_CLIENT_FACTORY.newClient(listener);
      }
      else {
        LOG.debug("Using remote service at " + _query.getWebServiceUrl() + "...");
        client = WSF_CLIENT_FACTORY.newClient(listener, new URI(_query.getWebServiceUrl()));
      }

      // invoke the WSF, and set signal.
      LOG.debug("Invoking " + request.getPluginClass() + "...");
      _signal = client.invoke(request);
    }
    catch (ClientUserException ex) {
      // FIXME: We need to figure out what kind of exception to throw here- does parameter validation for
      //        WSF plugins (i.e. process queries) happen in WDK or on the plugin side?  For now, assume
      //        validation happens correctly in WDK and throw model exception here
      throw new WdkModelException(ex);
    }
    catch (ClientModelException | URISyntaxException ex) {
      throw new WdkModelException(ex);
    }
    long end = System.currentTimeMillis();
    LOG.debug("Client took " + ((end - start) / 1000.0) + " seconds.");
  }

  private String buildCacheInsertSql(String tableName, long instanceId, List<Column> columns,
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
    if (_query.isHasWeight() && !columnNames.contains(weightColumn))
      sql.append(", ").append(weightColumn);

    sql.append(") VALUES (");
    sql.append(instanceId).append(", ?");
    for (int i = 0; i < columns.size(); i++) {
      sql.append(", ?");
    }

    // insert weight to the last column, if doesn't exist
    if (_query.isHasWeight() && !columnNames.contains(weightColumn))
      sql.append(", ").append(_assignedWeight);

    return sql.append(")").toString();
  }

  @Override
  protected ResultList getUncachedResults() throws WdkModelException {
    throw new UnsupportedOperationException("Process queries are always cacheable, so ProcessQueryInstance.getUncachedResults() should never be called");
  }

  @Override
  public String getSql() throws WdkModelException {
    // always get sql that queries on the cached result
    return getCachedSql(true);
  }

  @Override
  public String getSqlUnsorted() throws WdkModelException, WdkUserException {
    // always get sql that queries on the cached result
    return getCachedSql(false);
  }

  @Override
  public void createCache(String tableName, long instanceId) throws WdkModelException {
    LOG.debug("creating process query cache...");
    DBPlatform platform = _query.getWdkModel().getAppDb().getPlatform();
    Column[] columns = _query.getColumns();

    StringBuffer sqlTable = new StringBuffer("CREATE TABLE " + tableName + " (");

    // define the instance id column
    String numberType = platform.getNumberDataType(12);
    sqlTable.append(CacheFactory.COLUMN_INSTANCE_ID + " " + numberType + " NOT NULL, ");
    sqlTable.append(CacheFactory.COLUMN_ROW_ID + " " + numberType + " NOT NULL");
    if (_query.isHasWeight())
      sqlTable.append(", " + Utilities.COLUMN_WEIGHT + " " + numberType);
    
    // define the rest of the columns
    for (Column column : columns) {
      // weight column is already added to the sql.
      if (column.getName().equals(Utilities.COLUMN_WEIGHT) && _query.isHasWeight())
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
      DataSource dataSource = _wdkModel.getAppDb().getDataSource();
      SqlUtils.executeUpdate(dataSource, sqlTable.toString(), _query.getFullName() + "__create-cache-table");
    }
    catch (SQLException e) {
      throw new WdkModelException("Unable to create cache table.", e);
    }
    // also insert the result into the cache
    insertToCache(tableName, instanceId);
  }

  @Override
  public int getResultSize() throws WdkModelException {
    if (!getIsCacheable()) {
      int count = 0;
      try (ResultList resultList = getResults()) {
        while (resultList.next()) {
          count++;
        }
        return count;
      }
    }
    else {
      return super.getResultSize();
    }
  }

}
