package org.gusdb.wdk.service.formatter;

import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.answer.AnswerFilterInstance;
import org.gusdb.wdk.model.user.Step;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Formats WDK Step objects.  Step JSON will have the following form:
 * {
 *   id: Number, 
 *   
 *   answerSpec: {
 *     “questionName”: String,
 *     “params”: [ {
 *       “name”: String, “value”: Any
 *     } ],
 *     "legacyFilterName": (optional) String,
 *     “filters”: (optional) [ {
 *       “name”: String, value: Any
 *     } ],
 *     “viewFilters”: (optional) [ {
 *       “name”: String, value: Any
 *     } ],
 *     "weight": (optional) Integer
 *   }
 * }
 * 
 * @author rdoherty
 */
public class StepFormatter {

  public static JSONObject getStepJson(Step step) throws WdkModelException {
    try {
      return new JSONObject()
        .put(Keys.ID, step.getStepId())
        .put(Keys.DISPLAY_NAME, step.getDisplayName())
        .put(Keys.SHORT_DISPLAY_NAME, step.getShortDisplayName())
        .put(Keys.CUSTOM_NAME, step.getCustomName())
        .put(Keys.BASE_CUSTOM_NAME, step.getBaseCustomName())
        .put(Keys.COLLAPSED_NAME, step.getCollapsedName())
        .put(Keys.DESCRIPTION, step.getDescription())
        .put(Keys.OWNER_ID, step.getUser().getUserId())
        .put(Keys.STRATEGY_ID, step.getStrategyId())
        .put(Keys.ESTIMATED_SIZE, step.getEstimateSize())
        .put(Keys.HAS_COMPLETE_STEP_ANALYSES, step.getHasCompleteAnalyses())
        .put(Keys.RECORD_CLASS, step.getType())
        .put(Keys.ANSWER_SPEC, createAnswerSpec(step));
    }
    catch (JSONException e) {
      throw new WdkModelException("Unable to convert Step to service JSON", e);
    }
  }

  private static JSONObject createAnswerSpec(Step step) {
    JSONObject json = new JSONObject()
      .put(Keys.QUESTION_NAME, step.getQuestionName())
      .put(Keys.FILTERS, getOrEmptyArray(step.getFilterOptionsJSON()))
      .put(Keys.VIEW_FILTERS, getOrEmptyArray(step.getViewFilterOptionsJSON()))
      .put(Keys.WDK_WEIGHT, step.getAssignedWeight());
    AnswerFilterInstance legacyFilter = step.getFilter();
    if (legacyFilter != null) {
      json.put(Keys.LEGACY_FILTER_NAME, legacyFilter.getName());
    }
    JSONObject internalParamJson = step.getParamsJSON();
    JSONArray externalParamJson = new JSONArray();
    for (String paramName : JSONObject.getNames(internalParamJson)) {
      externalParamJson.put(new JSONObject()
          .put(Keys.NAME, paramName)
          .put(Keys.VALUE, internalParamJson.get(paramName)));
    }
    return json.put(Keys.PARAMETERS, externalParamJson);
  }

  private static JSONArray getOrEmptyArray(JSONArray jsonArrayOrNull) {
    return (jsonArrayOrNull == null ? new JSONArray() : jsonArrayOrNull);
  }
}
