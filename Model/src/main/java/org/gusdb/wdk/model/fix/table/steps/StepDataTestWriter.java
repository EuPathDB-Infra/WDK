package org.gusdb.wdk.model.fix.table.steps;

import static org.gusdb.fgputil.ArrayUtil.append;
import static org.gusdb.fgputil.FormatUtil.join;
import static org.gusdb.fgputil.functional.Functions.mapToList;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.ListBuilder;
import org.gusdb.fgputil.db.DBStateException;
import org.gusdb.fgputil.db.pool.DatabaseInstance;
import org.gusdb.fgputil.db.runner.SQLRunner;
import org.gusdb.fgputil.functional.FunctionalInterfaces.Function;
import org.gusdb.wdk.model.WdkModel;

/**
 * Overrides StepDataFactory to not actually write data back to the Steps table, but instead
 * insert records into a test table
 * 
 * @author rdoherty
 */
public class StepDataTestWriter extends StepDataWriter {

  public static final Logger LOG = Logger.getLogger(StepDataTestWriter.class);
  
  private static final String TEST_TABLE_SCHEMA = "wdkmaint";
  private static final String TEST_TABLE_NAME = "STEP_UPDATER_PLUGIN_TEST";
  private static final String TEST_TABLE = TEST_TABLE_SCHEMA + "." + TEST_TABLE_NAME;

  private static final String CREATE_TABLE_SQL =
      "CREATE TABLE " + TEST_TABLE + " (" +
          "\"STEP_ID\"            NUMBER(12,0) NOT NULL ENABLE," +
          "\"LEFT_CHILD_ID\"      NUMBER(12,0)," +
          "\"RIGHT_CHILD_ID\"     NUMBER(12,0)," +
          "\"ANSWER_FILTER\"      VARCHAR2(100 BYTE)," +
          "\"PROJECT_ID\"         VARCHAR2(50 BYTE) NOT NULL ENABLE," +
          "\"QUESTION_NAME\"      VARCHAR2(200 BYTE) NOT NULL ENABLE," +
          "\"DISPLAY_PARAMS\"     CLOB," +
          "\"OLD_DISPLAY_PARAMS\" CLOB," +
          "PRIMARY KEY (\"STEP_ID\"))";

  private static final String INSERT_COLS_TEXT = StepDataFactory.SELECT_COLS_TEXT + ",OLD_DISPLAY_PARAMS";

  private static final String INSERT_WILDCARDS = join(mapToList(SQLTYPES.keySet(), new Function<String, String>() {
    @Override public String apply(String obj) { return "?"; }}).toArray(), ",") + ",?";

  private static final String INSERT_SQL = "INSERT INTO " + TEST_TABLE + " (" +
      INSERT_COLS_TEXT + ") VALUES (" + INSERT_WILDCARDS + ")";

  private static final Integer[] INSERT_PARAMETER_TYPES = append(
      SQLTYPES.values().toArray(new Integer[SQLTYPES.size()]), Types.CLOB);

  private static final String DELETE_TEST_RECORDS = "DELETE FROM " + TEST_TABLE;

  @Override
  public void setUp(WdkModel wdkModel) throws DBStateException, SQLException {
    // need to create the test table if it doesn't exit and clear it if it does
    DatabaseInstance userDb = wdkModel.getUserDb();
    DataSource dataSource = userDb.getDataSource();
    LOG.info("Checking for existence of " + TEST_TABLE);
    if (userDb.getPlatform().checkTableExists(dataSource, TEST_TABLE_SCHEMA, TEST_TABLE_NAME)) {
      // clear previous run's records
      LOG.info(TEST_TABLE + " exists. Emptying...");
      new SQLRunner(dataSource, DELETE_TEST_RECORDS).executeStatement();
    }
    else {
      // create empty table
      LOG.info(TEST_TABLE + " does not exist. Creating...");
      new SQLRunner(dataSource, CREATE_TABLE_SQL).executeStatement();
    }
  }

  @Override
  public String getWriteSql(String schema) {
    return INSERT_SQL;
  }

  @Override
  public Integer[] getParameterTypes() {
    return INSERT_PARAMETER_TYPES;
  }

  @Override
  public Collection<Object[]> toValues(StepData row) {
    return ListBuilder.asList(new Object[] {
        row.getStepId(),
        row.getLeftChildId(),
        row.getRightChildId(),
        row.getLegacyAnswerFilter(),
        row.getProjectId(),
        row.getQuestionName(),
        row.getParamFilters().toString(),
        row.getOrigParamFiltersString()
    });
  }
}
