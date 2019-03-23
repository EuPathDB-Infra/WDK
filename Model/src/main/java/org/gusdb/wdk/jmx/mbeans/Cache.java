package org.gusdb.wdk.jmx.mbeans;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.db.pool.DatabaseInstance;
import org.gusdb.wdk.jmx.BeanBase;
import org.gusdb.wdk.model.dbms.CacheFactory;

/**
 * MBean representing the WDK database cache.
 *
 * Performs SQL queries of the cache tables. Uses the WDK's
 * queryPlatform (the application database).
 *
 * @see org.gusdb.wdk.model.WdkModel#getAppDb()
 */
public class Cache extends BeanBase implements CacheMBean {

  private static final Logger logger = Logger.getLogger(Cache.class);
	
  DataSource dataSource;
  String platformName;
  
  public Cache() {
    super();
    DatabaseInstance platform = getWdkModel().getAppDb();
    platformName = platform.getPlatform().getClass().getSimpleName();
    dataSource = platform.getDataSource();
  }

  /**
   * Returns the count of QUERYRESULT tables in
   * the user schema. Specifically,
   *
   * <pre>
   * {@code
   * select                               
   * count(table_name) cache_count        
   * from user_tables                     
   * where table_name like 'QUERYRESULT%' 
   * }
   * </pre>
   */
  @Override
  public String getcache_table_count() {
    String sql = cacheTableCountSql();
    if (sql == null) return "unsupported database platform: " + platformName;
    return query(sql);
  }

  private String cacheTableCountSql() {
    StringBuffer sql = new StringBuffer();
    if (platformName.toLowerCase().startsWith("oracle")) {
      sql.append(" select                                ");
      sql.append(" count(table_name) cache_count         ");
      sql.append(" from user_tables                      ");
      sql.append(" where table_name like 'QUERYRESULT%'  ");
    } else if (platformName.toLowerCase().startsWith("postgre")) {
      sql.append(" select                                              ");
      sql.append(" count(table_name) cache_count                       ");
      sql.append(" from information_schema.tables                      ");
      sql.append(" where lower(table_name) like lower('QUERYRESULT%')  "); 
    } else {
      return null;
    }

    return sql.toString();
  }

  /**
   * execute given SQL string
   *
   * @param SQL to execute
   * @return String result
   */
  private String query(String sql) {
    String value = null;
    ResultSet rs = null;
    PreparedStatement ps = null;
    try {
      ps = SqlUtils.getPreparedStatement(dataSource, sql);
      rs = ps.executeQuery();
     if (rs.next()) {
        ResultSetMetaData rsmd = rs.getMetaData();
        int numColumns = rsmd.getColumnCount();
        for (int i=1; i<numColumns+1; i++) {
          String columnName = rsmd.getColumnName(i).toLowerCase();
          value = rs.getString(columnName);
        }
      }
    } catch (SQLException sqle) {
      logger.warn("Failed attempting " + sql + ". This MBean data will not be registered.\n");
      logger.debug(sqle);
    } finally {
        SqlUtils.closeResultSetAndStatement(rs, ps);
    }
    return value;
  }

  /**
   * MBean operation to reset WDK database cache through
   * call to CacheFactory resetCache().
   *
   * @see org.gusdb.wdk.model.dbms.CacheFactory#resetCache
   */
  @Override
  public void resetWdkCache() {
    try {
      CacheFactory factory = new CacheFactory(getWdkModel());
      factory.resetCache(true, true);
    } catch (Exception e) {
        // TODO: something
    }
  }

  /**
   * 
   * @return whether global caching is turned on or not.
   *
   * @see org.gusdb.wdk.model.config.ModelConfig#isCaching
   */
  @Override
  public boolean getWdkIsCaching() {
    return getWdkModel().getModelConfig().isCaching();
  }

  /**
   * MBean operation to toggle global WDK database caching true/false.
   * Disabling the caching can be useful when benchmarking
   * database and system tunings.
   *
   * @see org.gusdb.wdk.model.config.ModelConfig#setCaching
   */
  @Override
  public void toggleWdkIsCaching() {
    //boolean isCaching = getWdkModel().getModelConfig().isCaching();
    //getWdkModel().getModelConfig().setCaching( ! isCaching );
    throw new UnsupportedOperationException("We're sorry, but toggling WDK caching setting is no longer supported.");
  }
}
