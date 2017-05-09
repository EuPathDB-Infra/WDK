package org.gusdb.wdk.service.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

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

import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.query.param.FilterParamNew;
import org.gusdb.wdk.model.query.param.FilterParamNew.FilterParamSummaryCounts;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.query.param.ParamHandler;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.question.QuestionSet;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.service.formatter.QuestionFormatter;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Provides access to Question data configured in the WDK Model.  All question
 * name path params can be either the configured question URL segment (which
 * defaults to the short name but can be overridden in the XML), or the
 * question's full, two-part name, made by joining the question set name and
 * question short name with a '.'.
 * 
 * @author rdoherty
 */
@Path("/question")
@Produces(MediaType.APPLICATION_JSON)
public class QuestionService extends WdkService {

  private static final String QUESTION_RESOURCE = "Question Name: ";
  
  /**
   * Get a list of all questions for a recordClass. Does not supply details of the questions (use another endpoint for that).
   */
  @GET
  public Response getQuestions(
      @QueryParam("recordClass") String recordClassStr,
      @QueryParam("expandQuestions") Boolean expandQuestions,
      @QueryParam("expandParams") Boolean expandParams)
          throws JSONException, WdkModelException, WdkUserException {
    try {
      Map<String,String> dependerParams = null;
      return Response.ok(QuestionFormatter.getQuestionsJson(
          (recordClassStr == null || recordClassStr.isEmpty() ? getAllQuestions(getWdkModel()) :
            getQuestionsForRecordClasses(getWdkModel(), recordClassStr.split(","))),
            getFlag(expandQuestions), getFlag(expandParams), getSessionUser(), dependerParams).toString()).build();
    }
    catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage(), e);
    }
  }

  private static List<Question> getQuestionsForRecordClasses(
      WdkModel wdkModel, String[] recordClassNames) throws IllegalArgumentException {
    try {
      List<Question> questions = new ArrayList<>();
      for (String rcName : recordClassNames) {
        RecordClass rc = wdkModel.getRecordClass(rcName);
        questions.addAll(Arrays.asList(wdkModel.getQuestions(rc)));
      }
      return questions;
    }
    catch (WdkModelException e) {
      throw new IllegalArgumentException("At least one passed record class name is incorrect.", e);
    }
  }

  // TODO: seems like this should be part of the model, but we need
  //       to consider XML questions, boolean questions, etc.
  private static List<Question> getAllQuestions(WdkModel wdkModel) {
    List<Question> questions = new ArrayList<>();
    for (QuestionSet qSet : wdkModel.getAllQuestionSets()) {
      questions.addAll(Arrays.asList(qSet.getQuestions()));
    }
    return questions;
  }
  
  /**
   * Get the information about a specific question.  Use expandParams=true to get the details of each parameter, including vocabularies and metadata info.
   * This endpoint is typically used to display a question page (using default values). 
   */
  @GET
  @Path("/{questionName}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getQuestion(
      @PathParam("questionName") String questionName,
      @QueryParam("expandParams") Boolean expandParams)
          throws WdkUserException, WdkModelException {
    Question question = getQuestionFromSegment(questionName);
    if(question == null)
      throw new NotFoundException(WdkService.formatNotFound(QUESTION_RESOURCE + questionName));
    Map<String,String> dependedParamValues = new HashMap<String, String>();
    return Response.ok(QuestionFormatter.getQuestionJson(question,
        getFlag(expandParams), getSessionUser(), dependedParamValues).toString()).build();
  }

  private Question getQuestionFromSegment(String questionName) {
    WdkModel model = getWdkModel();
    try {
      Question q = model.getQuestionByUrlSegment(questionName);
      return (q == null ? model.getQuestion(questionName) : q);
    }
    catch(WdkModelException e) {
      // A WDK Model Exception here implies that a question of the name provided cannot be found.
      throw new NotFoundException(WdkService.formatNotFound(QUESTION_RESOURCE + questionName));
    }
  }

  /**
   * Get information about a question, given a complete set of param values.  (This endpoint is 
   * typically used for a revise operation.)  Throw a WdkUserException if any parameter value
   * is missing or invalid.  (The exception only describes the first invalid parameter, not all such.)
   * @param questionName
   * @param expandParams
   * @param body
   * @return
   * @throws WdkUserException
   * @throws WdkModelException
   *
   * Sample request body:
   * 
   * {
   *   "contextParamValues": {
   *     "size": "5",
   *     "people": "Sam,Sue"
   *   }
   * }
   */
  @POST
  @Path("/{questionName}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getQuestionRevise(@PathParam("questionName") String questionName, String body)
          throws WdkUserException, WdkModelException {
    Question question = getQuestionFromSegment(questionName);
    if (question == null)
      throw new NotFoundException(questionName);
    // extract context values from body
    Map<String, String> contextParamValues = new HashMap<String, String>(); 
    try {
      JSONObject jsonBody = new JSONObject(body);
      contextParamValues = parseContextParamValuesFromJson(jsonBody, question);
    }
    catch (JSONException e) {
      throw new BadRequestException(e);
    }

    // confirm that we got all param values
    for (Param param : question.getParams()) {
      if (!contextParamValues.containsKey(param.getName()))
        throw new WdkUserException("This call to the question service requires " +
            "that the body contain values for all params.  But it is missing one for: " + param.getName());
      param.validate(getSessionUser(), contextParamValues.get(param.getName()), contextParamValues);
    }

    return Response.ok(QuestionFormatter.getQuestionJson(question, true, getSessionUser(),
        contextParamValues).toString()).build();
  }

  /**
   * Get an updated set of vocabularies (and meta data info) for the parameters that depend on the specified changed parameter.
   * (Also validate the changed parameter.)
   * Request must provide the parameter values of any other parameters that those vocabularies depend on.
   * (This endpoint is typically used when a user changes a depended param.)
   *
   * Sample request body:
   *
   * {
   *   "changedParam" : { "name": "height", "value": "12" },
   *   "contextParamValues" : [see /{questionName} endpoint]
   * }
   *
   * @param questionName
   * @param expandParams
   * @param body
   * @return
   * @throws WdkUserException
   * @throws WdkModelException
   */
  @POST
  @Path("/{questionName}/refreshedDependentParams")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getQuestionChange(@PathParam("questionName") String questionName, String body)
          throws WdkUserException, WdkModelException {
    Question question = getQuestionFromSegment(questionName);
    if (question == null)
      throw new NotFoundException(WdkService.NOT_FOUND + questionName);
    Map<String, String> contextParamValues = new HashMap<String, String>();
    String changedParamName = null;
    String changedParamValue = null;
    
    try {
      JSONObject jsonBody = new JSONObject(body);
      JSONObject changedParam = jsonBody.getJSONObject("changedParam");
      changedParamName = changedParam.getString("name");
      changedParamValue = changedParam.getString("value");
      contextParamValues = parseContextParamValuesFromJson(jsonBody, question);
    }
    catch (JSONException e) {
      throw new BadRequestException(e);
    }

    // remove the changed param from the context (maybe we should throw an exception instead?)
    contextParamValues.remove(changedParamName);

    // find the param object for the changed param, and validate it. (this will also validate the paramValuesContext it needs, if dependent)
    Param changedParam = null;
    for (Param param : question.getParams()) if (param.getName().equals(changedParamName)) changedParam = param;
    if (changedParam == null) throw new WdkUserException("Param with name '" + changedParamName + "' is no longer valid for question '" + question.getName() + "'");
    changedParam.validate(getSessionUser(), changedParamValue, contextParamValues);
    
    // find all dependencies of the changed param, and remove them from the context
    for (Param dependentParam : changedParam.getAllDependentParams()) contextParamValues.remove(dependentParam.getName());

    return Response.ok(QuestionFormatter.getQuestionJson(question, true, getSessionUser(),
        contextParamValues, changedParam.getAllDependentParams()).toString()).build();
  }

  private Map<String, String> parseContextParamValuesFromJson(JSONObject bodyJson, Question question) throws JSONException, WdkUserException {

    Map<String, String> contextParamValues = new HashMap<String, String>();
    JSONObject contextJson = bodyJson.getJSONObject("contextParamValues");

    for (Iterator<?> keys = contextJson.keys(); keys.hasNext();) {
      String keyName = (String) keys.next();
      String keyValue = contextJson.getString(keyName);
      if (keyName == null) throw new WdkUserException("contextParamValues contains a null key");
      if (keyValue == null) throw new WdkUserException("Parameter name '" + keyName + "' has null value");
      if (!question.getParamMap().containsKey(keyName)) throw new WdkUserException("Parameter '" + keyName + "' is not in question '" + question.getFullName() + "'.");
      contextParamValues.put(keyName, keyValue);
    }
    return contextParamValues;
  }
  
  /**
   * Exclusive to FilterParams.  Get a summary of filtered and unfiltered counts for a specified ontology term.
   *
   * Sample request body:
   *
   * {
   *   "ontologyId" : string
   *   "filters" : [ see raw value for FilterParamHandler ]
   *   "contextParamValues" : [see /{questionName} endpoint]
   * }
   *
   * @param questionName
   * @param paramName
   * @param body
   * @return
   * @throws WdkUserException
   * @throws WdkModelException
   */
  @POST
  @Path("/{questionName}/{paramName}/ontologyTermSummary")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getFilterParamOntologyTermSummary(@PathParam("questionName") String questionName, @PathParam("paramName") String paramName, String body)
          throws WdkUserException, WdkModelException {
    
    Question question = getQuestionFromSegment(questionName);
    FilterParamNew filterParam = getFilterParam(questionName, question, paramName);
    
    Map<String, String> contextParamValues = new HashMap<String, String>();
    
    try {
      JSONObject jsonBody = new JSONObject(body);
      contextParamValues = parseContextParamValuesFromJson(jsonBody, question);
      JSONObject filters = jsonBody.getJSONObject("filters");
      String ontologyId = jsonBody.getString("ontologyId");
      Map<String, FilterParamSummaryCounts> counts = filterParam.getOntologyTermSummary(getSessionUser(), contextParamValues, ontologyId, filters);
      return Response.ok(QuestionFormatter.getOntologyTermSummaryJson(counts)).build();
    }
    catch (JSONException e) {
      throw new BadRequestException(e);
    }
  }
  
  /**
   * Exclusive to FilterParams.  Get a summary of filtered and unfiltered counts.
   *
   * Sample request body:
   *
   * {
   *   "filters" : [ see raw value for FilterParamHandler ]
   *   "contextParamValues" : [see /{questionName} endpoint]
   * }
   *
   * @param questionName
   * @param paramName
   * @param body
   * @return
   * @throws WdkUserException
   * @throws WdkModelException
   */
  @POST
  @Path("/{questionName}/{paramName}/summaryCounts")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getFilterParamSummaryCounts(@PathParam("questionName") String questionName, @PathParam("paramName") String paramName, String body)
          throws WdkUserException, WdkModelException {
    
    Question question = getQuestionFromSegment(questionName);
    FilterParamNew filterParam = getFilterParam(questionName, question, paramName);
    
    Map<String, String> contextParamValues = new HashMap<String, String>();
    
    try {
      JSONObject jsonBody = new JSONObject(body);
      contextParamValues = parseContextParamValuesFromJson(jsonBody, question);
      JSONObject filters = jsonBody.getJSONObject("filters");
      FilterParamSummaryCounts counts = filterParam.getTotalsSummary(getSessionUser(), contextParamValues, filters);
      return Response.ok(QuestionFormatter.getFilterParamSummaryJson(counts)).build();
    }
    catch (JSONException e) {
      throw new BadRequestException(e);
    }
  }

  private Param getParam(String questionName, Question question, String paramName) throws WdkUserException {
    if (question == null)
      throw new NotFoundException(WdkService.NOT_FOUND + questionName);
    Param param = question.getQuery().getParamMap().get(paramName);
    if (param == null)
      throw new NotFoundException(WdkService.NOT_FOUND + paramName);
    return param;
  }
  
  private FilterParamNew getFilterParam(String questionName, Question question, String paramName) throws WdkUserException {
    Param param = getParam(questionName, question, paramName);
    if (!(param instanceof FilterParamNew)) throw new WdkUserException(paramName + " is not a FilterParam");
    return (FilterParamNew)param;
  }
  /*
   * { internalValue: "la de dah" }
   */

  @POST
  @Path("/{questionName}/{paramName}/internalValue")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response getParamInternalValue(@PathParam("questionName") String questionName, @PathParam("paramName") String paramName, String body)
          throws WdkUserException, WdkModelException {
    
    Question question = getQuestionFromSegment(questionName);
    Param param = getParam(questionName, question, paramName);
    ParamHandler paramHandler = param.getParamHandler();
    
    Map<String, String> contextParamValues = new HashMap<String, String>();
    
    try {
      JSONObject jsonBody = new JSONObject(body);
      contextParamValues = parseContextParamValuesFromJson(jsonBody, question);
      JSONObject stableValueJson = jsonBody.getJSONObject("stableValue");
      String stableValue = stableValueJson.toString();
      String internalValue = paramHandler.toInternalValue(getSessionUser(), stableValue, contextParamValues);
      return Response.ok(QuestionFormatter.getInternalValueJson(internalValue)).build();
    }
    catch (JSONException e) {
      throw new BadRequestException(e);
    }
  }



}
