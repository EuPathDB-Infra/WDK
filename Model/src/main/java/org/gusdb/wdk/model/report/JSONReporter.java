/**
 * 
 */
package org.gusdb.wdk.model.report;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.db.QueryLogger;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.db.platform.DBPlatform;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.answer.AnswerValue;
import org.gusdb.wdk.model.record.Field;
import org.gusdb.wdk.model.record.FieldScope;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.model.record.RecordInstance;
import org.gusdb.wdk.model.record.TableField;
import org.gusdb.wdk.model.record.TableValue;
import org.gusdb.wdk.model.record.attribute.AttributeField;
import org.gusdb.wdk.model.record.attribute.AttributeValue;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

/**
 * @author Cary P.
 * 
 */
public class JSONReporter extends Reporter {

    private static Logger logger = Logger.getLogger(JSONReporter.class);

    public static final String PROPERTY_TABLE_CACHE = "table_cache";
    public static final String PROPERTY_RECORD_ID_COLUMN = "record_id_column";

    public static final String FIELD_SELECTED_COLUMNS = "o-fields";
    public static final String TABLE_SELECTED_COLUMNS = "o-tables";
    public static final String FIELD_HAS_EMPTY_TABLE = "hasEmptyTable";

    private String tableCache;
    private String recordIdColumn;

    @SuppressWarnings("unused")
    private boolean hasEmptyTable = false;

    private String sqlInsert;
    private String sqlQuery;

    public JSONReporter(AnswerValue answerValue, int startIndex, int endIndex) {
        super(answerValue, startIndex, endIndex);
    }

    /**
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.report.Reporter#setProperties(java.util.Map)
     */
    @Override
    public void setProperties(Map<String, String> properties) throws WdkModelException {
        super.setProperties(properties);

        tableCache = properties.get(PROPERTY_TABLE_CACHE);

        // check required properties
        recordIdColumn = properties.get(PROPERTY_RECORD_ID_COLUMN);
        logger.info(" tableCache:" + tableCache + "recordIdColumn: "
                + recordIdColumn);
        if (tableCache != null && recordIdColumn == null)
            throw new WdkModelException("The required property for reporter "
                    + this.getClass().getName() + ", "
                    + PROPERTY_RECORD_ID_COLUMN + ", is missing");
    }

    @Override
    public void configure(Map<String, String> config) {
        super.configure(config);

        // get basic configurations
        if (config.containsKey(FIELD_HAS_EMPTY_TABLE)) {
            String value = config.get(FIELD_HAS_EMPTY_TABLE);
            hasEmptyTable = value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true");
        }
    }

    @Override
    public String getConfigInfo() {
        return "This reporter does not have config info yet.";
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.report.Reporter#getHttpContentType()
     */
    @Override
    public String getHttpContentType() {
      return "application/json";
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.report.Reporter#getDownloadFileName()
     */
    @Override
    public String getDownloadFileName() {
        logger.info("Internal format: " + format);
        String name = getQuestion().getName();
        if (format.equalsIgnoreCase("text")) {
            return name + "_detail.txt";
        } else if (format.equalsIgnoreCase("pdf")) {
            return name + "_detail.pdf";
        } else { // use the default file name defined in the parent
            return super.getDownloadFileName();
        }
    }

    // FIXME Use JSON library to create JSON
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.report.IReporter#format(org.gusdb.wdk.model.Answer)
     */
    @Override
    protected void write(OutputStream out) throws WdkModelException,
            SQLException, NoSuchAlgorithmException, JSONException,
            WdkUserException {
        OutputStreamWriter streamWriter = new OutputStreamWriter(out);
        JSONWriter writer = new JSONWriter(streamWriter);

        // get the columns that will be in the report
        Set<Field> fields = validateColumns();

        Set<AttributeField> attributes = new LinkedHashSet<AttributeField>();
        Set<TableField> tables = new LinkedHashSet<TableField>();
        for (Field field : fields) {
            if (field instanceof AttributeField) {
                attributes.add((AttributeField) field);
            } else if (field instanceof TableField) {
                tables.add((TableField) field);
            }
        }

        // get the formatted result
        WdkModel wdkModel = getQuestion().getWdkModel();
        RecordClass recordClass = getQuestion().getRecordClass();
        String[] pkColumns = recordClass.getPrimaryKeyAttributeField().getColumnRefs();

        // construct the insert sql
        StringBuffer sqlInsert = new StringBuffer("INSERT INTO ");
        sqlInsert.append(tableCache).append(" (wdk_table_id, ");
        for (String column : pkColumns) {
            sqlInsert.append(column).append(", ");
        }
        sqlInsert.append(" table_name, row_count, content) VALUES (");
        sqlInsert.append(wdkModel.getUserDb().getPlatform()
            .getNextIdSqlExpression("apidb", "wdkTable"));
        sqlInsert.append(", ");
        for (int i = 0; i < pkColumns.length; i++) {
            sqlInsert.append("?, ");
        }
        sqlInsert.append("?, ?, ?)");

        // construct the query sql
        StringBuffer sqlQuery = new StringBuffer("SELECT ");
        sqlQuery.append("count(*) AS cache_count FROM ").append(tableCache);
        sqlQuery.append(" WHERE ");
        for (String column : pkColumns) {
            sqlQuery.append(column).append(" = ? AND ");
        }
        sqlQuery.append(" table_name = ?");

        this.sqlInsert = sqlInsert.toString();
        this.sqlQuery = sqlQuery.toString();
        PreparedStatement psInsert = null;
        PreparedStatement psQuery = null;
        try {
            if (tableCache != null) {
                // want to cache the table content
                DataSource dataSource = wdkModel.getAppDb().getDataSource();
                psInsert = SqlUtils.getPreparedStatement(dataSource,
                        sqlInsert.toString());
                psQuery = SqlUtils.getPreparedStatement(dataSource,
                        sqlQuery.toString());
            }
            int recordCount = 0;
            AnswerValue av = this.getAnswerValue();
            // get page based answers with a maximum size (defined in
            // PageAnswerIterator)
            writer
              .object()
                .key("response")
                .object()
                  .key("recordset")
                  .object()
                    .key("id")
                    .value(av.getChecksum())
                    .key("count")
                    .value(this.getResultSize())
                    .key("type")
                    .value(av.getQuestion().getRecordClass().getDisplayName())
                    .key("records")
                    .array();

            for (AnswerValue pageAnswer : this) {
                for (RecordInstance record : pageAnswer.getRecordInstances()) {
                    writer
                      .object()
                        .key("id")
                        .value(record.getPrimaryKey());

                    // print out attributes of the record first
                    formatAttributes(record, attributes, writer);
                    // print out tables
                    formatTables(record, tables, writer, pageAnswer, psInsert,
                            psQuery);
                    // writer.flush();
                    // count the records processed so far
                    recordCount++;
                    writer.endObject();
                    streamWriter.flush();
                }
            }

            writer
                    .endArray() // records
                  .endObject()
                .endObject()
              .endObject();
            streamWriter.flush();
            streamWriter.close();
            logger.info("Totally " + recordCount + " records dumped");
        }
        catch (IOException ioe) {
            logger.error(ioe);
        }
        finally {
            SqlUtils.closeStatement(psQuery);
            SqlUtils.closeStatement(psInsert);
        }
    }

    private Set<Field> validateColumns() throws WdkModelException {
        // get a map of report maker fields
        Map<String, Field> fieldMap = getQuestion().getFields(
                FieldScope.REPORT_MAKER);

        // the config map contains a list of column names;
        Set<Field> columns = new LinkedHashSet<Field>();

        String fieldsList = config.get(FIELD_SELECTED_COLUMNS);
        String tablesList = config.get(TABLE_SELECTED_COLUMNS);
        if (fieldsList == null) fieldsList = "none";
        if (tablesList == null) tablesList = "none";
        logger.info("fieldsList = " + fieldsList + "    tablesList = " + tablesList);
        if (fieldsList.equals("all") && tablesList.equals("all")) {
            logger.info("all all");
            columns.addAll(fieldMap.values());
        } else {
            if (fieldsList.equals("all")) {
                logger.info("FIELDSLIST ALL");
                for (String k : fieldMap.keySet()) {
                    Field f = fieldMap.get(k);
                    if (f.getClass().getName().contains("AttributeField"))
                        columns.add(f);
                }
            } else if (!fieldsList.equals("none")) {
                String[] fields = fieldsList.split(",");
                for (String column : fields) {
                    column = column.trim();
                    if (fieldMap.containsKey(column)) {
                        columns.add(fieldMap.get(column));
                    }
                }
            }
            if (tablesList.equals("all")) {
                for (String k : fieldMap.keySet()) {
                    Field f = fieldMap.get(k);
                    if (f.getClass().getName().contains("TableField"))
                        columns.add(f);
                }
            } else if (!tablesList.equals("none")) {
                String[] tables = tablesList.split(",");
                for (String column : tables) {
                    column = column.trim();
                    if (!fieldMap.containsKey(column))
                        throw new WdkModelException("The column '" + column
                                + "' cannot be included in the report");
                    columns.add(fieldMap.get(column));
                }
            }
        }
        logger.info(columns.size());
        return columns;
    }

    private void formatAttributes(RecordInstance record,
            Set<AttributeField> attributes, JSONWriter writer)
            throws WdkModelException, WdkUserException {
        if (attributes.size() > 0) {
            writer.key("fields").array();
            for (AttributeField field : attributes) {
                AttributeValue value = record.getAttributeValue(field.getName());
                writer.object()
                    .key("name")
                    .value(field.getName())
                    .key("value")
                    .value(value)
                .endObject();
            }
            writer.endArray();
        }
    }

    private void formatTables(RecordInstance record, Set<TableField> tables,
            JSONWriter writer, AnswerValue answerValue,
            PreparedStatement psInsert, PreparedStatement psQuery)
            throws WdkModelException, SQLException, WdkUserException {
        DBPlatform platform = getQuestion().getWdkModel().getAppDb().getPlatform();
        RecordClass recordClass = record.getRecordClass();
        String[] pkColumns = recordClass.getPrimaryKeyAttributeField().getColumnRefs();

        // Add the following to the writer:
        //
        // { tables: [
        //   { name: String, rows: [
        //     { fields: [
        //       { name: String, value: String },
        //       ...
        //     ]}
        //   ]}
        // ]};

        JSONArray tablesArray = new JSONArray();

        // print out tables of the record
        boolean needUpdate = false;
        for (TableField table : tables) {
            // we will be writing this object to the cache
            JSONObject tableObject = new JSONObject()
              .put("name", table.getDisplayName());
            JSONArray rowsArray = new JSONArray();
            TableValue tableValue = record.getTableValue(table.getName());
            AttributeField[] fields = table.getAttributeFields(FieldScope.REPORT_MAKER);

            // output table header
            int tableSize = 0;
            for (Map<String, AttributeValue> row : tableValue) {
                JSONObject rowObject = new JSONObject();
                JSONArray fieldsArray = new JSONArray();
                for (AttributeField field : fields) {
                    String fieldName = field.getName();
                    AttributeValue value = row.get(fieldName);
                    JSONObject fieldObject = new JSONObject()
                      .put("name", fieldName)
                      .put("value", value.getValue());
                    fieldsArray.put(fieldObject);
                }
                rowObject.put("fields", fieldsArray);
                rowsArray.put(rowObject);
            }
            tableObject.put("rows", rowsArray);
            tablesArray.put(tableObject);

            String content = tableObject.toString();
            // check if the record has been cached
            if (tableCache != null) {
                Map<String, String> pkValues = record.getPrimaryKey().getValues();
                long start = System.currentTimeMillis();
                for (int index = 1; index <= pkColumns.length; index++) {
                    Object value = pkValues.get(pkColumns[index - 1]);
                    psQuery.setObject(index, value);
                }
                psQuery.setString(pkColumns.length + 1, table.getName());
                ResultSet rs = psQuery.executeQuery();
                QueryLogger.logEndStatementExecution(sqlQuery,
                        "wdk-report-json-select-count", start);
                rs.next();
                int count = rs.getInt("cache_count");
                if (count == 0) {
                    // insert into table cache
                    int index;
                    for (index = 1; index <= pkColumns.length; index++) {
                        Object value = pkValues.get(pkColumns[index - 1]);
                        psInsert.setObject(index, value);
                    }
                    psInsert.setString(index++, table.getName());
                    psInsert.setInt(index++, tableSize);
                    platform.setClobData(psInsert, index++, content, false);
                    psInsert.addBatch();
                    needUpdate = true;
                }
                SqlUtils.closeResultSetOnly(rs);
            }
        }

        // write to the stream
        writer
            .key("tables")
            .value(tablesArray);

        if (tableCache != null && needUpdate) {
            long start = System.currentTimeMillis();
            psInsert.executeBatch();
            QueryLogger.logEndStatementExecution(sqlInsert, "wdk-report-json-insert",
                    start);
        }
    }

    @Override
    protected void complete() {
        // do nothing
    }

    @Override
    protected void initialize() throws WdkModelException {
        // do nothing
    }
}
