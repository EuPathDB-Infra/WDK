package org.gusdb.wdk.service.service;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.record.FieldScope;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.model.record.RecordInstance;
import org.gusdb.wdk.model.record.RecordNotFoundException;
import org.gusdb.wdk.model.record.TableField;
import org.gusdb.wdk.model.user.User;
import org.gusdb.wdk.service.formatter.AttributeFieldFormatter;
import org.gusdb.wdk.service.formatter.RecordClassFormatter;
import org.gusdb.wdk.service.formatter.TableFieldFormatter;
import org.gusdb.wdk.service.request.RecordRequest;
import org.gusdb.wdk.service.request.RequestMisformatException;
import org.gusdb.wdk.service.stream.RecordStreamer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Path("/record")
public class RecordService extends WdkService {

  private static final Logger LOG = Logger.getLogger(RecordService.class);
  private static final String RECORD_CLASS_RESOURCE = "Record Class Name: ";
  private static final String TABLE_RESOURCE = "Table Name: ";
  private static final String RECORD_RESOURCE = "with primary key [%s]";

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getRecordClassList(
      @QueryParam("expandRecordClasses") Boolean expandRecordClasses,
      @QueryParam("expandAttributes") Boolean expandAttributes,
      @QueryParam("expandTables") Boolean expandTables,
      @QueryParam("expandTableAttributes") Boolean expandTableAttributes) {
    return Response.ok(
        RecordClassFormatter.getRecordClassesJson(getWdkModel().getAllRecordClassSets(),
            getFlag(expandRecordClasses), getFlag(expandAttributes),
            getFlag(expandTables), getFlag(expandTableAttributes)).toString()
    ).build();
  }

  @GET
  @Path("{recordClassName}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getRecordClassInfo(
      @PathParam("recordClassName") String recordClassName,
      @QueryParam("expandAttributes") Boolean expandAttributes,
      @QueryParam("expandTables") Boolean expandTables,
      @QueryParam("expandTableAttributes") Boolean expandTableAttributes) {
    try {
      RecordClass rc = getRecordClass(recordClassName);
      return Response.ok(
          RecordClassFormatter.getRecordClassJson(rc, getFlag(expandAttributes),
              getFlag(expandTables), getFlag(expandTableAttributes)).toString()
      ).build();
    }
    catch (WdkModelException e) {
      throw new NotFoundException(WdkService.formatNotFound(RECORD_CLASS_RESOURCE + recordClassName));
    }
  }

  @GET
  @Path("{recordClassName}/attribute")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getAttributesInfo(
      @PathParam("recordClassName") String recordClassName,
      @QueryParam("expandAttributes") Boolean expandAttributes) {
    try {
      RecordClass recordClass = getRecordClass(recordClassName);
      JSONArray attribsJson = AttributeFieldFormatter.getAttributesJson(
          recordClass.getAttributeFieldMap().values(), FieldScope.ALL, getFlag(expandAttributes));
      return Response.ok(attribsJson.toString()).build();
    }
    catch (WdkModelException e) {
      throw new NotFoundException(WdkService.formatNotFound(RECORD_CLASS_RESOURCE + recordClassName));
    }
  }

  @GET
  @Path("{recordClassName}/table")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getTablesInfo(
      @PathParam("recordClassName") String recordClassName,
      @QueryParam("expandTables") Boolean expandTables,
      @QueryParam("expandTableAttributes") Boolean expandTableAttributes) {
    try {
      RecordClass recordClass = getRecordClass(recordClassName);
      JSONArray tablesJson = TableFieldFormatter.getTablesJson(
          recordClass.getTableFieldMap().values(), FieldScope.ALL,
          getFlag(expandTables), getFlag(expandTableAttributes));
      return Response.ok(tablesJson.toString()).build();
    }
    catch (WdkModelException e) {
      throw new NotFoundException(WdkService.formatNotFound(RECORD_CLASS_RESOURCE + recordClassName));
    }
  }

  @GET
  @Path("{recordClassName}/table/{tableName}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getTableInfo(
      @PathParam("recordClassName") String recordClassName,
      @PathParam("tableName") String tableName,
      @QueryParam("expandTableAttributes") Boolean expandTableAttributes) {
    return getTableResponse(recordClassName, tableName, expandTableAttributes, false);
  }

  @GET
  @Path("{recordClassName}/table/{tableName}/attribute")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getTableAttributesInfo(
      @PathParam("recordClassName") String recordClassName,
      @PathParam("tableName") String tableName,
      @QueryParam("expandTableAttributes") Boolean expandTableAttributes) {
    return getTableResponse(recordClassName, tableName, expandTableAttributes, true);
  }
  
  @GET
  @Path("{recordClassName}/answerFormat")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getAnswerFormats(
      @PathParam("recordClassName") String recordClassName) {
    try {
      RecordClass recordClass = getRecordClass(recordClassName);
      JSONArray json = RecordClassFormatter.getAnswerFormatsJson(recordClass.getReporterMap().values(), FieldScope.ALL);
      return Response.ok(json.toString()).build();
    }
    catch (WdkModelException e) {
      throw new NotFoundException(WdkService.formatNotFound(RECORD_CLASS_RESOURCE + recordClassName));
    }
  }

  @POST
  @Path("{recordClassName}/instance")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response buildResult(@PathParam("recordClassName") String recordClassName, String body) throws WdkModelException {
    RecordInstance recordInstance = null;
    try {
      JSONObject json = new JSONObject(body);
      RecordClass rc = getRecordClass(recordClassName);
      RecordRequest request = RecordRequest.createFromJson(
          getCurrentUser(), json, rc, getWdkModelBean());

      recordInstance = getRecordInstance(getCurrentUser(), request);

      return Response.ok(RecordStreamer.getRecordAsStream(recordInstance, request.getAttributeNames(), request.getTableNames())).build();
    }
    catch (WdkUserException | RecordNotFoundException e) {
      // these may be thrown when the PK values either don't exist or map to >1 record
      String primaryKeys = (recordInstance == null ? "<unknown>" : recordInstance.getPrimaryKey().getValuesAsString());
      throw new NotFoundException(WdkService.formatNotFound(RECORD_CLASS_RESOURCE + recordClassName + ", " + String.format(RECORD_RESOURCE, primaryKeys)));
    }
    catch (JSONException | RequestMisformatException e) {
      LOG.warn("Passed request body deemed unacceptable", e);
      throw new BadRequestException(e);
    }
  }

  private RecordClass getRecordClass(String recordClassName) throws WdkModelException {
    WdkModel model = getWdkModel();
    RecordClass rc = model.getRecordClassByUrlSegment(recordClassName);
    return (rc == null ? model.getRecordClass(recordClassName) : rc);
  }

  private static RecordInstance getRecordInstance(User user, RecordRequest recordRequest) throws WdkModelException, WdkUserException {
    RecordClass recordClass = recordRequest.getRecordClass();
    return new RecordInstance(user, recordClass, recordRequest.getPrimaryKey());
  }

  private Response getTableResponse(String recordClassName, String tableName,
      Boolean expandTableAttributes, boolean attributesOnly) {
    try {
      RecordClass rc = getRecordClass(recordClassName);
      TableField table = rc.getTableFieldMap().get(tableName);
      boolean expandAttributes = getFlag(expandTableAttributes);
      if (table == null) throw new WdkModelException ("Table '" + tableName +
          "' not found for RecordClass '" + recordClassName + "'");
      return Response.ok((attributesOnly ?
          AttributeFieldFormatter.getAttributesJson(
              table.getAttributeFieldMap(FieldScope.ALL).values(), FieldScope.ALL, expandAttributes) :
          TableFieldFormatter.getTableJson(table, expandAttributes)
      ).toString()).build();
    }
    catch (WdkModelException e) {
      throw new NotFoundException(WdkService.formatNotFound(RECORD_CLASS_RESOURCE + recordClassName + ", " + TABLE_RESOURCE + tableName));
    }
  }
}
