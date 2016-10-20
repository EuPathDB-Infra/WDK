/**
 * 
 */
package org.gusdb.wdk.model.test.stress;

import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.test.CommandHelper;
import org.gusdb.wdk.model.test.stress.StressTestTask.ResultType;

/**
 * @author: Jerric
 * @created: Mar 16, 2006
 * @modified by: Jerric
 * @modified at: Mar 16, 2006
 * 
 */
public class StressTestAnalyzer {

    private static final Logger logger = Logger.getLogger(StressTestAnalyzer.class);

    private long testTag;
    private DataSource dataSource;

    public StressTestAnalyzer(long testTag, WdkModel wdkModel) {
        this.testTag = testTag;
        dataSource = wdkModel.getAppDb().getDataSource();
    }

    public long getTaskCount() throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT count(*) FROM " + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        return (Long) SqlUtils.executeScalar(dataSource,
                sb.toString(), "wdk-stress-result-count");
    }

    public long getTaskCount(String taskType) throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT count(*) FROM " + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND task_type = '" + taskType + "'");
        return (Long) SqlUtils.executeScalar(dataSource,
                sb.toString(), "wdk-stress-result-count-by-type");
    }

    public long getSucceededTaskCount() throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT count(*) FROM " + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND result_type = '" + ResultType.Succeeded.name() + "'");
        return (Long) SqlUtils.executeScalar(dataSource,
                sb.toString(), "wdk-stress-result-count-by-type");
    }

    public long getSucceededTaskCount(String taskType) throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT count(*) FROM " + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND result_type = '" + ResultType.Succeeded.name() + "'");
        sb.append(" AND task_type = '" + taskType + "'");
        return (Long) SqlUtils.executeScalar(dataSource,
                sb.toString(), "wdk-stress-result-count-by-type");
    }

    public long getFailedTaskCount() throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT count(*) FROM " + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND result_type != '" + ResultType.Succeeded.name() + "'");
        return (Long) SqlUtils.executeScalar(dataSource,
                sb.toString(), "wdk-stress-result-count-by-type");
    }

    public long getFailedTaskCount(String taskType) throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT count(*) FROM " + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND result_type != '" + ResultType.Succeeded.name() + "'");
        sb.append(" AND task_type = '" + taskType + "'");
        return (Long) SqlUtils.executeScalar(dataSource,
                sb.toString(), "wdk-stress-result-count-by-type");
    }

    public long getTaskCount(ResultType resultType) throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT count(*) FROM " + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND result_type = '" + resultType.name() + "'");
        return (Long) SqlUtils.executeScalar(dataSource,
                sb.toString(), "wdk-stress-result-count-by-type");
    }

    public long getTaskCount(ResultType resultType, String taskType)
            throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT count(*) FROM " + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND result_type = '" + resultType.name() + "'");
        sb.append(" AND task_type = '" + taskType + "'");
        return (Long) SqlUtils.executeScalar(dataSource,
                sb.toString(), "wdk-stress-result-count-by-type");
    }

    public float getTaskSuccessRatio() throws SQLException {
        long total = getTaskCount();
        long succeeded = getSucceededTaskCount();
        return ((float) succeeded / total);
    }

    public float getTaskSuccessRatio(String taskType) throws SQLException {
        long total = getTaskCount(taskType);
        long succeeded = getSucceededTaskCount(taskType);
        return (total == 0) ? 0 : ((float) succeeded / total);
    }

    public float getTotalResponseTime() throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT sum(end_time - start_time) FROM "
                + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        ResultSet rs = SqlUtils.executeQuery(dataSource,
                sb.toString(), "wdk-stress-response-time");
        rs.next();
        long sum = rs.getLong(1);
        SqlUtils.closeResultSetAndStatement(rs, null);
        return (sum / 1000F);
    }

    public float getTotalResponseTime(String taskType) throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT sum(end_time - start_time) FROM "
                + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND task_type = '" + taskType + "'");
        ResultSet rs = SqlUtils.executeQuery(dataSource,
                sb.toString(), "wdk-stress-response-time-by-type");
        rs.next();
        long sum = rs.getLong(1);
        SqlUtils.closeResultSetAndStatement(rs, null);
        return (sum / 1000F);
    }

    public float getAverageResponseTime() throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT avg(end_time - start_time) FROM "
                + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        ResultSet rs = SqlUtils.executeQuery(dataSource,
                sb.toString(), "wdk-stress-response");
        rs.next();
        float average = rs.getFloat(1);
        SqlUtils.closeResultSetAndStatement(rs, null);
        return (average / 1000F);
    }

    public float getAverageResponseTime(String taskType) throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT avg(end_time - start_time) FROM "
                + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND task_type = '" + taskType + "'");
        ResultSet rs = SqlUtils.executeQuery(dataSource,
                sb.toString(), "wdk-stress-response-time-by-type");
        rs.next();
        float average = rs.getFloat(1);
        SqlUtils.closeResultSetAndStatement(rs, null);
        return (average / 1000F);
    }

    public float getTotalSucceededResponseTime() throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT sum(end_time - start_time) FROM "
                + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND result_type = '" + ResultType.Succeeded.name() + "'");
        ResultSet rs = SqlUtils.executeQuery(dataSource,
                sb.toString(), "wdk-stress-response-time-by-type");
        rs.next();
        long sum = rs.getLong(1);
        SqlUtils.closeResultSetAndStatement(rs, null);
        return (sum / 1000F);
    }

    public float getTotalSucceededResponseTime(String taskType)
            throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT sum(end_time - start_time) FROM "
                + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND result_type = '" + ResultType.Succeeded.name() + "'");
        sb.append(" AND task_type = '" + taskType + "'");
        ResultSet rs = SqlUtils.executeQuery(dataSource,
                sb.toString(), "wdk-stress-response-time-by-type");
        rs.next();
        long sum = rs.getLong(1);
        SqlUtils.closeResultSetAndStatement(rs, null);
        return (sum / 1000F);
    }

    public float getAverageSucceededResponseTime() throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT avg(end_time - start_time) FROM "
                + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND result_type = '" + ResultType.Succeeded.name() + "'");
        ResultSet rs = SqlUtils.executeQuery(dataSource,
                sb.toString(), "wdk-stress-response-time-by-type");
        rs.next();
        float average = rs.getFloat(1);
        SqlUtils.closeResultSetAndStatement(rs, null);
        return (average / 1000F);
    }

    public float getAverageSucceededResponseTime(String taskType)
            throws SQLException {
        StringBuffer sb = new StringBuffer();
        sb.append("SELECT avg(end_time - start_time) FROM "
                + StressTester.TABLE_STRESS_RESULT);
        sb.append(" WHERE test_tag = " + testTag);
        sb.append(" AND result_type = '" + ResultType.Succeeded.name() + "'");
        sb.append(" AND task_type = '" + taskType + "'");
        ResultSet rs = SqlUtils.executeQuery(dataSource,
                sb.toString(), "wdk-stress-response-time-by-type");
        rs.next();
        float average = rs.getFloat(1);
        SqlUtils.closeResultSetAndStatement(rs, null);
        return (average / 1000F);
    }

    public void print() throws SQLException {
        // print out results

        // print out all types results
        System.out.println("------------------ All Types --------------------");
        System.out.println("# of Total Tasks: " + getTaskCount());
        System.out.print("# of Succeeded Tasks: " + getSucceededTaskCount());
        System.out.println("\t (" + (getTaskSuccessRatio() * 100) + "%)");
        System.out.println("# of Failed Tasks: " + getFailedTaskCount());
        System.out.println("# of IO Connection Error: "
                + getTaskCount(ResultType.ConnectionError));
        System.out.println("# of HTTP Error: "
                + getTaskCount(ResultType.HttpError));
        System.out.println("# of Application Error: "
                + getTaskCount(ResultType.ApplicationException));
        System.out.print("Response Time - Total: " + getTotalResponseTime());
        System.out.println("\tAverage: " + getAverageResponseTime());
        System.out.print("SucceededResponse Time - Total: "
                + getTotalSucceededResponseTime());
        System.out.println("\tAverage: " + getAverageSucceededResponseTime());

        // print out specific types results
        String[] types = { StressTester.TYPE_HOME_URL,
                StressTester.TYPE_QUESTION_URL,
                StressTester.TYPE_XML_QUESTION_URL,
                StressTester.TYPE_RECORD_URL };
        for (String type : types) {
            System.out.println();
            System.out.println("------------------ Type: " + type
                    + " --------------------");
            System.out.println("# of Total Tasks: " + getTaskCount(type));
            System.out.print("# of Succeeded Tasks: "
                    + getSucceededTaskCount(type));
            System.out.println("\t (" + (getTaskSuccessRatio(type) * 100)
                    + "%)");
            System.out.println("# of Failed Tasks: " + getFailedTaskCount(type));
            System.out.println("# of Connection Error: "
                    + getTaskCount(ResultType.ConnectionError, type));
            System.out.println("# of HTTP Error: "
                    + getTaskCount(ResultType.HttpError, type));
            System.out.println("# of Application Error: "
                    + getTaskCount(ResultType.ApplicationException, type));
            System.out.print("Response Time - Total: "
                    + getTotalResponseTime(type));
            System.out.println("\tAverage: " + getAverageResponseTime(type));
            System.out.print("SucceededResponse Time - Total: "
                    + getTotalSucceededResponseTime(type));
            System.out.println("\tAverage: "
                    + getAverageSucceededResponseTime(type));
        }
    }

    private static Options declareOptions() {
        String[] names = { "model", "tag" };
        String[] descs = {
                "the name of the model.  This is used to find the Model XML "
                        + "file ($GUS_HOME/config/model_name.xml) the Model "
                        + "property file ($GUS_HOME/config/model_name.prop) "
                        + "and the Model config file "
                        + "($GUS_HOME/config/model_name-config.xml)",
                "The test tag of the stress test to be analyzed." };
        boolean[] required = { true, true };
        int[] args = { 0, 0 };

        return CommandHelper.declareOptions(names, descs, required, args);
    }

    public static void main(String[] args) throws Exception {
        String cmdName = System.getProperty("cmdName");

        // process args
        Options options = declareOptions();
        CommandLine cmdLine = CommandHelper.parseOptions(cmdName, options, args);

        String modelName = cmdLine.getOptionValue("model");
        String testTagStr = cmdLine.getOptionValue("tag");
        long testTag = Long.parseLong(testTagStr);

        // load WdkModel
        String gusHome = System.getProperty(Utilities.SYSTEM_PROPERTY_GUS_HOME);
        try (WdkModel wdkModel = WdkModel.construct(modelName, gusHome)) {
          // create tester
          logger.info("Initializing stress test analyzer on " + modelName + " with test_tag=" + testTag);
          StressTestAnalyzer analyzer = new StressTestAnalyzer(testTag, wdkModel);
          // run tester
          analyzer.print();
        }
    }
}
