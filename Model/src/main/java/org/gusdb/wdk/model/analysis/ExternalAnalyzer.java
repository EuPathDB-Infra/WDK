package org.gusdb.wdk.model.analysis;

import static org.gusdb.fgputil.FormatUtil.NL;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.MapBuilder;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.answer.AnswerValue;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.record.Field;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.model.record.TableField;
import org.gusdb.wdk.model.record.attribute.AttributeField;
import org.gusdb.wdk.model.report.AbstractTabularReporter;
import org.gusdb.wdk.model.report.AttributesTabularReporter;
import org.gusdb.wdk.model.report.Reporter;
import org.gusdb.wdk.model.report.StandardConfig;
import org.gusdb.wdk.model.report.TableTabularReporter;
import org.gusdb.wdk.model.user.analysis.ExecutionStatus;
import org.gusdb.wdk.model.user.analysis.StatusLogger;
import org.json.JSONException;

/**
 * This analyzer dumps a pre-configured set of result attributes to a tab-
 * delimited file, where they may be accessed by an "external" (though currently
 * only on the same server) application or web app for analysis.
 * 
 * @author rdoherty
 */
public class ExternalAnalyzer extends AbstractStepAnalyzer {

  private static final Logger LOG = Logger.getLogger(ExternalAnalyzer.class);

  // this plugin can only be assigned to questions/recordClasses that
  //   are configured with a tabular reporter
  protected static final String TABULAR_REPORTER_NAME = "tabular";
  protected static final String TABLE_TABULAR_REPORTER_NAME = "tableTabular";

  // properties determining what data to save into plugin output directory
  protected static final String DUMP_MODEL_PROPS_PROP_KEY = "dumpModelProps";
  protected static final String EXTRACTED_ATTRIBS_PROP_KEY = "attributesToExtract";
  protected static final String EXTRACTED_TABLES_PROP_KEY = "tablesToExtract";
  protected static final String ADD_HEADER_PROP_KEY = "addHeader";
  protected static final String DUMP_HEADER_DISPLAY_MAP_PROP_KEY = "dumpDisplayMap";

  // properties determining result view information
  protected static final String EXTERNAL_APP_URL_PROP_KEY = "externalAppUrl";
  protected static final String IFRAME_WIDTH_PROP_KEY = "iframeWidthPx";
  protected static final String IFRAME_LENGTH_PROP_KEY = "iframeLengthPx";

  // file naming constants
  protected static final String FILE_NAME_SUFFIX = ".tab";
  protected static final String ATTRIBUTES_FILE_NAME = "attributes" + FILE_NAME_SUFFIX;
  protected static final String HEADER_MAPPING_FILE_NAME = "header.mapping";
  protected static final String MODEL_PROPS_FILE_NAME = "model.prop";
  protected static final String MODEL_XML_PROPS_FILE_NAME = "modelXml.prop";
  protected static final String LAST_RUN_FILE_NAME = "last_run";

  protected static final int DEFAULT_IFRAME_WIDTH_PX = 900;
  protected static final int DEFAULT_IFRAME_HEIGHT_PX = 450;
  protected static final boolean ADD_HEADER_BY_DEFAULT = true;
  protected static final boolean DUMP_MODEL_PROPS_BY_DEFAULT = false;
  protected static final boolean DUMP_HEADER_DISPLAY_MAP_BY_DEFAULT = false;

  public static class ViewModel {

    private String _iframeBaseUrl;
    private final int _iframeWidth;
    private final int _iframeHeight;

    public ViewModel(String iframeBaseUrl, int width, int height) {
      _iframeBaseUrl = iframeBaseUrl;
      _iframeWidth = width;
      _iframeHeight = height;
    }

    public void setIframeBaseUrl(String url) { _iframeBaseUrl = url; }

    public String getIframeBaseUrl() { return _iframeBaseUrl; }
    public String getDownloadPath() { return ATTRIBUTES_FILE_NAME; }
    public int getIframeWidth() { return _iframeWidth; }
    public int getIframeHeight() { return _iframeHeight; }
  }

  @Override
  public void validateProperties() throws WdkModelException {
    checkPropertyExistence(EXTERNAL_APP_URL_PROP_KEY);
    checkAtLeastOneExists(EXTRACTED_ATTRIBS_PROP_KEY, EXTRACTED_TABLES_PROP_KEY);
    checkPositiveIntegerIfPresent(IFRAME_WIDTH_PROP_KEY);
    checkPositiveIntegerIfPresent(IFRAME_LENGTH_PROP_KEY);
    checkBooleanIfPresent(ADD_HEADER_PROP_KEY);
  }

  @Override
  public void validateQuestion(Question question) throws WdkModelException {
    // make sure this question has a tabular reporter; needed to dump results
    if (question.getRecordClass().getReporterMap().get(TABULAR_REPORTER_NAME) == null) {
      throw new WdkModelException("A RecordClass using the " + getClass().getSimpleName() +
          " must also be configured with a tabular reporter under the name '" +
          TABULAR_REPORTER_NAME + "'.");
    }
  }

  @Override
  public Object getResultViewModel() throws WdkModelException {
    return new ViewModel(
        getProperty(EXTERNAL_APP_URL_PROP_KEY),
        chooseSize(IFRAME_WIDTH_PROP_KEY, DEFAULT_IFRAME_WIDTH_PX),
        chooseSize(IFRAME_LENGTH_PROP_KEY, DEFAULT_IFRAME_HEIGHT_PX));
  }

  protected int chooseSize(String propName, int defaultValue) {
    String prop = getProperty(propName);
    return (prop != null && !prop.isEmpty()) ? Integer.parseInt(prop) : defaultValue;
  }

  @Override
  public ExecutionStatus runAnalysis(AnswerValue answerValue, StatusLogger log)
      throws WdkModelException, WdkUserException {

    // all files will be written to plugin instance's storage directory
    String storageDir = getStorageDirectory().toAbsolutePath().toString();
    writeContentToFile(storageDir, LAST_RUN_FILE_NAME,
        new SimpleDateFormat(FormatUtil.STANDARD_TIMESTAMP_FORMAT).format(new Date()) + NL);

    // if dumpModelProp is set to true, dump model properties to disk
    if (determineBooleanProperty(DUMP_MODEL_PROPS_PROP_KEY, DUMP_MODEL_PROPS_BY_DEFAULT)) {
      WdkModel wdkModel = getWdkModel();
      dumpModelProps(wdkModel, storageDir);
      dumpModelXmlProps(wdkModel, storageDir);
    }

    // decide if header should be added to table and attribute tab files
    boolean hasHeader = determineBooleanProperty(ADD_HEADER_PROP_KEY, ADD_HEADER_BY_DEFAULT);

    // if hasHeader and display value map requested, dump
    if (hasHeader && determineBooleanProperty(DUMP_HEADER_DISPLAY_MAP_PROP_KEY, DUMP_HEADER_DISPLAY_MAP_BY_DEFAULT)) {
      dumpHeaderDisplayMap(answerValue.getQuestion().getRecordClass(), storageDir);
    }

    // configure tabular reporter if attributes requested in config
    String attributes = getProperty(EXTRACTED_ATTRIBS_PROP_KEY);
    if (attributes != null && !attributes.trim().isEmpty()) {
      Reporter reporter = new AttributesTabularReporter(answerValue);
      reporter.configure(getConfig(StandardConfig.SELECTED_FIELDS, attributes.trim(), hasHeader));
      writeReport(reporter, Paths.get(storageDir, ATTRIBUTES_FILE_NAME));
    }

    // get array of requested tables
    String tablesStr = getProperty(EXTRACTED_TABLES_PROP_KEY);
    if (tablesStr != null && !tablesStr.trim().isEmpty()) {
      String[] tables = tablesStr.split(",");
      for (String table : tables) {
        Reporter reporter = new TableTabularReporter(answerValue);
        reporter.configure(getConfig(StandardConfig.SELECTED_TABLES, table, hasHeader));
        writeReport(reporter, Paths.get(storageDir, table + FILE_NAME_SUFFIX));
      }
    }

    return ExecutionStatus.COMPLETE;
  }

  protected static void writeContentToFile(String storageDir, String fileName, String content) throws WdkModelException {
    File file = Paths.get(storageDir, fileName).toFile();
    try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
      out.write(content);
    }
    catch (IOException e) {
      throw new WdkModelException("Could not write " + file.getAbsolutePath(), e);
    }
  }

  private void dumpHeaderDisplayMap(RecordClass recordClass, String storageDir) throws WdkModelException {
    String[] attributeNames = getProperty(EXTRACTED_ATTRIBS_PROP_KEY).trim().split(",");
    String[] tableNames = getProperty(EXTRACTED_TABLES_PROP_KEY).trim().split(",");
    File mappingOutFile = Paths.get(storageDir, HEADER_MAPPING_FILE_NAME).toFile();
    try (BufferedWriter out = new BufferedWriter(new FileWriter(mappingOutFile))) {
      writeField(out, recordClass.getIdAttributeField(), "");
      for (String attributeName : attributeNames) {
        AttributeField attr = recordClass.getAttributeFieldMap().get(attributeName);
        if (attr == null) {
          LOG.warn("Attribute '" + attributeName + "', specified in analysis plugin, is not valid for record class '" + recordClass.getFullName() + "'.");
          continue;
        }
        writeField(out, attr, "");
      }
      for (String tableName : tableNames) {
        TableField table = recordClass.getTableFieldMap().get(tableName);
        if (table == null) {
          LOG.warn("Table '" + tableName + "', specified in analysis plugin, is not valid for record class '" + recordClass.getFullName() + "'.");
          continue;
        }
        writeField(out, table, "");
        for (AttributeField attribute : table.getAttributeFields()) {
          writeField(out, attribute, table.getName() + ":");
        }
      }
    }
    catch (IOException e) {
      throw new WdkModelException("Unable to dump column header mapping to file.", e);
    }
  }

  private void writeField(BufferedWriter out, Field field, String keyPrefix) throws IOException {
    out.write(keyPrefix + field.getName() + "=" + field.getDisplayName());
    out.newLine();
  }

  private static void dumpModelProps(WdkModel wdkModel, String storageDir) throws WdkModelException {
    File propsOutFile = Paths.get(storageDir, MODEL_PROPS_FILE_NAME).toFile();
    try (BufferedWriter out = new BufferedWriter(new FileWriter(propsOutFile))) {
      for (Entry<String,String> prop : wdkModel.getProperties().entrySet()) {
        out.write(prop.getKey() + "=" + prop.getValue());
        out.newLine();
      }
    }
    catch (IOException e) {
      throw new WdkModelException("Unable to dump WDK Model properties to file.", e);
    }
  }

  private void dumpModelXmlProps(WdkModel wdkModel, String storageDir) throws WdkModelException {
    File propsOutFile = Paths.get(storageDir, MODEL_PROPS_FILE_NAME).toFile();
    try (BufferedWriter out = new BufferedWriter(new FileWriter(propsOutFile))) {
      out.write("gusHome=" + wdkModel.getGusHome() + NL);
      out.write("project=" + wdkModel.getProjectId() + NL);
      out.write("version=" + wdkModel.getVersion() + NL);
      out.write("buildNumber=" + wdkModel.getBuildNumber() + NL);
      out.write("releaseDate=" + wdkModel.getReleaseDate() + NL);
      out.write("startupTime=" + wdkModel.getStartupTime() + NL);
      out.write("displayName=" + wdkModel.getDisplayName() + NL);
      out.write("introduction=" + wdkModel.getIntroduction() + NL);
    }
    catch (IOException e) {
      throw new WdkModelException("Unable to dump WDK Model XML properties to file.", e);
    }
  }

  private boolean determineBooleanProperty(String propKey, boolean defaultValue) {
    String propValue = getProperty(propKey);
    if (propValue == null || propValue.trim().isEmpty()) {
      return defaultValue;
    }
    return Boolean.valueOf(propValue.trim());
  }

  private static Map<String, String> getConfig(String reportSpecificKey, String reportSpecificValue, boolean hasHeader) {
    return new MapBuilder<String,String>()
        .put(StandardConfig.ATTACHMENT_TYPE, "text")
        .put(AbstractTabularReporter.FIELD_HAS_HEADER, String.valueOf(hasHeader))
        .put(AbstractTabularReporter.FIELD_DIVIDER, FormatUtil.TAB)
        .put(reportSpecificKey, reportSpecificValue)
        .toMap();
  }

  private static void writeReport(Reporter reporter, Path outputFile) throws WdkModelException {
    // write query results to disk
    try (FileOutputStream fileOut = new FileOutputStream(outputFile.toFile())) {
      reporter.report(fileOut);
    }
    catch (IOException | JSONException e) {
      throw new WdkModelException("Unable to dump analysis files to data storage dir", e);
    }
  }
}
