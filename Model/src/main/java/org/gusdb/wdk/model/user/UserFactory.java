package org.gusdb.wdk.model.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.FormatUtil.Style;
import org.gusdb.fgputil.db.QueryLogger;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.db.pool.DatabaseInstance;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.config.ModelConfig;
import org.gusdb.wdk.model.config.ModelConfigUserDB;

/**
 * @author xingao
 */
public class UserFactory {

  private static final String GUEST_USER_PREFIX = "WDK_GUEST_";
  private static final String SYSTEM_USER_PREFIX = GUEST_USER_PREFIX + "SYSTEM_";

  private static final String GLOBAL_PREFERENCE_KEY = "[Global]";

  private static Logger logger = Logger.getLogger(UserFactory.class);

  // -------------------------------------------------------------------------
  // data base table and column definitions
  // -------------------------------------------------------------------------
  static final String TABLE_USER = "users";

  static final String COLUMN_SIGNATURE = "signature";

  private final String COLUMN_EMAIL = "email";

  // -------------------------------------------------------------------------
  // the macros used by the registration email
  // -------------------------------------------------------------------------
  private static final String EMAIL_MACRO_USER_NAME = "USER_NAME";
  private static final String EMAIL_MACRO_EMAIL = "EMAIL";
  private static final String EMAIL_MACRO_PASSWORD = "PASSWORD";

  public static String encrypt(String str) {
    // convert each byte into hex format
    StringBuffer buffer = new StringBuffer();
    for (byte code : Utilities.getEncryptedBytes(str)) {
      buffer.append(Integer.toHexString(code & 0xFF));
    }
    return buffer.toString();
  }

  /**
   * md5 checksum algorithm. encrypt(String) drops leading zeros of hex codes so
   * is not compatible with md5
   **/
  public static String md5(String str) {
    StringBuffer buffer = new StringBuffer();
    for (byte code : Utilities.getEncryptedBytes(str)) {
      buffer.append(Integer.toString((code & 0xff) + 0x100, 16).substring(1));
    }
    return buffer.toString();
  }

  // -------------------------------------------------------------------------
  // member variables
  // -------------------------------------------------------------------------
  private DatabaseInstance userDb;
  private DataSource dataSource;

  private String userSchema;
  private String defaultRole;

  private String projectId;

  // WdkModel is used by the legacy code, may consider to be removed
  private WdkModel wdkModel;

  public UserFactory(WdkModel wdkModel) {
    this.wdkModel = wdkModel;
    this.userDb = wdkModel.getUserDb();
    this.dataSource = userDb.getDataSource();
    this.projectId = wdkModel.getProjectId();

    ModelConfig modelConfig = wdkModel.getModelConfig();
    ModelConfigUserDB userDB = modelConfig.getUserDB();
    this.userSchema = userDB.getUserSchema();
    this.defaultRole = modelConfig.getDefaultRole();
  }

  /**
   * @return Returns the userRole.
   */
  public String getDefaultRole() {
    return defaultRole;
  }

  public String getProjectId() {
    return projectId;
  }

  public User createUser(String email, String lastName, String firstName,
      String middleName, String title, String organization, String department,
      String address, String city, String state, String zipCode,
      String phoneNumber, String country,
      Map<String, String> globalPreferences,
      Map<String, String> projectPreferences) throws WdkUserException,
      WdkModelException {
	  boolean resetPwdAndSendEmail = true;
	  String dontEmailProp = getWdkModel().getProperties().get("DONT_EMAIL_NEW_USER");
	  if (dontEmailProp != null && dontEmailProp.equals("true")) resetPwdAndSendEmail = false;
    return createUser(email, lastName, firstName, middleName, title,
        organization, department, address, city, state, zipCode, phoneNumber,
        country, globalPreferences, projectPreferences, resetPwdAndSendEmail);
  }

  public User createUser(String email, String lastName, String firstName,
      String middleName, String title, String organization, String department,
      String address, String city, String state, String zipCode,
      String phoneNumber, String country,
      Map<String, String> globalPreferences,
      Map<String, String> projectPreferences, boolean resetPwd)
      throws WdkUserException, WdkModelException {
    if (email == null)
      throw new WdkUserException("The user's email cannot be empty.");
    // format the info
    email = email.trim();
    if (email.length() == 0)
      throw new WdkUserException("The user's email cannot be empty.");

    PreparedStatement psUser = null;
    try {
      // check whether the user exist in the database already exist.
      // if loginId exists, the operation failed
      if (isExist(email))
        throw new WdkUserException("The email '" + email
            + "' has already been registered. " + "Please choose another one.");

      // get a new userId
      int userId = userDb.getPlatform().getNextId(dataSource, userSchema, "users");
      String signature = encrypt(userId + "_" + email);
      Date registerTime = new Date();

      String sql = "INSERT INTO " + userSchema + TABLE_USER + " ("
          + Utilities.COLUMN_USER_ID + ", " + COLUMN_EMAIL
          + ", passwd, is_guest, " + "register_time, last_name, first_name, "
          + "middle_name, title, organization, department, address, "
          + "city, state, zip_code, phone_number, country,signature)"
          + " VALUES (?, ?, ' ', ?, ?, ?, ?, ?, ?, ?, ?,"
          + "?, ?, ?, ?, ?, ?, ?)";
      long start = System.currentTimeMillis();
      psUser = SqlUtils.getPreparedStatement(dataSource, sql);
      psUser.setInt(1, userId);
      psUser.setString(2, email);
      psUser.setBoolean(3, false);
      psUser.setTimestamp(4, new Timestamp(registerTime.getTime()));
      psUser.setString(5, lastName);
      psUser.setString(6, firstName);
      psUser.setString(7, middleName);
      psUser.setString(8, title);
      psUser.setString(9, organization);
      psUser.setString(10, department);
      psUser.setString(11, address);
      psUser.setString(12, city);
      psUser.setString(13, state);
      psUser.setString(14, zipCode);
      psUser.setString(15, phoneNumber);
      psUser.setString(16, country);
      psUser.setString(17, signature);
      psUser.executeUpdate();
      QueryLogger.logEndStatementExecution(sql, "wdk-user-register", start);

      // create user object
      User user = new User(wdkModel, userId, email, signature);
      user.setLastName(lastName);
      user.setFirstName(firstName);
      user.setMiddleName(middleName);
      user.setTitle(title);
      user.setOrganization(organization);
      user.setDepartment(department);
      user.setAddress(address);
      user.setCity(city);
      user.setState(state);
      user.setZipCode(zipCode);
      user.setPhoneNumber(phoneNumber);
      user.setCountry(country);
      user.addUserRole(defaultRole);
      user.setGuest(false);

      // save user's roles
      saveUserRoles(user);

      // save preferences
      if (globalPreferences == null)
        globalPreferences = new LinkedHashMap<String, String>();
      for (String param : globalPreferences.keySet()) {
        user.setGlobalPreference(param, globalPreferences.get(param));
      }
      if (projectPreferences == null)
        projectPreferences = new LinkedHashMap<String, String>();
      for (String param : projectPreferences.keySet()) {
        user.setProjectPreference(param, projectPreferences.get(param));
      }
      savePreferences(user);

      // generate a random password, and send to the user via email
      if (resetPwd)
        resetPassword(user);

      return user;
    } catch (SQLException ex) {
      throw new WdkUserException(ex);
    } finally {
      SqlUtils.closeStatement(psUser);
    }
  }
  
  /**
   * create a lazy-creation system user that will only be created when needed.
   * @return
   * @throws WdkModelException
   */
  public User createSystemUser() throws WdkModelException {
    return createTemporaryUser(SYSTEM_USER_PREFIX);
  }

  /**
   * create a lazy-creation guest that will only be created when needed.
   * @return
   * @throws WdkModelException
   */
  public User createGuestUser() throws WdkModelException {
    return createTemporaryUser(GUEST_USER_PREFIX);
  }
  
  /**
   * create a lazy-creation temporary user that will only be created when needed.
   * @param guestEmailPrefix
   * @return
   */
  User createTemporaryUser(String guestEmailPrefix) {
    User guest = new User(wdkModel, 0, guestEmailPrefix, null);
    guest.setGuest(true);
    guest.setFirstName("WDK Guest");
    guest.setEmailPrefix(guestEmailPrefix);
    return guest;
  }

  /**
   * Save the temporary user into database, and initialize its fields.
   * 
   * @param user
   * @return
   * @throws WdkModelException
   */
  User saveTemporaryUser(User user) throws WdkModelException {
    // logger.error("TRACE", new Exception());
 
    PreparedStatement psUser = null;
    try {
      // get a new user id
      int userId = userDb.getPlatform().getNextId(dataSource, userSchema, "users");
      user.setUserId(userId);
      user.setEmail(user.getEmailPrefix() + userId);
      user.setSignature(encrypt(userId + "_" + user.getEmail()));
      
      Date registerTime = new Date();
      Date lastActiveTime = new Date();
      String sql = "INSERT INTO " + userSchema
          + "users (user_id, email, passwd, is_guest, "
          + "register_time, last_active, first_name, signature) "
          + "VALUES (?, ?, ' ', ?, ?, ?, ?, ?)";
      long start = System.currentTimeMillis();
      psUser = SqlUtils.getPreparedStatement(dataSource, sql);
      psUser.setInt(1, userId);
      psUser.setString(2, user.getEmail());
      psUser.setBoolean(3, true);
      psUser.setTimestamp(4, new Timestamp(registerTime.getTime()));
      psUser.setTimestamp(5, new Timestamp(lastActiveTime.getTime()));
      psUser.setString(6, user.getFirstName());
      psUser.setString(7, user.getSignature());
      psUser.executeUpdate();
      QueryLogger.logEndStatementExecution(sql, "wdk-user-create-guest", start);

      user.addUserRole(defaultRole);

      // save user's roles
      saveUserRoles(user);

      logger.info("Guest user " + user.getEmail() + " created.");

      return user;
    } catch (SQLException e) {
      throw new WdkModelException("Unable to create guest user.", e);
    } catch (WdkUserException e) {
      throw new WdkModelException("Unable to create guest user.", e);
    } finally {
      SqlUtils.closeStatement(psUser);
    }
  }

  private User getMergedUser(User guest, User loggedInUser)
      throws WdkModelException, WdkUserException {
    if (loggedInUser == null)
      return loggedInUser;

    // merge the history of the guest into the user
    loggedInUser.mergeUser(guest);

    // update user active timestamp
    updateUser(loggedInUser);

    return loggedInUser;
  }

  public User login(User guest, String email, String password)
      throws WdkUserException, WdkModelException {
    // make sure the guest is really a guest
    if (!guest.isGuest())
      throw new WdkUserException("User has been logged in.");

    // authenticate the user; if fails, a WdkUserException will be thrown out
    User user = authenticate(email, password);
    if (user == null) {
      throw new WdkUserException("Invalid email or password.");
    }
    return getMergedUser(guest, user);
  }

  public User login(User guest, int userId)
      throws WdkModelException, WdkUserException {
    return getMergedUser(guest, getUser(userId));
  }

  /**
   * Returns whether email and password are a correct credentials combination.
   * 
   * @param email user email
   * @param password user password
   * @return true email corresponds to user and password is correct, else false
   * @throws WdkModelException if error occurs while determining result
   */
  public boolean isCorrectPassword(String email, String password) throws WdkModelException {
    return authenticate(email, password) != null;
  }

  private User authenticate(String email, String password)
      throws WdkModelException {
    /*
     * String[] fields = new String[]{ "email", email.trim().toLowerCase(),
     * "passwd", encrypt(password) };
     */
    String[] fields = new String[] { "email", email.trim(), "passwd",
        encrypt(password) };
    return getUserByFields(fields, "wdk-user-login");

  }

  public User getUserByEmail(String email) throws WdkModelException {
    return getUserByFields(new String[] { "email", email.trim() },
        "wdk-user-get-id-by-email");
  }

  // FIXME: should probably be renamed getUserBySignature
  public User getUser(String signature) throws WdkModelException, WdkUserException {
    User user = getUserByFields(new String[] { "signature", signature },
        "wdk-user-get-id-by-signature");
    if (user == null) {
      // signature is rarely sent in by user; if User cannot be found, it's
      // probably an error
      throw new WdkUserException("Unable to find user with signature: "
          + signature);
    }
    return user;
  }

  /**
   * Returns a User based on the passed parameters, or null if none can be found
   * 
   * @param fieldMap
   *          a even-numbered array of key value pairs (column_name,
   *          required_value) from which to identify a user
   * @param queryName
   *          name of this query (for logging)
   * @return User that meets the criteria, or null if none exists
   * @throws WdkModelException if problem occurs accessing database
   */
  private User getUserByFields(String[] fieldMap, String queryName)
      throws WdkModelException {
    Connection conn = null;
    PreparedStatement ps = null;
    ResultSet rs = null;
    try {
      // get user information
      String sql = getUserIdQuery(fieldMap);
      long start = System.currentTimeMillis();
      conn = dataSource.getConnection();
      ps = conn.prepareStatement(sql);
      for (int i = 0; i < fieldMap.length; i += 2) {
        ps.setString((i / 2) + 1, fieldMap[i + 1]);
      }
      rs = ps.executeQuery();
      QueryLogger.logEndStatementExecution(sql.toString(), queryName, start);
      if (!rs.next()) {
        logger.warn("Unable to get user given the following params: "
            + toParamString(fieldMap));
        return null;
      }
      // read user info
      int userId = rs.getInt("user_id");
      return getUser(userId);
    } catch (SQLException e) {
      throw new WdkModelException(
          "Unable to get user given the following params: "
              + toParamString(fieldMap), e);
    } finally {
      SqlUtils.closeQuietly(rs, ps, conn);
    }
  }

  private String getUserIdQuery(String[] fieldMap) throws WdkModelException {
    StringBuilder sql = new StringBuilder().append("SELECT ").append(
        Utilities.COLUMN_USER_ID).append(" FROM ").append(userSchema).append(
        TABLE_USER);
    if (fieldMap.length % 2 == 1) {
      throw new WdkModelException(
          "Only even-sized array 'map' can be passed to this method.");
    }
    for (int i = 0; i < fieldMap.length; i += 2) {
      // convert email to lower cases
      if (fieldMap[i].equals(COLUMN_EMAIL)) {
        fieldMap[i] = "lower(" + fieldMap[i] + ")";
        if (fieldMap[i + 1] != null)
          fieldMap[i + 1] = fieldMap[i + 1].toLowerCase();
      }

      sql.append(i == 0 ? " WHERE " : " AND ").append(fieldMap[i]).append(
          fieldMap[i + 1] == null ? " IS NULL" : " = ?");
    }
    String sqlStr = sql.toString();
    logger.debug("Generated the following SQL: " + sqlStr);
    return sqlStr;
  }

  private String toParamString(String[] fieldMap) {
    StringBuilder str = new StringBuilder("Map [ ");
    for (int i = 0; i < fieldMap.length; i += 2) {
      str.append("{ \"").append(fieldMap[i]).append("\", ");
      str.append(fieldMap[i + 1] == null ? "null } " : "\"" + fieldMap[i + 1]
          + "\" } ");
    }
    return str.append("]").toString();
  }

  /**
   * Returns user by user ID. Since the ID will generally be produced
   * "internally", this function throws WdkModelException if user is not found.
   * 
   * @param userId
   *          user ID
   * @return user object
   * @throws WdkModelException if user cannot be found or error occurs
   */
  public User getUser(int userId) throws WdkModelException {
    PreparedStatement psUser = null;
    ResultSet rsUser = null;
    String sql = "SELECT email, signature, is_guest, last_name, "
        + "first_name, middle_name, title, organization, "
        + "department, address, city, state, zip_code, "
        + "phone_number, country FROM " + userSchema
        + "users WHERE user_id = ?";
    try {
      // get user information
      long start = System.currentTimeMillis();
      psUser = SqlUtils.getPreparedStatement(dataSource, sql);
      psUser.setInt(1, userId);
      rsUser = psUser.executeQuery();
      QueryLogger.logEndStatementExecution(sql, "wdk-user-get-user-by-id", start);
      if (!rsUser.next()) {
        throw new NoSuchUserException("Invalid user id: " + userId);
      }

      // read user info
      String email = rsUser.getString("email");
      String signature = rsUser.getString("signature");
      User user = new User(wdkModel, userId, email, signature);
      user.setGuest(rsUser.getBoolean("is_guest"));
      user.setLastName(rsUser.getString("last_name"));
      user.setFirstName(rsUser.getString("first_name"));
      user.setMiddleName(rsUser.getString("middle_name"));
      user.setTitle(rsUser.getString("title"));
      user.setOrganization(rsUser.getString("organization"));
      user.setDepartment(rsUser.getString("department"));
      user.setAddress(rsUser.getString("address"));
      user.setCity(rsUser.getString("city"));
      user.setState(rsUser.getString("state"));
      user.setZipCode(rsUser.getString("zip_code"));
      user.setPhoneNumber(rsUser.getString("phone_number"));
      user.setCountry(rsUser.getString("country"));

      // load the user's roles
      user.setUserRole(getUserRoles(user));

      // load user's preferences
      user.setPreferences(getPreferences(user));

      return user;
    }
    catch (SQLException | WdkUserException e) {
      throw new WdkModelException("Unable to get user with ID " + userId, e);
    }
    finally {
      SqlUtils.closeResultSetAndStatement(rsUser, psUser);
    }
  }

  public User[] queryUsers(String emailPattern) throws WdkUserException,
      WdkModelException {
    String sql = "SELECT user_id, email FROM " + userSchema + "users";

    if (emailPattern != null && emailPattern.length() > 0) {
      emailPattern = emailPattern.replace('*', '%');
      emailPattern = emailPattern.replaceAll("'", "");
      sql += " WHERE email LIKE '" + emailPattern + "'";
    }
    sql += " ORDER BY email";
    List<User> users = new ArrayList<User>();
    ResultSet rs = null;
    try {
      rs = SqlUtils.executeQuery(dataSource, sql,
          "wdk-user-query-users-by-email");
      while (rs.next()) {
        int userId = rs.getInt("user_id");
        User user = getUser(userId);
        users.add(user);
      }
    } catch (SQLException ex) {
      throw new WdkUserException(ex);
    } finally {
      SqlUtils.closeResultSetAndStatement(rs, null);
    }
    User[] array = new User[users.size()];
    users.toArray(array);
    return array;
  }

  public void checkConsistancy() throws WdkUserException {
    ResultSet rs = null;
    PreparedStatement psUser = null;
    try {
      // update user's register time
      int count = SqlUtils.executeUpdate(dataSource, "UPDATE "
          + userSchema + "users SET register_time = last_active "
          + "WHERE register_time is null", "wdk-user-update-register-time");
      System.out.println(count + " users with empty register_time have "
          + "been updated");

      // update history's is_delete field
      count = SqlUtils.executeUpdate(dataSource, "UPDATE "
          + userSchema + "histories SET is_deleted = 0 "
          + "WHERE is_deleted is null", "wdk-user-update-deleted");
      System.out.println(count + " histories with empty is_deleted have "
          + "been updated");

      // update user's signature
      String sql = "Update " + userSchema
          + "users SET signature = ? WHERE user_id = ?";
      psUser = SqlUtils.getPreparedStatement(dataSource, sql);
      rs = SqlUtils.executeQuery(dataSource, sql,
          "wdk-user-update-signature");
      while (rs.next()) {
        int userId = rs.getInt("user_id");

        String email = rs.getString("email");
        String signature = encrypt(userId + "_" + email);
        psUser.setString(1, signature);
        psUser.setInt(2, userId);
        psUser.executeUpdate();
        System.out.println("User [" + userId + "] " + email
            + "'s signature is updated");
      }
    } catch (SQLException ex) {
      throw new WdkUserException(ex);
    } finally {
      SqlUtils.closeResultSetAndStatement(rs, null);
      SqlUtils.closeStatement(psUser);
    }
  }

  private Set<String> getUserRoles(User user) throws WdkUserException, WdkModelException {
    Set<String> roles = new LinkedHashSet<String>();
    PreparedStatement psRole = null;
    ResultSet rsRole = null;
    String sql = "SELECT user_role from " + userSchema + "user_roles "
        + "WHERE user_id = ?";
    try {
      // load the user's roles
      long start = System.currentTimeMillis();
      psRole = SqlUtils.getPreparedStatement(dataSource, sql);
      psRole.setInt(1, user.getUserId());
      rsRole = psRole.executeQuery();
      QueryLogger.logStartResultsProcessing(sql, "wdk-user-get-roles", start, rsRole);
      while (rsRole.next()) {
        roles.add(rsRole.getString("user_role"));
      }
    } catch (SQLException ex) {
      throw new WdkUserException(ex);
    } finally {
      SqlUtils.closeResultSetAndStatement(rsRole, psRole);
    }
    return roles;
  }

  private void saveUserRoles(User user) throws WdkUserException, WdkModelException {
    // get a list of original roles, and find the roles to be deleted and
    // added
    Set<String> oldRoles = getUserRoles(user);
    List<String> toDelete = new ArrayList<String>();
    List<String> toInsert = new ArrayList<String>();
    for (String role : user.getUserRoles()) {
      if (!oldRoles.contains(role))
        toInsert.add(role);
    }
    for (String role : oldRoles) {
      if (user.containsUserRole(role))
        toDelete.add(role);
    }

    int userId = user.getUserId();
    PreparedStatement psDelete = null, psInsert = null;
    try {
      String sqlDelete = "DELETE FROM " + userSchema + "user_roles "
          + " WHERE user_id = ? AND user_role = ?";
      psDelete = SqlUtils.getPreparedStatement(dataSource, sqlDelete);
      String sqlInsert = "INSERT INTO " + userSchema
          + "user_roles (user_id, user_role)" + " VALUES (?, ?)";
      psInsert = SqlUtils.getPreparedStatement(dataSource, sqlInsert);

      // delete roles
      long start = System.currentTimeMillis();
      for (String role : toDelete) {
        psDelete.setInt(1, userId);
        psDelete.setString(2, role);
        psDelete.addBatch();
      }
      psDelete.executeBatch();
      QueryLogger.logEndStatementExecution(sqlDelete, "wdk-user-delete-roles", start);

      // insert roles
      start = System.currentTimeMillis();
      for (String role : toInsert) {
        psInsert.setInt(1, userId);
        psInsert.setString(2, role);
        psInsert.addBatch();
      }
      psInsert.executeBatch();
      QueryLogger.logEndStatementExecution(sqlInsert, "wdk-user-insert-roles", start);
    } catch (SQLException ex) {
      throw new WdkUserException(ex);
    } finally {
      SqlUtils.closeStatement(psDelete);
      SqlUtils.closeStatement(psInsert);
    }
  }

  /**
   * Save the basic information of a user
   * 
   * @param user
   */
  void saveUser(User user) throws WdkModelException {
    // Two integrity checks:
    // 1. Check if user exists in the database. if not, fail and ask to create the user first
    try {
      getUser(user.getUserId());
    }
    catch (WdkModelException e) {
      throw new WdkModelException("Cannot update user; no user exists with ID " + user.getUserId(), e);
    }
    // 2. Check if another user exists with this email (PK will protect us but want better message)
    User emailUser = getUserByEmail(user.getEmail());
    if (emailUser != null && emailUser.getUserId() != user.getUserId()) {
      throw new WdkModelException("This email is already in use by another account.  Please choose another.");
    }
    
    PreparedStatement psUser = null;
    String sqlUser = "UPDATE " + userSchema + "users SET is_guest = ?, "
        + "last_active = ?, last_name = ?, first_name = ?, "
        + "middle_name = ?, organization = ?, department = ?, "
        + "title = ?,  address = ?, city = ?, state = ?, "
        + "zip_code = ?, phone_number = ?, country = ?, " + "email = ? "
        + "WHERE user_id = ?";
    try {
      Date lastActiveTime = new Date();

      // save the user's basic information
      long start = System.currentTimeMillis();
      psUser = SqlUtils.getPreparedStatement(dataSource, sqlUser);
      psUser.setBoolean(1, user.isGuest());
      psUser.setTimestamp(2, new Timestamp(lastActiveTime.getTime()));
      psUser.setString(3, user.getLastName());
      psUser.setString(4, user.getFirstName());
      psUser.setString(5, user.getMiddleName());
      psUser.setString(6, user.getOrganization());
      psUser.setString(7, user.getDepartment());
      psUser.setString(8, user.getTitle());
      psUser.setString(9, user.getAddress());
      psUser.setString(10, user.getCity());
      psUser.setString(11, user.getState());
      psUser.setString(12, user.getZipCode());
      psUser.setString(13, user.getPhoneNumber());
      psUser.setString(14, user.getCountry());
      psUser.setString(15, user.getEmail());
      psUser.setInt(16, user.getUserId());
      psUser.executeUpdate();
      QueryLogger.logEndStatementExecution(sqlUser, "wdk-user-update-user", start);

      // save user's roles
      // saveUserRoles(user);

      // save preference
      savePreferences(user);
    } catch (SQLException ex) {
      throw new WdkModelException(ex);
    } finally {
      SqlUtils.closeStatement(psUser);
    }
  }

  /**
   * update the time stamp of the activity
   * 
   * @param user
   * @throws WdkModelException 
   */
  private void updateUser(User user) throws WdkUserException, WdkModelException {
    PreparedStatement psUser = null;
    String sql = "UPDATE " + userSchema
        + "users SET last_active = ? WHERE user_id = ?";
    try {
      Date lastActiveTime = new Date();
      long start = System.currentTimeMillis();
      psUser = SqlUtils.getPreparedStatement(dataSource, sql);
      psUser.setTimestamp(1, new Timestamp(lastActiveTime.getTime()));
      psUser.setInt(2, user.getUserId());
      int result = psUser.executeUpdate();
      QueryLogger.logEndStatementExecution(sql, "wdk-user-update-user-last-active",
          start);
      if (result == 0)
        throw new WdkUserException("User " + user.getEmail()
            + " cannot be found.");
    } catch (SQLException ex) {
      throw new WdkModelException(ex);
    } finally {
      SqlUtils.closeStatement(psUser);
    }
  }

  public void deleteExpiredUsers(int hoursSinceActive) throws WdkUserException,
      WdkModelException {
    PreparedStatement psUser = null;
    ResultSet rsUser = null;
    String sql = "SELECT email FROM " + userSchema + "users " + "WHERE email "
        + "LIKE '" + GUEST_USER_PREFIX + "%' AND last_active < ?";
    try {
      // construct time
      Calendar calendar = Calendar.getInstance();
      calendar.add(Calendar.HOUR_OF_DAY, -hoursSinceActive);
      Timestamp timestamp = new Timestamp(calendar.getTime().getTime());

      long start = System.currentTimeMillis();
      psUser = SqlUtils.getPreparedStatement(dataSource, sql);
      psUser.setTimestamp(1, timestamp);
      rsUser = psUser.executeQuery();
      QueryLogger.logStartResultsProcessing(sql, "wdk-user-select-expired-user", start, rsUser);
      int count = 0;
      while (rsUser.next()) {
        deleteUser(rsUser.getString("email"));
        count++;
      }
      System.out.println("Deleted " + count + " expired users.");
    } catch (SQLException ex) {
      throw new WdkUserException(ex);
    } finally {
      SqlUtils.closeResultSetAndStatement(rsUser, psUser);
    }
  }

  private void savePreferences(User user) throws WdkModelException {
    // get old preferences and determine what to delete, update, insert
    int userId = user.getUserId();
    List<Map<String, String>> oldPreferences = getPreferences(user);
    Map<String, String> oldGlobal = oldPreferences.get(0);
    Map<String, String> newGlobal = user.getGlobalPreferences();
    updatePreferences(userId, GLOBAL_PREFERENCE_KEY, oldGlobal, newGlobal);

    Map<String, String> oldSpecific = oldPreferences.get(1);
    Map<String, String> newSpecific = user.getProjectPreferences();
    logger.debug("old pref: " + oldSpecific);
    logger.debug("new pref: " + newSpecific);
    updatePreferences(userId, projectId, oldSpecific, newSpecific);
  }

  private void updatePreferences(int userId, String projectId,
      Map<String, String> oldPreferences, Map<String, String> newPreferences)
      throws WdkModelException {
    // determine whether to delete, insert or update
    Set<String> toDelete = new LinkedHashSet<String>();
    Map<String, String> toUpdate = new LinkedHashMap<String, String>();
    Map<String, String> toInsert = new LinkedHashMap<String, String>();
    for (String key : oldPreferences.keySet()) {
      if (!newPreferences.containsKey(key)) {
        toDelete.add(key);
      } else { // key exist, check if need to update
        String newValue = newPreferences.get(key);
	String oldValue = oldPreferences.get(key);
	if (newValue == null || oldValue == null) 
	  throw new WdkModelException("Null values not allowed for preferences. Key: " + key + " Old pref: " + oldValue + " New pref: " + newValue);
	if (!oldPreferences.get(key).equals(newValue))
          toUpdate.put(key, newValue);
      }
    }
    for (String key : newPreferences.keySet()) {
      if (newPreferences.get(key) == null) 
	  throw new WdkModelException("Null values not allowed for new preference values. Key: " + key);
      if (!oldPreferences.containsKey(key))
        toInsert.put(key, newPreferences.get(key));
    }
    logger.debug("to insert: " + FormatUtil.prettyPrint(toInsert, Style.MULTI_LINE));
    logger.debug("to update: " + FormatUtil.prettyPrint(toUpdate, Style.MULTI_LINE));
    logger.debug("to delete: " + FormatUtil.arrayToString(toDelete.toArray()));

    PreparedStatement psDelete = null, psInsert = null, psUpdate = null;
    try {
      // delete preferences
      String sqlDelete = "DELETE FROM " + userSchema + "preferences "
          + " WHERE user_id = ? AND project_id = ? "
          + " AND preference_name = ?";
      psDelete = SqlUtils.getPreparedStatement(dataSource, sqlDelete);
      long start = System.currentTimeMillis();
      for (String key : toDelete) {
        psDelete.setInt(1, userId);
        psDelete.setString(2, projectId);
        psDelete.setString(3, key);
        psDelete.addBatch();
      }
      psDelete.executeBatch();
      QueryLogger.logEndStatementExecution(sqlDelete, "wdk-user-delete-preference",
          start);

      // insert preferences
      String sqlInsert = "INSERT INTO " + userSchema + "preferences "
          + " (user_id, project_id, preference_name, " + " preference_value)"
          + " VALUES (?, ?, ?, ?)";
      psInsert = SqlUtils.getPreparedStatement(dataSource, sqlInsert);
      start = System.currentTimeMillis();
      for (String key : toInsert.keySet()) {
        start = System.currentTimeMillis();
        psInsert.setInt(1, userId);
        psInsert.setString(2, projectId);
        psInsert.setString(3, key);
        psInsert.setString(4, toInsert.get(key));
        psInsert.addBatch();
      }
      psInsert.executeBatch();
      QueryLogger.logEndStatementExecution(sqlInsert, "wdk-user-insert-preference",
          start);

      // update preferences
      String sqlUpdate = "UPDATE " + userSchema + "preferences "
          + " SET preference_value = ? WHERE user_id = ? "
          + " AND project_id = ? AND preference_name = ?";
      psUpdate = SqlUtils.getPreparedStatement(dataSource, sqlUpdate);
      start = System.currentTimeMillis();
      for (String key : toUpdate.keySet()) {
        start = System.currentTimeMillis();
        psUpdate.setString(1, toUpdate.get(key));
        psUpdate.setInt(2, userId);
        psUpdate.setString(3, projectId);
        psUpdate.setString(4, key);
        psUpdate.addBatch();
      }
      psUpdate.executeBatch();
      QueryLogger.logEndStatementExecution(sqlUpdate, "wdk-user-update-preference",
          start);
    } catch (SQLException e) {
      throw new WdkModelException("Unable to update user (id=" + userId
          + ") preferences", e);
    } finally {
      SqlUtils.closeStatement(psDelete);
      SqlUtils.closeStatement(psInsert);
      SqlUtils.closeStatement(psUpdate);
    }
  }

  /**
   * @param user
   * @return a list of 2 elements, the first is a map of global preferences, the
   *         second is a map of project-specific preferences.
   */
  private List<Map<String, String>> getPreferences(User user)
      throws WdkModelException {
    Map<String, String> global = new LinkedHashMap<String, String>();
    Map<String, String> specific = new LinkedHashMap<String, String>();
    int userId = user.getUserId();
    PreparedStatement psSelect = null;
    ResultSet resultSet = null;
    String sql = "SELECT * FROM " + userSchema + "preferences "
        + " WHERE user_id = ?";
    try {
      // load preferences
      long start = System.currentTimeMillis();
      psSelect = SqlUtils.getPreparedStatement(dataSource, sql);
      psSelect.setInt(1, userId);
      resultSet = psSelect.executeQuery();
      QueryLogger.logStartResultsProcessing(sql, "wdk-user-select-preference", start, resultSet);
      while (resultSet.next()) {
        String projectId = resultSet.getString("project_id");
        String prefName = resultSet.getString("preference_name");
        String prefValue = resultSet.getString("preference_value");
        if (projectId.equals(GLOBAL_PREFERENCE_KEY))
          global.put(prefName, prefValue);
        else if (projectId.equals(this.projectId))
          specific.put(prefName, prefValue);
      }
    }
    catch (SQLException e) {
      throw new WdkModelException("Could not get preferences for user "
          + user.getUserId(), e);
    }
    finally {
      SqlUtils.closeResultSetAndStatement(resultSet, psSelect);
    }
    List<Map<String, String>> preferences = new ArrayList<Map<String, String>>();
    preferences.add(global);
    preferences.add(specific);
    return preferences;
  }

  public void resetPassword(String email) throws WdkUserException,
      WdkModelException {
    User user = getUserByEmail(email);
    if (user == null) {
      throw new WdkUserException("Cannot find user with email: " + email);
    }
    resetPassword(user);
  }

  private void resetPassword(User user) throws WdkModelException {
    String email = user.getEmail();

    // generate a random password of 8 characters long, the range will be
    // [0-9A-Za-z]
    StringBuffer buffer = new StringBuffer();
    Random rand = new Random();
    for (int i = 0; i < 8; i++) {
      int value = rand.nextInt(36);
      if (value < 10) { // number
        buffer.append(value);
      } else { // lower case letters
        buffer.append((char) ('a' + value - 10));
      }
    }
    String password = buffer.toString();

    savePassword(email, password);

    ModelConfig modelConfig = wdkModel.getModelConfig();
    String emailContent = modelConfig.getEmailContent();
    String supportEmail = modelConfig.getSupportEmail();
    String emailSubject = modelConfig.getEmailSubject();

    // send an email to the user
    String pattern = "\\$\\$" + EMAIL_MACRO_USER_NAME + "\\$\\$";
    String name = user.getFirstName() + " " + user.getLastName();
    String message = emailContent.replaceAll(pattern,
        Matcher.quoteReplacement(name));

    pattern = "\\$\\$" + EMAIL_MACRO_EMAIL + "\\$\\$";
    message = message.replaceAll(pattern, Matcher.quoteReplacement(email));

    pattern = "\\$\\$" + EMAIL_MACRO_PASSWORD + "\\$\\$";
    message = message.replaceAll(pattern, Matcher.quoteReplacement(password));

    Utilities.sendEmail(wdkModel, user.getEmail(), supportEmail, emailSubject,
        message);
  }

  void changePassword(String email, String oldPassword, String newPassword,
      String confirmPassword) throws WdkUserException, WdkModelException {
    email = email.trim();

    if (newPassword == null || newPassword.trim().length() == 0)
      throw new WdkUserException("The new password cannot be empty.");

    // check if the new password matches
    if (!newPassword.equals(confirmPassword))
      throw new WdkUserException("The new password doesn't match, "
          + "please type them again. It's case sensitive.");

    PreparedStatement ps = null;
    ResultSet rs = null;
    String sql = "SELECT count(*) " + "FROM " + userSchema
        + "users WHERE email = ? " + "AND passwd = ?";
    try {
      // encrypt password
      oldPassword = encrypt(oldPassword);

      // check if the old password matches
      long start = System.currentTimeMillis();
      ps = SqlUtils.getPreparedStatement(dataSource, sql);
      ps.setString(1, email);
      ps.setString(2, oldPassword);
      rs = ps.executeQuery();
      QueryLogger.logEndStatementExecution(sql,
          "wdk-user-count-user-by-email-password", start);
      rs.next();
      int count = rs.getInt(1);
      if (count <= 0)
        throw new WdkUserException("The current password is incorrect.");

      // passed check, then save the new password
      savePassword(email, newPassword);
    }
    catch (SQLException ex) {
      throw new WdkUserException(ex);
    }
    finally {
      SqlUtils.closeResultSetAndStatement(rs, ps);
    }

  }

  public void savePassword(String email, String password)
      throws WdkModelException {
    email = email.trim();
    PreparedStatement ps = null;
    String sql = "UPDATE " + userSchema
        + "users SET passwd = ? WHERE email = ?";
    try {
      // encrypt the password, and save it
      String encrypted = encrypt(password);
      long start = System.currentTimeMillis();
      ps = SqlUtils.getPreparedStatement(dataSource, sql);
      ps.setString(1, encrypted);
      ps.setString(2, email);
      int numRowsUpdated = ps.executeUpdate();
      QueryLogger.logEndStatementExecution(sql, "wdk-user-update-password", start);
      if (numRowsUpdated != 1) {
        throw new WdkModelException("Password update for user with email '" +
            email + "' updated " + numRowsUpdated + " rows.");
      }
    } catch (SQLException ex) {
      throw new WdkModelException(ex);
    } finally {
      SqlUtils.closeStatement(ps);
    }
  }

  private boolean isExist(String email) throws WdkUserException {
    email = email.trim();
    // check if user exists in the database. if not, fail and ask to create the user first
    PreparedStatement ps = null;
    ResultSet rs = null;
    String sql = "SELECT count(*) " + "FROM " + userSchema
        + "users WHERE email = ?";
    try {
      long start = System.currentTimeMillis();
      ps = SqlUtils.getPreparedStatement(dataSource, sql);
      ps.setString(1, email);
      rs = ps.executeQuery();
      QueryLogger.logEndStatementExecution(sql, "wdk-user-select-user-by-email", start);
      rs.next();
      int count = rs.getInt(1);
      return (count > 0);
    }
    catch (SQLException ex) {
      throw new WdkUserException(ex);
    }
    finally {
      SqlUtils.closeResultSetAndStatement(rs, ps);
    }
  }

  public void deleteUser(String email) throws WdkUserException, WdkModelException {
    try {
      // get user id
      User user = getUserByEmail(email);
      if (user == null) {
        throw new WdkUserException("Unable to find user with email: " + email);
      }
  
      // delete strategies and steps from all projects
      user.deleteStrategies(true);
      user.deleteSteps(true);
  
      String where = " WHERE user_id = " + user.getUserId();
  
      // delete preference
      String sql = "DELETE FROM " + userSchema + "preferences" + where;
      SqlUtils.executeUpdate(dataSource, sql,
          "wdk-user-delete-preference");
  
      // delete user roles
      sql = "DELETE FROM " + userSchema + "user_roles" + where;
      SqlUtils.executeUpdate(dataSource, sql, "wdk-user-delete-role");
  
      // delete user
      sql = "DELETE FROM " + userSchema + "users" + where;
      SqlUtils.executeUpdate(dataSource, sql, "wdk-user-delete-user");
    }
    catch (SQLException e) {
      throw new WdkModelException(e);
    }
  }

  public WdkModel getWdkModel() {
    return wdkModel;
  }
}
