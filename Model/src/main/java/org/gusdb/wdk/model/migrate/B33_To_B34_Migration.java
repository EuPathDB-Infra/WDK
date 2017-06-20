package org.gusdb.wdk.model.migrate;

import static org.gusdb.fgputil.FormatUtil.NL;

import javax.sql.DataSource;

import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.db.platform.Oracle;
import org.gusdb.fgputil.db.platform.SupportedPlatform;
import org.gusdb.fgputil.db.pool.DatabaseInstance;
import org.gusdb.fgputil.db.pool.SimpleDbConfig;
import org.gusdb.fgputil.db.runner.SQLRunner;
import org.gusdb.fgputil.db.runner.SingleLongResultSetHandler;
import org.gusdb.fgputil.db.slowquery.QueryLogger;

/**
 * This script has two options: create and drop
 * 
 * Create:
 * 1. Creates a new sequence in primary accountDb schema from old primary userDb sequence
 * 2. (if replication) Creates a new sequence in secondary accountDb schema from old secondary userDb sequence
 * 3. Create accounts and account_properties tables in primary accountDb schema from primary userDb users table
 * 4. Copy primary userDb users table to users_bk
 * 5. Trim columns from primary userDb users table (except user_id, is_guest, registration_time)
 * 6. Rename registration_time column to first_access
 * 
 * Drop:
 * 1. Drops accounts and account_properties table, and account ID sequence, from primary accountDb schema
 * 
 * EuPathDB plan:
 * 
 * After b33 release (and apicommdev copy from apicomm):
 * - merge account_db branch code to trunk (+OAuth2Server)
 * - execute this script's 'create' option against accountdb/apicommdevN + replication
 * - build OAuth2 integrate server (BUT NOT LIVE OAUTH!!) with trunk code (will happen automatically)
 * - tell developers they must use "userDb" authMethod or switch to the integrate OAuth2 server
 * 
 * During b34 release:
 * - execute this script's 'drop' option against accountdb
 * - execute this script's 'create' option against accountdb/apicommN + replication
 * 
 * @author rdoherty
 */
public class B33_To_B34_Migration {

  // configuration constants
  private static final boolean WRITE_TO_DB = false;    // keep off to check generated SQL
  private static final boolean REPLICATED_DBS = false; // keep off until testing on apicommDev

  // connection information to user DBs
  private static final String PRIMARY_USERDB_CONNECTION_URL = "jdbc:oracle:oci:@rm9972"; // to be apicommdevn
  //private static final String REPLICATED_USERDB_CONNECTION_URL = "jdbc:oracle:oci:@rm9972"; // to be apicommdevs

  // connection information to account DBs
  //private static final String PRIMARY_ACCTDB_CONNECTION_URL = "jdbc:oracle:oci:@acctdbn";
  private static final String PRIMARY_ACCTDB_CONNECTION_URL = "jdbc:oracle:oci:@rm9972";
  private static final String REPLICATED_ACCTDB_CONNECTION_URL = "jdbc:oracle:oci:@acctdbs";

  // object names when operating in userDBs
  private static final String USER_DB_SCHEMA = "userlogins5.";
  private static final String USERS_TABLE = USER_DB_SCHEMA + "users";
  private static final String BACKUP_USERS_TABLE = USER_DB_SCHEMA + "users_backup";

  // object names when operating in account DBs
  //private static final String ACCOUNT_DB_SCHEMA = "useraccounts.";
  private static final String ACCOUNT_DB_SCHEMA = "wdkmaint.";
  private static final String NEW_TABLE_ACCOUNTS = ACCOUNT_DB_SCHEMA + "accounts";
  private static final String NEW_TABLE_ACCOUNT_PROPS = ACCOUNT_DB_SCHEMA + "account_properties";
  private static final String NEW_USER_ID_SEQUENCE = ACCOUNT_DB_SCHEMA + NEW_TABLE_ACCOUNTS + "_PKSEQ";

  // userdb objects needed to be read while creating account DB
  private static final String ACCOUNTDB_DBLINK_TO_USERDB = ""; // add dblink suffix (including '@') to userdb here if needed
  private static final String SOURCE_USERS_TABLE = USERS_TABLE + ACCOUNTDB_DBLINK_TO_USERDB;
  private static final String SOURCE_USERS_BACKUP_TABLE = BACKUP_USERS_TABLE + ACCOUNTDB_DBLINK_TO_USERDB;
  private static final String SOURCE_USER_ID_SEQUENCE = SOURCE_USERS_TABLE + "_PKSEQ" + ACCOUNTDB_DBLINK_TO_USERDB;

  /*======================================================*/
  /*          SQL to be executed on AccountDB             */
  /*======================================================*/

  private static final String SEQUENCE_START_NUM_MACRO = "$$sequence_start_macro$$";

  private static final String READ_OLD_USER_ID_SEQUENCE =
      "SELECT " + SOURCE_USER_ID_SEQUENCE + ".nextval" + ACCOUNTDB_DBLINK_TO_USERDB + " from dual";
  
  private static final String CREATE_ACCOUNT_USER_ID_SEQUENCE =
      "CREATE SEQUENCE " + NEW_USER_ID_SEQUENCE +
      "  MINVALUE 1 MAXVALUE 9999999999999999999999999999" +
      "  INCREMENT BY 10" +
      "  START WITH " + SEQUENCE_START_NUM_MACRO +
      "  CACHE 20 NOORDER NOCYCLE";

  private static final String CREATE_ACCOUNT_TABLE_SQL =
      "create table " + NEW_TABLE_ACCOUNTS + " as ( " +
      "  select user_id, email, passwd, is_guest, signature, address as stable_id, register_time, last_active as last_login " +
      "  from " + SOURCE_USERS_TABLE +
      "  where is_guest = 0" +
      ")";

  private static final String SELECT_USER_PROPS_SQL_SUFFIX =
      " from " + SOURCE_USERS_TABLE + " where is_guest = 0 ";

  private static final String CREATE_ACCOUNT_PROPS_TABLE_SQL =
      "create table " + NEW_TABLE_ACCOUNT_PROPS + " as ( " +
      "  select user_id, 'first_name' as key, first_name as value " + SELECT_USER_PROPS_SQL_SUFFIX +
      "  union " +
      "  select user_id, 'middle_name' as key, middle_name as value " + SELECT_USER_PROPS_SQL_SUFFIX +
      "  union " +
      "  select user_id, 'last_name' as key, last_name as value " + SELECT_USER_PROPS_SQL_SUFFIX +
      "  union " +
      "  select user_id, 'organization' as key, organization as value " + SELECT_USER_PROPS_SQL_SUFFIX +
      ")";

  private static final String RESIZE_PROPERTY_VALUE_COL_SQL =
      "ALTER TABLE " + NEW_TABLE_ACCOUNT_PROPS + " MODIFY value VARCHAR2(4000)";

  private static final String DELETE_ACCOUNTS_TABLE_SQL = "delete table " + NEW_TABLE_ACCOUNTS;
  private static final String DELETE_ACCOUNT_PROPS_TABLE_SQL = "delete table " + NEW_TABLE_ACCOUNT_PROPS;
  private static final String DELETE_ACCOUNT_SEQUENCE_TABLE_SQL = "delete sequence " + NEW_USER_ID_SEQUENCE;

  /*======================================================*/
  /*          SQL to be executed on UserDB             */
  /*======================================================*/

  private static final String BACK_UP_USERS_TABLE =
      "create table " + BACKUP_USERS_TABLE + " as (" +
      "  select * from " + USERS_TABLE +
      ")";

  private static final String DROP_COLS_FROM_USERS_TABLE =
      "alter table " + USERS_TABLE + " drop (" +
      "  EMAIL, PASSWD, SIGNATURE, REGISTER_TIME, LAST_ACTIVE, LAST_NAME," +
      "  FIRST_NAME, MIDDLE_NAME, TITLE, ORGANIZATION, DEPARTMENT, ADDRESS," +
      "  CITY, STATE, ZIP_CODE, PHONE_NUMBER, COUNTRY, PREV_USER_ID, MIGRATION_ID" +
      ")";

  /*===========================================================================*/
  /* SqlGetter interface and implementations which provide various SQLs to run */
  /*===========================================================================*/

  private static interface SqlGetter {
    public String getSql(DataSource ds) throws Exception;
  }

  private static SqlGetter doSql(String sql) {
    return ds -> sql;
  }

  private static SqlGetter createAccountSequenceFromUserSequence() {
    return ds -> {
      SingleLongResultSetHandler result = new SingleLongResultSetHandler();
      new SQLRunner(ds, READ_OLD_USER_ID_SEQUENCE).executeQuery(result);
      return CREATE_ACCOUNT_USER_ID_SEQUENCE.replace(
          SEQUENCE_START_NUM_MACRO, result.getRetrievedValue().toString());
    };
  }

  private static SqlGetter conditionallyUseBackupTable(String originalSql) {
    return ds -> new Oracle().checkTableExists(
        ds, USER_DB_SCHEMA.substring(0, USER_DB_SCHEMA.length() - 1), SOURCE_USERS_BACKUP_TABLE) ?
            originalSql.replace(SOURCE_USERS_TABLE, SOURCE_USERS_BACKUP_TABLE) : originalSql;
  }

  /*===================================================================*/
  /* Groups of commands to be run depending on command and replication */
  /*===================================================================*/

  private static final SqlGetter[] PRIMARY_SQLS_TO_RUN_ACCOUNT_DB = {
      // create a new sequence in account DB with the start ID of the old sequence
      createAccountSequenceFromUserSequence(),
      // create new account tables from user table
      conditionallyUseBackupTable(CREATE_ACCOUNT_TABLE_SQL),
      conditionallyUseBackupTable(CREATE_ACCOUNT_PROPS_TABLE_SQL),
      doSql(RESIZE_PROPERTY_VALUE_COL_SQL)
  };

  private static final SqlGetter[] REPLICATED_SQLS_TO_RUN_ACCOUNT_DB = {
      createAccountSequenceFromUserSequence()
  };

  private static final SqlGetter[] PRIMARY_SQLS_TO_RUN_USER_DB = {
      // make a copy of the users table
      doSql(BACK_UP_USERS_TABLE),
      // trim columns off existing user table
      doSql(DROP_COLS_FROM_USERS_TABLE)
  };

  private static final SqlGetter[] DROP_ACCOUNT_DB_SQLS = {
      doSql(DELETE_ACCOUNT_PROPS_TABLE_SQL),
      doSql(DELETE_ACCOUNTS_TABLE_SQL),
      doSql(DELETE_ACCOUNT_SEQUENCE_TABLE_SQL)
  };

  /*============================================*/
  /*         main method and subroutines        */
  /*============================================*/

  private static void printUsageAndExit() {
    System.err.println(NL + "USAGE: fgpJava " + B33_To_B34_Migration.class.getName() + " [create|drop] <db_user> <db_password>" + NL);
    System.exit(1);
  }

  public static void main(String[] args) {
    if (args.length != 3 || args[1].trim().isEmpty() || args[2].trim().isEmpty()) {
      printUsageAndExit();
    }
    String operation = args[0];
    String dbUser = args[1];
    String dbPassword = args[2];
    QueryLogger.setInactive();
    switch (operation) {
      case "create":
        runSqls(PRIMARY_ACCTDB_CONNECTION_URL, PRIMARY_SQLS_TO_RUN_ACCOUNT_DB, dbUser, dbPassword);
        runSqls(PRIMARY_USERDB_CONNECTION_URL, PRIMARY_SQLS_TO_RUN_USER_DB, dbUser, dbPassword);
        if (REPLICATED_DBS) {
          runSqls(REPLICATED_ACCTDB_CONNECTION_URL, REPLICATED_SQLS_TO_RUN_ACCOUNT_DB, dbUser, dbPassword);
        }
        break;
      case "drop":
        runSqls(PRIMARY_ACCTDB_CONNECTION_URL, DROP_ACCOUNT_DB_SQLS, dbUser, dbPassword);
        break;
      default:
        printUsageAndExit();
    }
  }

  private static void runSqls(String connectionUrl, SqlGetter[] sqlsToRun, String dbUser, String dbPassword) {
    SimpleDbConfig dbConfig = SimpleDbConfig.create(SupportedPlatform.ORACLE, connectionUrl, dbUser, dbPassword);
    try (DatabaseInstance db = new DatabaseInstance(dbConfig)) {
      DataSource ds = db.getDataSource();
      for (SqlGetter sqlGen : sqlsToRun) {
        String sql = sqlGen.getSql(ds);
        System.out.println("Executing on " + connectionUrl + ":" + FormatUtil.NL + sql);
        if (WRITE_TO_DB) {
          new SQLRunner(ds, sql).executeStatement();
        }
      }
    }
    catch (Exception e) {
      System.err.println("Error while executing migration: " + FormatUtil.getStackTrace(e));
      System.exit(2);
    }
  }
}
