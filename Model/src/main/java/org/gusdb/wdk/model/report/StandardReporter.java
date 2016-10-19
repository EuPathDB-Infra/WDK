package org.gusdb.wdk.model.report;

import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.FormatUtil.Style;
import org.gusdb.fgputil.Tuples.TwoTuple;
import org.gusdb.fgputil.functional.FunctionalInterfaces.Function;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.answer.AnswerValue;
import org.gusdb.wdk.model.answer.stream.FileBasedRecordStream;
import org.gusdb.wdk.model.answer.stream.PagedAnswerRecordStream;
import org.gusdb.wdk.model.answer.stream.RecordStream;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.record.Field;
import org.gusdb.wdk.model.record.FieldScope;
import org.gusdb.wdk.model.record.RecordInstance;
import org.gusdb.wdk.model.record.TableField;
import org.gusdb.wdk.model.record.TableValue;
import org.gusdb.wdk.model.record.attribute.AttributeField;
import org.json.JSONObject;

/**
 * A reporter whose configuration is standard, i.e. a list of attributes and tables
 * 
 * @author steve
 */
public abstract class StandardReporter extends AbstractReporter {

  private static final Logger LOG = Logger.getLogger(StandardReporter.class);

  private StandardConfig _standardConfig;
  private Set<AttributeField> _attributes;
  private Set<TableField> _tables;

  protected StandardReporter(AnswerValue answerValue) {
    super(answerValue);
  }

  @Override
  public void configure(Map<String, String> config) throws WdkUserException {
    LOG.info(getClass().getName() + " instantiated and configured with: " +
        FormatUtil.prettyPrint(config, Style.MULTI_LINE));
    _standardConfig = new StandardConfig(getQuestion()).configure(config);
    loadValidatedFields();
  }

  @Override
  public void configure(JSONObject config) throws WdkUserException {
    LOG.info(getClass().getName() + " instantiated and configured with: " + config.toString(2));
    _standardConfig = new StandardConfig(getQuestion()).configure(config);
    loadValidatedFields();
  }

  protected StandardConfig getStandardConfig() {
    return _standardConfig;
  }

  protected Set<AttributeField> getSelectedAttributes() {
    return _attributes;
  }

  protected Set<TableField> getSelectedTables() {
    return _tables;
  }

  private static String PROPERTY_PAGE_SIZE = "page_size";
  private static final int DEFAULT_PAGE_SIZE = 100;
  protected RecordStream getRecords() throws WdkModelException {
    switch(_standardConfig.getStreamStrategy()) {
      case PAGED_ANSWER:
        int pageSize = DEFAULT_PAGE_SIZE;
        if (_properties.containsKey(PROPERTY_PAGE_SIZE)) {
          try {
            pageSize = Integer.valueOf(_properties.get(PROPERTY_PAGE_SIZE));
          }
          catch (NumberFormatException e) {
            throw new WdkModelException("Reporter property '" + PROPERTY_PAGE_SIZE + "' must be a positive integer.");
          }
        }
        return new PagedAnswerRecordStream(_baseAnswer, pageSize);
      case FILE_BASED:
      default:
        return new FileBasedRecordStream(_baseAnswer, getSelectedAttributes(), getSelectedTables()).populateFiles();
    }
  }

  private void loadValidatedFields() throws WdkUserException {

    // get the columns that will be in the report
    Set<Field> fields = validateColumns(getQuestion(), _standardConfig);

    // divide fields into attributes and tables
    _attributes = new LinkedHashSet<AttributeField>();
    _tables = new LinkedHashSet<TableField>();
    for (Field field : fields) {
      if (field instanceof AttributeField) {
        _attributes.add((AttributeField) field);
      }
      else if (field instanceof TableField) {
        _tables.add((TableField) field);
      }
    }
  }

  /**
   * @return set of validated fields
   * @throws WdkModelException if an error occurs while validating
   * @throws WdkUserException if column inputs are invalid
   */
  private static Set<Field> validateColumns(Question question, StandardConfig stdConfig) throws WdkUserException {
    // get a map of report maker fields
    Map<String, Field> fieldMap = question.getFields(FieldScope.ALL);
    // the config map contains a list of column names;
    Set<Field> columns = new LinkedHashSet<Field>();
    if (stdConfig.getIsAllFields()) {
      columns.addAll(fieldMap.values());
    }
    else {
      Set<AttributeField> attrColumns = validateAttributeColumns(question, stdConfig);
      columns.addAll(attrColumns);
      
      if (stdConfig.getIsAllTables()) {
        for (String k : fieldMap.keySet()) {
          Field f = fieldMap.get(k);
          // if this is a table field
          if (TableField.class.isAssignableFrom(f.getClass()))
            columns.add(f);
        }
      }
      else {
        for (String table : stdConfig.getTables()) {
          table = table.trim();
          if (!fieldMap.containsKey(table))
            throw new WdkUserException("The table '" + table + "' is requested for the report, but is not available for this type of record");
          columns.add(fieldMap.get(table));
        }
      }
    }
    return columns;
  }

  /**
   * Find the requested fields that are Attribute columns and add to provided set
   * 
   * @param fieldMap the requested fields
   * @param columns add the attribute columns here
   * @throws WdkUserException 
   */
  private static Set<AttributeField> validateAttributeColumns(Question question, StandardConfig stdConfig) throws WdkUserException {
    Map<String, AttributeField> fieldMap = question.getAttributeFieldMap();
    Set<AttributeField> columns = new LinkedHashSet<AttributeField>();
    
    if (stdConfig.getIsAllAttributes()) {
      for (String k : fieldMap.keySet()) {
        Field f = fieldMap.get(k);
        // if this is an attribute field
        if (AttributeField.class.isAssignableFrom(f.getClass()))
          columns.add((AttributeField)f);
      }
    }
    else {
      for (String column : stdConfig.getAttributes()) {
        column = column.trim();
        if (fieldMap.containsKey(column)) {
          columns.add(fieldMap.get(column));
        } else throw new WdkUserException("Column '" + column + "' is requested for the report, but is not available for this type of record");
      }
    }
    return columns;
  }

  protected static void formatTables(RecordInstance record, Set<TableField> tables,
      boolean includeEmptyTables, PrintWriter writer, TableCache tableCache,
      Function<TableValue, TwoTuple<Integer,String>> formatTableFunction)
          throws WdkModelException, SQLException, WdkUserException {

    // print out tables of the record
    for (TableField table : tables) {

      TwoTuple<Integer,String> tableData;

      // if not caching then simply format and return
      if (tableCache == null) {
        tableData = formatTableFunction.apply(record.getTableValue(table.getName()));
      }
      else {
        // check if the record has been cached
        tableData = tableCache.getCachedTableValue(record, table.getName());
        if (tableData == null) {
          tableData = formatTableFunction.apply(record.getTableValue(table.getName()));
          tableCache.insertTableValue(record, table.getName(), tableData);
        }
      }

      // write to the stream
      if (includeEmptyTables || tableData.getFirst() > 0) {
        writer.println(tableData.getSecond());
        writer.flush();
      }
    }

    // commit batch of all tables for each record instance
    if (tableCache != null) {
      tableCache.flushBatch();
    }
  }
}
