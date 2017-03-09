package org.gusdb.wdk.model.fix;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.BaseCLI;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.db.runner.BasicResultSetHandler;
import org.gusdb.fgputil.db.runner.SQLRunner;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.config.ModelConfigUserDB;

/**
 * @author steve fischer
 * 
 *         this script needs to be run after the model expander & step expander.
 * 
 *         prints a report of invalid steps
 */
public class InvalidStepReporter extends BaseCLI {

  public static final String ARG_INCLUDE = "include";
  public static final String ARG_SUMMARY_ONLY = "summaryOnly";
  public static final String VALUE_ALL = "all";
  public static final String VALUE_INVALID = "invalid";
  public static final String VALUE_VALID = "valid";

  private static final Logger logger = Logger.getLogger(StepValidator.class);

  public static void main(String[] args) {
    String cmdName = System.getProperty("cmdName");
    InvalidStepReporter reporter = new InvalidStepReporter(cmdName);
    try {
      reporter.invoke(args);
      logger.info("report complete.");
      System.exit(0);
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(1);
    }
  }

  public InvalidStepReporter(String command) {
    super((command == null) ? command : "wdkInvalidStepReport",
        "store model information into database");
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.fgputil.BaseCLI#declareOptions()
   */
  @Override
  protected void declareOptions() {
    addSingleValueOption(ARG_PROJECT_ID, true, null, "ProjectId, which "
        + "should match the directory name under $GUS_HOME, where "
        + "model-config.xml is stored.  This model-conig.xml file is "
        + "only used to find the login info for the User database.");

    addSingleValueOption(ARG_INCLUDE, false, VALUE_VALID, "An optional flag "
        + "to determine what steps to be included in the report. "
        + "By default, only include valid steps. The available values are, "
        + "all, valid, invalid; and by default, valid.");
    
    addNonValueOption(ARG_SUMMARY_ONLY, false, "Do not run the full invalid step report.  Only do a summary of projects and their valid/invalid counts");


  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.fgputil.BaseCLI#execute()
   */
  @Override
  protected void execute() throws Exception {
    String gusHome = System.getProperty(Utilities.SYSTEM_PROPERTY_GUS_HOME);

    String projectId = (String) getOptionValue(ARG_PROJECT_ID);
    String include = (String) getOptionValue(ARG_INCLUDE);
    try (WdkModel wdkModel = WdkModel.construct(projectId, gusHome)) {

      if ((Boolean) getOptionValue(ARG_SUMMARY_ONLY)) {
        summaryReport(wdkModel);
        System.exit(0);
      }

      // make sure the include value is correct
      if (!include.equals(VALUE_ALL) && !include.equals(VALUE_INVALID)
          && !include.equals(VALUE_VALID)) {
        printUsage();
        System.exit(-1);
      }
  
      report(wdkModel, include);
    }
  }
  
  private void summaryReport(WdkModel wdkModel) {
    ModelConfigUserDB userDB = wdkModel.getModelConfig().getUserDB();
    String steps = userDB.getUserSchema() + "steps";
    String users = userDB.getUserSchema() + "users";
    
    DataSource dataSource = wdkModel.getUserDb().getDataSource();

    String sql = "select s.project_id,s.is_valid,count(*) as count " +
        "from " + steps  + " s, " + users + " u " +        
        " where s.user_id = u.user_id " +
        " and u.is_guest = 0 " + 
        " group by project_id,is_valid " +
        " order by project_id,is_valid";
    
    Object[] args = {};
    BasicResultSetHandler handler = new BasicResultSetHandler();
    
    System.out.println("Invalid step summary report");
    System.out.println("");
    
    new SQLRunner(dataSource, sql, "invalid-step-report-summary").executeQuery(args, handler);
    List<Map<String,Object>> results = handler.getResults();
    for (Map<String,Object> row : results) {
      String proj = (String)row.get("PROJECT_ID");
      BigDecimal is_val = (BigDecimal)row.get("IS_VALID");
      BigDecimal cnt = (BigDecimal)row.get("COUNT");
      System.out.println(proj + "\t" + is_val + "\t" + cnt);
    } 
  }

  private void report(WdkModel wdkModel, String include) throws SQLException {
    System.out.println("Reporting invalid steps which are previously "
        + ((include.equals(VALUE_ALL) ? "valid & invalid."
            : include.equals(VALUE_VALID) ? "valid" : "invalid")));

    // determine the condition
    String condition;
    if (include.equals(VALUE_INVALID)) {
      condition = " AND s.is_valid = 0 ";
    } else if (include.equals(VALUE_VALID)) {
      condition = " AND (s.is_valid = 1 OR s.is_valid IS NULL)";
    } else {
      condition = "";
    }

    questionNames(wdkModel, condition);
    paramNames(wdkModel, condition);
    paramValues(wdkModel, "FlatVocabParam", condition);
    paramValues(wdkModel, "EnumParam", condition);
  }

  private void questionNames(WdkModel wdkModel, String flag)
      throws SQLException {
    ModelConfigUserDB userDB = wdkModel.getModelConfig().getUserDB();
    String steps = userDB.getUserSchema() + "steps";
    String users = userDB.getUserSchema() + "users";
    DataSource dataSource = wdkModel.getUserDb().getDataSource();

    String sql = "SELECT count(distinct s.step_id) count, s.project_id, s.question_name"
        + " FROM " + steps + " s," + users + " u,"
        + "  (SELECT project_id, question_name FROM " + steps
        + "   MINUS      "
        + "   SELECT project_id, question_name FROM wdk_questions) d"
        + " WHERE s.project_id = d.project_id"
				+ " AND s.user_id = u.user_id "
				+ " AND u.is_guest = 0 "
        + " AND s.question_name = d.question_name " + flag
        + " GROUP BY s.project_id, s.question_name"
        + " ORDER BY s.project_id, s.question_name";
 
    ResultSet resultSet = SqlUtils.executeQuery(dataSource, sql,
        "wdk-invalid-report-questions");
    System.out.println("----------- Invalid Question Name ------------");
    System.out.println("");
    System.out.println("count\tproject_id\tquestion_name");
    try {
      while (resultSet.next()) {
        String count = resultSet.getString("count");
        String project_id = resultSet.getString("project_id");
        String question_name = resultSet.getString("question_name");
        System.out.println(count + "\t" + project_id + "\t" + question_name);
      }
    } finally {
      SqlUtils.closeResultSetOnly(resultSet);
    }
    System.out.println("");
    System.out.println("");
  }

  private void paramNames(WdkModel wdkModel, String flag) throws SQLException {
    ModelConfigUserDB userDB = wdkModel.getModelConfig().getUserDB();
    String steps = userDB.getUserSchema() + "steps";
    String users = userDB.getUserSchema() + "users";
    DataSource dataSource = wdkModel.getUserDb().getDataSource();

    String sql = "SELECT count(distinct s.step_id) count, s.project_id, s.question_name, "
        + "      sp.param_name       "
        + "FROM step_params sp, wdk_questions wq, " + steps + " s, " + users + " u, "
        + "     ( SELECT s.project_id, s.question_name, sp.param_name "
        + "       FROM step_params sp, " + steps + " s "
        + "       WHERE sp.step_id = s.step_id             " + flag
        + "      MINUS  "
        + "       SELECT q.project_id, q.question_name, p.param_name  "
        + "       FROM wdk_questions q, wdk_params p "
        + "       WHERE q.question_id = p.question_id) d     "
        + "WHERE s.project_id = d.project_id  "
				+ " AND s.user_id = u.user_id "
				+ " AND u.is_guest = 0 "
        + "      AND s.question_name = d.question_name " + flag
        + "      AND sp.step_id = s.step_id  "
        + "      AND sp.param_name = d.param_name "
        + "      and s.project_id = wq.project_id "
        + "      and s.question_name = wq.question_name "
        + "GROUP BY s.project_id, s.question_name, sp.param_name "
        + "ORDER BY s.project_id, s.question_name, sp.param_name ";

    ResultSet resultSet = SqlUtils.executeQuery(dataSource, sql,
        "wdk-invalid-report-params");
    System.out.println("----------- Invalid Param Name ------------");
    System.out.println("");
    System.out.println("count\tproject_id\tquestion_name\tparam_name");
    try {
      while (resultSet.next()) {
        String count = resultSet.getString("count");
        String project_id = resultSet.getString("project_id");
        String question_name = resultSet.getString("question_name");
        String param_name = resultSet.getString("param_name");
        System.out.println(count + "\t" + project_id + "\t" + question_name
            + "\t" + param_name);

      }
    } finally {
      SqlUtils.closeResultSetOnly(resultSet);
    }
    System.out.println("");
    System.out.println("");
  }

  private void paramValues(WdkModel wdkModel, String type, String flag)
      throws SQLException {
    ModelConfigUserDB userDB = wdkModel.getModelConfig().getUserDB();
    String steps = userDB.getUserSchema() + "steps";
    String users = userDB.getUserSchema() + "users";
    DataSource dataSource = wdkModel.getUserDb().getDataSource();

    String sql = "SELECT count(distinct s.step_id) count, s.project_id, s.question_name, "
        + "      sp.param_name, d.param_value              "
        + "FROM step_params sp, wdk_questions wq, wdk_params wp, "
        + steps
        + " s, "
				+ users
        + " u, "
        + "     (  SELECT s.project_id, s.question_name,  "
        + "               sp.param_name, sp.param_value        "
        + "        FROM step_params sp, wdk_questions q, wdk_params p, "
        + steps
        + " s "
        + "        WHERE sp.step_id = s.step_id  "
        + "          AND s.project_id = q.project_id  "
        + "          AND s.question_name = q.question_name  "
        + "          AND q.question_id = p.question_id  "
        + "          AND sp.param_name = p.param_name  "
        + "          AND p.param_type = '"
        + type
        + "'"
        + flag
        + "      MINUS            "
        + "        SELECT q.project_id, q.question_name,  "
        + "               p.param_name, ep.param_value  "
        + "        FROM wdk_questions q, wdk_params p, wdk_enum_params ep "
        + "        WHERE q.question_id = p.question_id  "
        + "          AND p.param_id = ep.param_id) d  "
        + "WHERE s.project_id = d.project_id  "
				+ " AND s.user_id = u.user_id "
				+ " AND u.is_guest = 0 "
        + "  AND s.question_name = d.question_name  "
        + "  AND sp.step_id = s.step_id  "
        + "  AND sp.param_name = d.param_name  "
        + "  AND sp.param_value = d.param_value  "
        + "  and s.project_id = wq.project_id "
        + "  and s.question_name = wq.question_name "
        + "  and wp.question_id = wq.question_id "
        + "  and sp.param_name = wp.param_name "
        + flag
        + "GROUP BY s.project_id, s.question_name, "
        + "         sp.param_name, d.param_value "
        + "ORDER BY s.project_id, s.question_name, "
        + "         sp.param_name, d.param_value ";

    System.out.println("----------- Invalid Param Value (" + type
        + ") ------------");
    System.out.println("");
    System.out.println("count\tproject_id\tquestion_name\tparam_name\tparam_value");

    ResultSet resultSet = SqlUtils.executeQuery(dataSource, sql,
        "wdk-invalid-report-param-values");
    try {
      while (resultSet.next()) {
        String count = resultSet.getString("count");
        String project_id = resultSet.getString("project_id");
        String question_name = resultSet.getString("question_name");
        String param_name = resultSet.getString("param_name");
        String param_value = resultSet.getString("param_value");
        System.out.println(count + "\t" + project_id + "\t" + question_name
            + "\t" + param_name + "\t" + param_value);
      }
    } finally {
      SqlUtils.closeResultSetOnly(resultSet);
    }
    System.out.println("");
    System.out.println("");
  }
}
