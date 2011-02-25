package org.gusdb.wdk.model.fix;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.dbms.DBPlatform;
import org.gusdb.wdk.model.dbms.SqlUtils;
import org.gusdb.wsf.util.BaseCLI;

public class BackupUser extends BaseCLI {

    private static final String ARG_BACKUP_SCHEMA = "backupSchema";
    private static final String ARG_CUTOFF_DATE = "cutoffDate";

    private static final Logger logger = Logger.getLogger(BackupUser.class);

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        String cmdName = System.getProperty("cmdName");
        BackupUser backup = new BackupUser(cmdName);
        try {
            backup.invoke(args);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            logger.info("WDK User Backup done.");
            System.exit(0);
        }
    }

    private String[] userColumns = { "user_id", "email", "passwd", "is_guest",
            "signature", "register_time", "last_active", "last_name",
            "first_name", "middle_name", "title", "organization", "department",
            "address", "city", "state", "zip_code", "phone_number", "country",
            "prev_user_id" };
    private String[] roleColumns = { "user_id", "user_role" };
    private String[] preferenceColumns = { "user_id", "project_id",
            "preference_name", "preference_value" };
    private String[] stepColumns = { "step_id", "display_id", "user_id",
            "answer_id", "left_child_id", "right_child_id", "create_time",
            "last_run_time", "estimate_size", "answer_filter", "custom_name",
            "is_deleted", "is_valid", "collapsed_name", "is_collapsible",
            "display_params", "prev_step_id", "invalid_message",
            "assigned_weight" };
    private String[] strategyColumns = { "strategy_id", "display_id",
            "user_id", "root_step_id", "project_id", "is_saved", "create_time",
            "last_view_time", "last_modify_time", "description", "signature",
            "name", "saved_name", "is_deleted", "prev_strategy_id" };
    private String[] datasetColumns = { "user_dataset_id", "dataset_id",
            "user_id", "create_time", "upload_file", "prev_user_dataset_id" };
    private String[] basketColumns = { "user_id", "project_id", "record_class",
            "pk_column_1", "pk_column_2", "pk_column_3" };
    private String[] favoriteColumns = { "user_id", "project_id",
            "record_class", "pk_column_1", "pk_column_2", "pk_column_3",
            "record_note", "record_group" };
    private String[] datasetIndexColumns = { "dataset_id", "dataset_checksum",
            "record_class", "summary", "dataset_size", "PREV_DATASET_ID" };
    private String[] datasetValueColumns = { "dataset_id", "pk_column_1",
            "pk_column_2", "pk_column_3" };
    private String[] answerColumns = { "answer_id", "answer_checksum",
            "project_id", "project_version", "question_name", "query_checksum",
            "old_query_checksum", "params", "result_message", "prev_answer_id" };

    private WdkModel wdkModel;
    private String userSchema;
    private String wdkSchema;
    private String backupSchema;

    public BackupUser(String command) {
        super((command == null) ? command : "wdkBackupUser", "This command "
                + "backs up expired guest user data to a given schema.");
    }

    @Override
    protected void declareOptions() {
        addSingleValueOption(ARG_PROJECT_ID, true, null, "a ProjectId"
                + ", which should match the directory name under $GUS_HOME, "
                + "where model-config.xml is stored.");

        addSingleValueOption(ARG_BACKUP_SCHEMA, true, null, "the backup schema"
                + " where the data should be stored.");

        addSingleValueOption(ARG_CUTOFF_DATE, true, null, "Any guest user "
                + "created by this date will be backed up, and removed "
                + "from the live schema defined in the model-config.xml. "
                + "The data should be in this format: yyyy/mm/dd");
    }

    @Override
    protected void execute() throws Exception {
        String gusHome = System.getProperty(Utilities.SYSTEM_PROPERTY_GUS_HOME);

        backupSchema = (String) getOptionValue(ARG_BACKUP_SCHEMA);

        String projectId = (String) getOptionValue(ARG_PROJECT_ID);
        String cutoffDate = (String) getOptionValue(ARG_CUTOFF_DATE);
        logger.info("Backing up guest user data... ");

        wdkModel = WdkModel.construct(projectId, gusHome);
        userSchema = wdkModel.getModelConfig().getUserDB().getUserSchema();
        wdkSchema = wdkModel.getModelConfig().getUserDB().getWdkEngineSchema();

        backupSchema = DBPlatform.normalizeSchema(backupSchema);
        userSchema = DBPlatform.normalizeSchema(userSchema);
        wdkSchema = DBPlatform.normalizeSchema(wdkSchema);

        backupGuestUsers(userSchema, wdkSchema, backupSchema, cutoffDate);
    }

    public void backupGuestUsers(String userSchema, String wdkSchema,
            String backupSchema, String cutoffDate) throws WdkUserException,
            WdkModelException, SQLException {

        String filterClause = "user_id IN (SELECT user_id FROM " + userSchema
                + "users WHERE is_guest = 1 AND register_time < to_date('"
                + cutoffDate + "', 'yyyy/mm/dd')"
                + " MINUS SELECT user_id FROM " + backupSchema + "users)";

        // copy tables from user schema
        copyUserRows(filterClause, "users", userColumns);
        copyUserRows(filterClause, "user_roles", roleColumns);
        copyUserRows(filterClause, "preferences", preferenceColumns);
        copyUserRows(filterClause, "user_baskets", basketColumns);
        copyUserRows(filterClause, "favorites", favoriteColumns);
        copyUserRows(filterClause, "user_datasets2", datasetColumns);
        copyUserRows(filterClause, "steps", stepColumns);
        copyUserRows(filterClause, "strategies", strategyColumns);

        // delete rows from user schema
        deleteRows(filterClause, "strategies");
        deleteRows(filterClause, "steps");
        deleteRows(filterClause, "user_datasets2");
        deleteRows(filterClause, "favorites");
        deleteRows(filterClause, "user_baskets");
        deleteRows(filterClause, "preferences");
        deleteRows(filterClause, "user_roles");
        deleteRows(filterClause, "users");

        // copy other data
        copyAnswerRows();
        deleteAnswerRows();

        copyDatasetIndexRows();
        copyDatasetValueRows();
        deleteDatasetValueRows();
        deleteDatasetIndexRows();
    }

    private void copyUserRows(String filterClause, String tableName,
            String[] columns) throws WdkUserException, WdkModelException,
            SQLException {
        logger.debug("Copying from " + tableName + "...");

        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String column : columns) {
            if (first) first = false;
            else builder.append(", ");
            builder.append(column);
        }

        String sql = "INSERT INTO " + backupSchema + tableName + " (" + builder
                + ") SELECT " + builder + " FROM " + userSchema + tableName
                + " WHERE " + filterClause;

        DataSource dataSource = wdkModel.getUserPlatform().getDataSource();
        SqlUtils.executeUpdate(wdkModel, dataSource, sql, "wdk-backup-insert-"
                + tableName);
    }

    private void deleteRows(String filterClause, String tableName)
            throws WdkUserException, WdkModelException, SQLException {
        logger.debug("deleting from " + tableName + "...");
        String schema = wdkModel.getModelConfig().getUserDB().getUserSchema();

        String sql = "DELETE FROM " + schema + tableName + " WHERE "
                + filterClause;

        DataSource dataSource = wdkModel.getUserPlatform().getDataSource();
        SqlUtils.executeUpdate(wdkModel, dataSource, sql, "wdk-backup-delete-"
                + tableName);
    }

    /**
     * copy answers that are not used by steps
     * 
     * @throws SQLException
     * @throws WdkModelException
     * @throws WdkUserException
     */
    private void copyAnswerRows() throws WdkUserException, WdkModelException,
            SQLException {
        logger.debug("copying answer rows...");

        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String column : answerColumns) {
            if (first) first = false;
            else builder.append(", ");
            builder.append(column);
        }

        String sql = "INSERT INTO " + backupSchema + "answers (" + builder
                + ") SELECT " + builder + " FROM " + wdkSchema + "answers "
                + "  WHERE answer_id NOT IN ("
                + "        SELECT answer_id FROM " + userSchema + "steps"
                + "        UNION                        "
                + "        SELECT answer_id FROM " + backupSchema + "answers)";
        DataSource dataSource = wdkModel.getUserPlatform().getDataSource();
        SqlUtils.executeUpdate(wdkModel, dataSource, sql,
                "wdk-backup-delete-answers");
    }

    private void copyDatasetIndexRows() throws WdkUserException,
            WdkModelException, SQLException {
        logger.debug("copying dataset index rows...");

        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String column : datasetIndexColumns) {
            if (first) first = false;
            else builder.append(", ");
            builder.append(column);
        }

        String sql = "INSERT INTO " + backupSchema + "dataset_indices  "
                + "  (" + builder + ")                              "
                + " SELECT                                " + builder
                + " FROM " + wdkSchema + "dataset_indices "
                + " WHERE dataset_id NOT IN (   "
                + "   SELECT dataset_id FROM " + userSchema + "user_datasets2"
                + "   UNION                      "
                + "   SELECT dataset_id FROM " + backupSchema
                + "dataset_indices)";
        DataSource dataSource = wdkModel.getUserPlatform().getDataSource();
        SqlUtils.executeUpdate(wdkModel, dataSource, sql,
                "wdk-backup-delete-dataset-indices");
    }

    private void copyDatasetValueRows() throws WdkUserException,
            WdkModelException, SQLException {
        logger.debug("copying dataset value rows...");

        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String column : datasetValueColumns) {
            if (first) first = false;
            else builder.append(", ");
            builder.append(column);
        }

        String sql = "INSERT INTO " + backupSchema + "dataset_values  " + "  ("
                + builder + ")                              "
                + " SELECT DISTINCT                       " + builder
                + " FROM " + wdkSchema + "dataset_values "
                + " WHERE dataset_id NOT IN (   "
                + "   SELECT dataset_id FROM " + userSchema + "user_datasets2"
                + "   UNION                      "
                + "   SELECT dataset_id FROM " + backupSchema
                + "dataset_indices)";
        DataSource dataSource = wdkModel.getUserPlatform().getDataSource();
        SqlUtils.executeUpdate(wdkModel, dataSource, sql,
                "wdk-backup-delete-dataset-values");

    }

    private void deleteAnswerRows() throws WdkUserException, WdkModelException,
            SQLException {
        logger.debug("deleting answer rows...");

        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String column : answerColumns) {
            if (first) first = false;
            else builder.append(", ");
            builder.append(column);
        }

        String sql = "DELETE FROM " + wdkSchema + "answers "
                + "   WHERE answer_id NOT IN ("
                + "         SELECT answer_id FROM " + userSchema + "steps "
                + "         UNION                        "
                + "         SELECT answer_id FROM wdkuser.steps)";
        DataSource dataSource = wdkModel.getUserPlatform().getDataSource();
        SqlUtils.executeUpdate(wdkModel, dataSource, sql,
                "wdk-backup-delete-answers");
    }

    private void deleteDatasetIndexRows() throws WdkUserException,
            WdkModelException, SQLException {
        logger.debug("deleting dataset index rows...");

        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String column : answerColumns) {
            if (first) first = false;
            else builder.append(", ");
            builder.append(column);
        }

        String sql = "DELETE FROM " + wdkSchema + "dataset_indices "
                + "   WHERE dataset_id NOT IN ( "
                + "   SELECT dataset_id FROM " + userSchema + "user_datasets2 "
                + "   UNION                           "
                + "   SELECT dataset_id FROM wdkuser.user_datasets)";
        DataSource dataSource = wdkModel.getUserPlatform().getDataSource();
        SqlUtils.executeUpdate(wdkModel, dataSource, sql,
                "wdk-backup-delete-dataset-indices");
    }

    private void deleteDatasetValueRows() throws WdkUserException,
            WdkModelException, SQLException {
        logger.debug("deleting dataset value rows...");

        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String column : answerColumns) {
            if (first) first = false;
            else builder.append(", ");
            builder.append(column);
        }

        String sql = "DELETE FROM " + wdkSchema + "dataset_values "
                + "   WHERE dataset_id NOT IN ( "
                + "   SELECT dataset_id FROM " + userSchema + "user_datasets2 "
                + "   UNION                           "
                + "   SELECT dataset_id FROM wdkuser.user_datasets)";
        DataSource dataSource = wdkModel.getUserPlatform().getDataSource();
        SqlUtils.executeUpdate(wdkModel, dataSource, sql,
                "wdk-backup-delete-dataset-values");

    }
}
