package org.gusdb.wdk.service.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
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
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.question.QuestionSet;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.service.formatter.QuestionFormatter;
import org.json.JSONException;
import org.json.JSONObject;

@Path("/question")
@Produces(MediaType.APPLICATION_JSON)
public class QuestionService extends WdkService {

  /**
   * Get a list of all questions for a recordClass. Does not supply details of the questions (use another endpoint for that).
   */
  @GET
  public Response getQuestions(
    @QueryParam("recordClass") String recordClassStr,
    @QueryParam("expandQuestions") Boolean expandQuestions,
    @QueryParam("expandParams") Boolean expandParams
  ) throws JSONException, WdkModelException, WdkUserException {
    try {
      Map<String,String> dependerParams = null;
      return Response.ok(QuestionFormatter.getQuestionsJson(
          (recordClassStr == null || recordClassStr.isEmpty() ? getAllQuestions(getWdkModel()) :
            getQuestionsForRecordClasses(getWdkModel(), recordClassStr.split(","))),
            getFlag(expandQuestions), getFlag(expandParams), getCurrentUser(), dependerParams).toString()).build();
    }
    catch (IllegalArgumentException e) {
      return getBadRequestBodyResponse(e.getMessage());
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
    
    getWdkModelBean().validateQuestionFullName(questionName);
    Question question = getWdkModel().getQuestion(questionName);
    Map<String,String> dependedParamValues = new HashMap<String, String>();
    
    return Response.ok(QuestionFormatter.getQuestionJson(question,
        getFlag(expandParams), getCurrentUser(), dependedParamValues).toString()).build();
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

    getWdkModelBean().validateQuestionFullName(questionName);
    Question question = getWdkModel().getQuestion(questionName);
    
    // extract context values from body
    Map<String, String> contextParamValues = new HashMap<String, String>(); 
    try {
      JSONObject jsonBody = new JSONObject(body);
      contextParamValues = parseContextParamValuesFromJson(jsonBody, question);
    }
    catch (JSONException e) {
      return getBadRequestBodyResponse(e.getMessage());
    }

    // confirm that we got all param values
    for (Param param : question.getParams()) {
      if (!contextParamValues.containsKey(param.getName()))
	throw new WdkUserException("This call to the question service requires that the body contain values for all params.  But it is missing one for: " + param.getName());
      param.validate(getCurrentUser(), contextParamValues.get(param.getName()), contextParamValues);
    }

    return Response.ok(QuestionFormatter.getQuestionJson(question, true, getCurrentUser(),
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

    getWdkModelBean().validateQuestionFullName(questionName);
    Question question = getWdkModel().getQuestion(questionName);
    
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
      return getBadRequestBodyResponse(e.getMessage());
    }

    // remove the changed param from the context (maybe we should throw an exception instead?)
    contextParamValues.remove(changedParamName);

    // find the param object for the changed param, and validate it. (this will also validate the paramValuesContext it needs, if dependent)
    Param changedParam = null;
    for (Param param : question.getParams()) if (param.getName().equals(changedParamName)) changedParam = param;
    if (changedParam == null) throw new WdkUserException("Param with name '" + changedParamName + "' is no longer valid for question '" + question.getName() + "'");
    changedParam.validate(getCurrentUser(), changedParamValue, contextParamValues);
    
    // find all dependencies of the changed param, and remove them from the context
    for (Param dependentParam : changedParam.getAllDependentParams()) contextParamValues.remove(dependentParam.getName());

    return Response.ok(QuestionFormatter.getQuestionJson(question, true, changedParam.getAllDependentParams(), getCurrentUser(),
        contextParamValues).toString()).build();
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
}
