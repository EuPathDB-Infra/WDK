package org.gusdb.wdk.model.report.reporter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.json.JsonWriter;
import org.gusdb.wdk.core.api.JsonKeys;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.answer.factory.AnswerValue;
import org.gusdb.wdk.model.answer.stream.RecordStream;
import org.gusdb.wdk.model.answer.stream.RecordStreamFactory;
import org.gusdb.wdk.model.record.RecordInstance;
import org.gusdb.wdk.model.record.TableField;
import org.gusdb.wdk.model.record.attribute.AttributeField;
import org.gusdb.wdk.model.report.AbstractReporter;
import org.gusdb.wdk.model.report.Reporter;
import org.gusdb.wdk.model.report.Reporter.ContentDisposition;
import org.gusdb.wdk.model.report.config.AnswerDetails;
import org.gusdb.wdk.model.report.config.AnswerDetailsFactory;
import org.gusdb.wdk.service.factory.AnswerValueFactory;
import org.gusdb.wdk.service.formatter.RecordFormatter;
import org.gusdb.wdk.service.request.exception.RequestMisformatException;
import org.json.JSONObject;

/**
 * Formats WDK answer service responses.  JSON will have the following form:
 * {
 *   meta: {
 *     class: String,
 *     totalCount: Number,
 *     responseCount: Number,
 *     attributes: [ String ],
 *     tables: [ String ]
 *   },
 *   records: [ see RecordFormatter ]
 * }
 */
public class DefaultJsonReporter extends AbstractReporter {

  @SuppressWarnings("unused")
  private static final Logger LOG = Logger.getLogger(DefaultJsonReporter.class);

  private static final String DEFAULT_JSON_FILENAME = "result.json";

  public static Reporter createDefault(AnswerValue answerValue) throws WdkModelException {
    AnswerDetails answerDetails = AnswerDetailsFactory.createDefault(answerValue.getQuestion());
    return new DefaultJsonReporter(answerValue).configure(answerDetails);
  }

  private Map<String,AttributeField> _attributes;
  private Map<String,TableField> _tables;
  private ContentDisposition _contentDisposition;
  
  public DefaultJsonReporter(AnswerValue answerValue) {
    super(answerValue);
  }

  @Override
  public Reporter configure(Map<String, String> config) {
    throw new UnsupportedOperationException("Map configuration not supported by this reporter.");
  }

  @Override
  public Reporter configure(JSONObject config) throws RequestMisformatException, WdkModelException {
    return configure(AnswerDetailsFactory.createFromJson(config, _baseAnswer.getQuestion()));
  }

  public DefaultJsonReporter configure(AnswerDetails config) throws WdkModelException {
    AnswerValueFactory factory = new AnswerValueFactory(_baseAnswer.getUser());
    _baseAnswer = factory.getConfiguredAnswer(_baseAnswer, config);
    _attributes = config.getAttributes();
    _tables = config.getTables();
    _contentDisposition = config.getContentDisposition();
    return this;
  }

  @Override
  public String getHttpContentType() {
    return MediaType.APPLICATION_JSON;
  }

  @Override
  public String getDownloadFileName() {
    return DEFAULT_JSON_FILENAME;
  }

  @Override
  public ContentDisposition getContentDisposition() {
    return _contentDisposition;
  }

  @Override
  protected void write(OutputStream out) throws WdkModelException {

    // create output writer and initialize record stream
    try (JsonWriter writer = new JsonWriter(new BufferedWriter(new OutputStreamWriter(out)));
         RecordStream recordStream = RecordStreamFactory.getRecordStream(
            _baseAnswer, _attributes.values(), _tables.values())) {

      // start parent object and records array
      writer.object().key(JsonKeys.RECORDS).array();

      // write records
      int numRecordsReturned = 0;
      for (RecordInstance record : recordStream) {
        writer.value(RecordFormatter.getRecordJson(record, _attributes.keySet(), _tables.keySet()).getFirst());
        numRecordsReturned++;
      }

      // get metadata object
      JSONObject metadata = getMetaData(_baseAnswer, _attributes.keySet(), _tables.keySet(), numRecordsReturned);

      // end records array, write meta property, and close object
      writer.endArray().key(JsonKeys.META).value(metadata);
      
      // allow subclasses an opportunity to extend the JSON
      writeAdditionalJson(writer).endObject();
    }
    catch (WdkUserException e) {
      // should already have validated any user input
      throw new WdkModelException("Internal validation failure", e);
    }
    catch (IOException e) {
      throw new WdkModelException("Unable to write reporter result to output stream", e);
    }
  }
  
  /**
   * to be used by subclasses, to add their additional json to the 
   * top level json object
   * @param writer
   * @return the JsonWriter that now includes the addtions, if any
   */
  public JsonWriter writeAdditionalJson(JsonWriter writer) { return writer; }

  private static JSONObject getMetaData(AnswerValue answerValue,
      Set<String> includedAttributes, Set<String> includedTables, int numRecordsReturned)
      throws WdkModelException, WdkUserException {
    JSONObject meta = new JSONObject();
    meta.put(JsonKeys.RECORD_CLASS_NAME, answerValue.getQuestion().getRecordClass().getFullName());
    meta.put(JsonKeys.TOTAL_COUNT, answerValue.getResultSizeFactory().getResultSize());
    meta.put(JsonKeys.RESPONSE_COUNT, numRecordsReturned);
    meta.put(JsonKeys.ATTRIBUTES, FormatUtil.stringCollectionToJsonArray(includedAttributes));
    meta.put(JsonKeys.TABLES, FormatUtil.stringCollectionToJsonArray(includedTables));
    return meta;
  }
}
