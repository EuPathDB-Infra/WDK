package org.gusdb.wdk.model.dbms;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.cache.ItemCache;
import org.gusdb.fgputil.cache.UnfetchableItemException;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.db.platform.DBPlatform;
import org.gusdb.fgputil.db.pool.DatabaseInstance;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.query.Query;
import org.gusdb.wdk.model.query.QueryInstance;

/**
 * @author Jerric Gao
 * 
 */
public class ResultFactory {

  @SuppressWarnings("unused")
  private Logger logger = Logger.getLogger(ResultFactory.class);

  public static class InstanceInfo {
    int instanceId;
    String message;
    final long creationDate;
    public InstanceInfo(int instanceId, String message) {
      this.instanceId = instanceId;
      this.message = message;
      creationDate = new Date().getTime();
    }
  }

  private static boolean USE_INSTANCE_INFO_CACHE = true;
  private static ItemCache<String, InstanceInfo> INSTANCE_INFO_CACHE = new ItemCache<String, InstanceInfo>();
  private final InstanceInfoFetcher instanceInfoFetcher;

  private DatabaseInstance database;
  private DBPlatform platform;
  private CacheFactory cacheFactory;
  private WdkModel wdkModel;

  public ResultFactory(WdkModel wdkModel) {
    this.database = wdkModel.getAppDb();
    this.platform = database.getPlatform();
    this.cacheFactory = new CacheFactory(wdkModel, database);
    this.wdkModel = wdkModel;
    this.instanceInfoFetcher = new InstanceInfoFetcher(this);
  }

  public CacheFactory getCacheFactory() {
    return cacheFactory;
  }

  public String getCachedSql(QueryInstance<?> queryInstance)
      throws WdkModelException, WdkUserException {
    // get query info
    Query query = queryInstance.getQuery();
    QueryInfo queryInfo = cacheFactory.getQueryInfo(query);

    int instanceId;
    try {
      if (queryInfo.isExist()) { // cache table exists
        instanceId = getInstanceId(queryInfo, queryInstance);
        //logger.debug("   ..... EXISTING table,  instanceid?: " + instanceId + "(if 0 we need a new one)");
        if (instanceId == 0) { // instance doesn't exist
          //logger.debug("creating cache instance and cache...");
          // create cache
          instanceId = createCache(queryInfo, queryInstance);
          //logger.debug("   ..... EXISTING table, NEW instanceid: " + instanceId);
          createCacheInstance(queryInfo, queryInstance, instanceId);
        }
      } else { // cache table doesn't exist
        //logger.debug("creating cache query, instance and cache...");
        // get a new instance id, and created cache
        instanceId = createCache(queryInfo, queryInstance);
        //logger.debug("   ..... NEW table and instanceid: " + instanceId);
        // create query info
        cacheFactory.createQueryInfo(queryInfo);
        createCacheInstance(queryInfo, queryInstance, instanceId);
      }
    } catch (SQLException ex) {
      throw new WdkModelException(ex);
    }
		//logger.debug("   ..... FINAL instanceid: " + instanceId);
    queryInstance.setInstanceId(instanceId);

    // get the cached sql
    StringBuffer sql = new StringBuffer("SELECT * ");
    sql.append(" FROM ").append(queryInfo.getCacheTable());
    sql.append(" WHERE ").append(CacheFactory.COLUMN_INSTANCE_ID);
    sql.append(" = ").append(instanceId);
    
    // append sorting columns to the sql
    Map<String, Boolean> sortingMap = query.getSortingMap();
    boolean firstSortingColumn = true;
    for (String column : sortingMap.keySet()) {
      if (firstSortingColumn) {
        sql.append(" ORDER BY ");
        firstSortingColumn = false;
      } else {
        sql.append(", ");
      }
      String order = sortingMap.get(column) ? " ASC " : " DESC ";
      sql.append(column).append(order);
    }
    
    // append row id as the last sorting column
    sql.append((sortingMap.size() > 0) ? ", " : " ORDER BY ");
    sql.append(CacheFactory.COLUMN_ROW_ID + " ASC ");
    
    return sql.toString();
  }

  public ResultList getCachedResults(QueryInstance<?> queryInstance)
      throws WdkModelException, WdkUserException {
    String sql = getCachedSql(queryInstance);
    //logger.debug("********* SQL to get the IDs from datasetparam: " + sql);

    // get the resultList
    try {
      DataSource dataSource = database.getDataSource();
      ResultSet resultSet = SqlUtils.executeQuery(dataSource,
          sql.toString(), queryInstance.getQuery().getFullName()
              + "__select-cache");
      return new SqlResultList(resultSet);
    }
    catch (SQLException e) {
      throw new WdkModelException("Unable to retrieve cached results.", e);
    }
  }

  private int getInstanceId(QueryInfo queryInfo, QueryInstance<?> instance)
      throws WdkModelException, WdkUserException {
    try {
      // get the query instance id; null if not exist
      String checksum = instance.getChecksum();
      int queryId = queryInfo.getQueryId();
      InstanceInfo instanceInfo = USE_INSTANCE_INFO_CACHE ?
          INSTANCE_INFO_CACHE.getItem(InstanceInfoFetcher.getKey(checksum, queryId), instanceInfoFetcher) :
          getInstanceInfo(checksum, queryId);
      if (instanceInfo.message != null) {
        instance.setResultMessage(instanceInfo.message);
      }
      return instanceInfo.instanceId;
    }
    catch (UnfetchableItemException e) {
      throw (WdkModelException)e.getCause();
    }
  }

  public InstanceInfo getInstanceInfo(String checksum, int queryId) throws WdkModelException {

    StringBuilder sql = new StringBuilder("SELECT ");
    sql.append(CacheFactory.COLUMN_INSTANCE_ID).append(", ");
    sql.append(CacheFactory.COLUMN_RESULT_MESSAGE);
    sql.append(" FROM ").append(CacheFactory.TABLE_INSTANCE);
    sql.append(" WHERE ").append(CacheFactory.COLUMN_QUERY_ID);
    sql.append(" = ").append(queryId);
    sql.append(" AND " + CacheFactory.COLUMN_INSTANCE_CHECKSUM);
    sql.append(" = '").append(checksum).append("'");
    sql.append(" ORDER BY " + CacheFactory.COLUMN_INSTANCE_ID);

    DataSource dataSource = database.getDataSource();
    ResultSet resultSet = null;
    //logger.debug("testing if we should reuse a wdk_instance_id in cache table: " +  sql.toString());
    try {
      resultSet = SqlUtils.executeQuery(dataSource, sql.toString(),
          "wdk-check-instance-exist");

      InstanceInfo instanceInfo = new InstanceInfo(0, null);
      if (resultSet.next()) {
        instanceInfo.instanceId = resultSet.getInt(CacheFactory.COLUMN_INSTANCE_ID);
        instanceInfo.message = platform.getClobData(resultSet, CacheFactory.COLUMN_RESULT_MESSAGE);
      }
      return instanceInfo;
    }
    catch (SQLException e) {
      throw new WdkModelException("Unable to check instance ID.", e);
    }
    finally {
      SqlUtils.closeResultSetAndStatement(resultSet, null);
    }
  }

  private int createCache(QueryInfo queryInfo, QueryInstance<?> instance)
      throws WdkModelException, SQLException, WdkUserException {
    DataSource dataSource = database.getDataSource();
    int instanceId = platform.getNextId(dataSource, null, CacheFactory.TABLE_INSTANCE);

    // check whether need to create the cache;
    String cacheTable = queryInfo.getCacheTable();
    String schema = database.getDefaultSchema();
    if (!platform.checkTableExists(dataSource, schema, cacheTable)) {
      // create the cache using the result of the first query
      instance.createCache(cacheTable, instanceId);
      // disable the stats on the new cache table
      platform.disableStatistics(dataSource, schema, cacheTable);
      createCacheTableIndex(queryInfo.getCacheTable(), instance.getQuery());
    }
    else {// insert result into existing cache table
      instance.insertToCache(cacheTable, instanceId);
    }

    return instanceId;
  }

  private void createCacheTableIndex(String cacheTable, Query query)
      throws WdkModelException {
    String[] indexColumns = query.getIndexColumns();

    // resize the index columns first
    resizeIndexColumns(cacheTable, indexColumns);

    // create index on query instance id
    StringBuffer sqlId = new StringBuffer("CREATE INDEX ");
    sqlId.append(cacheTable).append("_idx01 ON ").append(cacheTable);
    sqlId.append(" (").append(CacheFactory.COLUMN_INSTANCE_ID);

    // create index on other columns
    if (indexColumns != null) {
      for (String column : indexColumns) {
          sqlId.append(", ").append(column);
      }
    }
    sqlId.append(")");

    try {
      DataSource dataSource = database.getDataSource();
      SqlUtils.executeUpdate(dataSource, sqlId.toString(),
        query.getFullName() + "__create-cache-index01");
  }
    catch (SQLException e) {
      throw new WdkModelException("Could not create cache table index.", e);
    }
  }

  /**
   * Resize the index columns to the max size defined in the model config file.
   * Please note, only varchar columns are resized.
   * 
   * @param connection
   * @param cacheTable
   * @param indexColumns
   */
  private void resizeIndexColumns(String cacheTable, String[] indexColumns)
      throws WdkModelException {
    if (indexColumns == null || indexColumns.length == 0)
      return;

    // get the type of the columns
    DataSource dataSource = database.getDataSource();
    Map<String, Integer> columnSizes = new LinkedHashMap<>();
    ResultSet resultSet = null;
    try {
      resultSet = SqlUtils.executeQuery(dataSource, "SELECT * FROM "
          + cacheTable, cacheTable + "__ge-cache-metadata");
      ResultSetMetaData metaData = resultSet.getMetaData();
      for (int i = 1; i <= metaData.getColumnCount(); i++) {
        String column = metaData.getColumnName(i).toLowerCase();
        String type = metaData.getColumnTypeName(i).toLowerCase();
        if (type.contains("char"))
          columnSizes.put(column, metaData.getColumnDisplaySize(i));
      }
      metaData = null;
    } catch (SQLException ex) {
      throw new WdkModelException(ex);
    } finally {
      SqlUtils.closeResultSetAndStatement(resultSet, null);
    }

    try {
      // resize columns
      int maxSize = wdkModel.getModelConfig().getAppDB().getMaxPkColumnWidth();
      for (String column : indexColumns) {
        column = column.toLowerCase();
        if (!columnSizes.containsKey(column))
          continue;
        // throw new WdkModelException("The required index column '" + column
        // + "' doesn't exist in the cache table: " + cacheTable
        // + ". Please look up the query in 'Query' table that generates " +
        // "this cache table, and make sure it returns the index column.");

        String sql = platform.getResizeColumnSql(cacheTable, column, maxSize);
        SqlUtils.executeUpdate(dataSource, sql, cacheTable
            + "__change-column-size");
      }
    } catch (SQLException ex) {
      throw new WdkModelException("Failed to alter the sizes of index columns"
          + " '" + Utilities.fromArray(indexColumns) + "' on cache table "
          + cacheTable + ". Please look up the query in 'Query' table that "
          + "creates this cache, and make sure the maxPkColumnWidth property "
          + "defined in the model-config.xml is the same or bigger than the "
          + "size of primary key columns from that query.", ex);
    }
  }

  private void createCacheInstance(QueryInfo queryInfo, QueryInstance<?> instance,
      int instanceId) throws WdkModelException, WdkUserException {
    StringBuffer sql = new StringBuffer("INSERT INTO ");
    sql.append(CacheFactory.TABLE_INSTANCE).append(" (");
    sql.append(CacheFactory.COLUMN_INSTANCE_ID).append(", ");
    sql.append(CacheFactory.COLUMN_QUERY_ID).append(", ");
    sql.append(CacheFactory.COLUMN_INSTANCE_CHECKSUM).append(", ");
    sql.append(CacheFactory.COLUMN_RESULT_MESSAGE);
    sql.append(") VALUES (?, ?, ?, ?)");

    PreparedStatement ps = null;
    try {
      DataSource dataSource = database.getDataSource();
      ps = SqlUtils.getPreparedStatement(dataSource, sql.toString());
      ps.setInt(1, instanceId);
      ps.setInt(2, queryInfo.getQueryId());
      ps.setString(3, instance.getChecksum());
      platform.setClobData(ps, 4, instance.getResultMessage(), false);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new WdkModelException("Unable to add cache instance.", e);
    } finally {
      // close the statement manually, since we cannot close the
      // connection; it's not committed yet.
      SqlUtils.closeStatement(ps);
    }
  }
}
