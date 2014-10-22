package org.gusdb.wdk.model.config;

import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.db.platform.SupportedPlatform;
import org.gusdb.fgputil.db.platform.UnsupportedPlatformException;
import org.gusdb.fgputil.db.pool.ConnectionPoolConfig;

/**
 * It defines the properties that are common in both {@code <appDB>} and
 * {@code <userDB>}, such as DB connection pool settings, login information, and
 * DB connection debugging parameters.
 * 
 * @author xingao
 * 
 */
public abstract class ModelConfigDB implements ConnectionPoolConfig {

  protected static final boolean DEFAULT_READ_ONLY = false;
  protected static final boolean DEFAULT_AUTO_COMMIT = true;

  protected static final String CONFIG_TABLE = "config";
  protected static final String CONFIG_NAME_COLUMN = "config_name";
  protected static final String CONFIG_VALUE_COLUMN = "config_value";
  
  // required properties
  private String login;
  private String password;
  private String connectionUrl;
  private String platform;
  private String driverInitClass;

  // optional properties
  private short maxActive = 20;
  private short maxIdle = 1;
  private short minIdle = 0;
  private long maxWait = 50;
  private boolean defaultReadOnly = DEFAULT_READ_ONLY;
  private boolean defaultAutoCommit = DEFAULT_AUTO_COMMIT;

  /**
   * display DB connection count periodically in the log. This is used to
   * identify connection leaks. default false.
   */
  private boolean showConnections = false;
  /**
   * display interval, in seconds.
   */
  private long showConnectionsInterval = 10;
  /**
   * how long it will continue to display the connection count, in seconds.
   * after that, it will stop display any more counts.
   */
  private long showConnectionsDuration = 600;

  /**
   * @return the login
   */
  @Override
  public String getLogin() {
    return login;
  }

  /**
   * @param login
   *          the login to set
   */
  public void setLogin(String login) {
    this.login = login;
  }

  /**
   * @return the password
   */
  @Override
  public String getPassword() {
    return password;
  }

  /**
   * @param password
   *          the password to set
   */
  public void setPassword(String password) {
    this.password = password;
  }

  /**
   * @return the connectionUrl
   */
  @Override
  public String getConnectionUrl() {
    return connectionUrl;
  }

  /**
   * @param connectionUrl
   *          the connectionUrl to set
   */
  public void setConnectionUrl(String connectionUrl) {
    this.connectionUrl = connectionUrl;
  }

  /**
   * @param platform
   *          the platform to set
   * @throws UnsupportedPlatformException if platform is not supported
   */
  public void setPlatform(String platform) {
    this.platform = platform;
  }
  
  /**
   * @return DB platform string for this configuration
   */
  public String getPlatform() {
	return platform;
  }
  @Override
  public SupportedPlatform getPlatformEnum() {
	return (platform == null ? null :
      SupportedPlatform.toPlatform(platform.toUpperCase()));
  }
  
  /**
   * @return the maxActive
   */
  @Override
  public short getMaxActive() {
    return maxActive;
  }

  /**
   * @param maxActive
   *          the maxActive to set
   */
  public void setMaxActive(short maxActive) {
    this.maxActive = maxActive;
  }

  /**
   * @return the maxIdle
   */
  @Override
  public short getMaxIdle() {
    return maxIdle;
  }

  /**
   * @param maxIdle
   *          the maxIdle to set
   */
  public void setMaxIdle(short maxIdle) {
    this.maxIdle = maxIdle;
  }

  /**
   * @return the minIdle
   */
  @Override
  public short getMinIdle() {
    return minIdle;
  }

  /**
   * @param minIdle
   *          the minIdle to set
   */
  public void setMinIdle(short minIdle) {
    this.minIdle = minIdle;
  }

  /**
   * @return the maxWait
   */
  @Override
  public long getMaxWait() {
    return maxWait;
  }

  /**
   * @param maxWait
   *          the maxWait to set
   */
  public void setMaxWait(long maxWait) {
    this.maxWait = maxWait;
  }

  @Override
  public boolean getDefaultAutoCommit() {
    return defaultAutoCommit;
  }

  public void setDefaultAutoCommit(boolean defaultAutoCommit) {
    this.defaultAutoCommit = defaultAutoCommit;
  }

  @Override
  public boolean getDefaultReadOnly() {
    return defaultReadOnly;
  }

  public void setDefaultReadOnly(boolean defaultReadOnly) {
    this.defaultReadOnly = defaultReadOnly;
  }

  /**
   * @param driverInitClass implementation class to initialize DB driver
   */
  public void setDriverInitClass(String driverInitClass) {
	this.driverInitClass = driverInitClass;
  }
  
  /**
   * @return implementation class to initialize DB driver
   */
  @Override
  public String getDriverInitClass() {
	  return driverInitClass;
  }

  /**
   * @return the showConnections
   */
  @Override
  public boolean isShowConnections() {
    return showConnections;
  }

  /**
   * @param showConnections
   *          the showConnections to set
   */
  public void setShowConnections(boolean showConnections) {
    this.showConnections = showConnections;
  }

  /**
   * @return the showConnectionsInterval
   */
  @Override
  public long getShowConnectionsInterval() {
    return showConnectionsInterval;
  }

  /**
   * @param showConnectionsInterval
   *          the showConnectionsInterval to set
   */
  public void setShowConnectionsInterval(long showConnectionsInterval) {
    this.showConnectionsInterval = showConnectionsInterval;
  }

  /**
   * @return the showConnectionsDuration
   */
  @Override
  public long getShowConnectionsDuration() {
    return showConnectionsDuration;
  }

  /**
   * @param showConnectionsDuration
   *          the showConnectionsDuration to set
   */
  public void setShowConnectionsDuration(long showConnectionsDuration) {
    this.showConnectionsDuration = showConnectionsDuration;
  }
  
  @Override
  public String toString() {
    String defaultSchema = SupportedPlatform.toPlatform(getPlatform())
        .getPlatformInstance().getDefaultSchema(getLogin());
    return new StringBuilder("ModelConfigDB {").append(FormatUtil.NL)
        .append("  platform:      ").append(getPlatform()).append(FormatUtil.NL)
        .append("  connectionUrl: ").append(getConnectionUrl()).append(FormatUtil.NL)
        .append("  login:         ").append(getLogin()).append(FormatUtil.NL)
        .append("  defaultSchema: ").append(defaultSchema).append(FormatUtil.NL)
        .append("}").append(FormatUtil.NL)
        .toString();
  }

  public boolean isSameConnectionInfoAs(ModelConfigDB config) {
    return (login.equals(config.login) &&
        password.equals(config.password) &&
        connectionUrl.equals(config.connectionUrl) &&
        platform.equalsIgnoreCase(config.platform));
  }
}
