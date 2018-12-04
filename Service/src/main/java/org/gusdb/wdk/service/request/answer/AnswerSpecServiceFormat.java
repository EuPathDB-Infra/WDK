package org.gusdb.wdk.service.request.answer;

import org.gusdb.fgputil.json.JsonUtil;
import org.gusdb.wdk.core.api.JsonKeys;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.answer.spec.AnswerSpec;
import org.gusdb.wdk.model.answer.spec.AnswerSpecBuilder;
import org.gusdb.wdk.model.answer.spec.ParamFiltersClobFormat;
import org.gusdb.wdk.service.request.exception.RequestMisformatException;
import org.json.JSONException;
import org.json.JSONObject;

public class AnswerSpecServiceFormat {

  /**
   * Creates an AnswerSpecBuilder object using the passed JSON.  "questionName"
   * and "parameters" are the only required properties; legacy and modern
   * filters are optional; omission means no filters will be applied.  Weight is
   * optional and defaults to 0.
   * 
   * Input Format:
   * {
   *   "questionName" : String,
   *   "parameters": Object (map from paramName -> paramValue),
   *   "legacyFilterName": (optional) String,
   *   "filters": (optional) [ {
   *     "name": String, value: Any
   *   } ],
   *   "viewFilters": (optional) [ {
   *     "name": String, value: Any
   *   } ],
   *   "wdk_weight": (optional) Integer
   * }
   * 
   * @param json JSON representation of an answer spec
   * @param wdkModel WDK model
   * @return constructed answer spec builder
   * @throws RequestMisformatException if JSON is malformed
   */
  public static AnswerSpecBuilder parse(JSONObject json, WdkModel wdkModel) throws RequestMisformatException {
    try {
      // get question name, validate, and create instance with valid Question
      AnswerSpecBuilder specBuilder = AnswerSpec.builder(wdkModel)
          .setQuestionName(json.getString(JsonKeys.QUESTION_NAME))
          .setParamValues(JsonUtil.parseProperties(json.getJSONObject(JsonKeys.PARAMETERS)));

      // all filter fields and weight are optional
      if (json.has(JsonKeys.LEGACY_FILTER_NAME)) {
        specBuilder.setLegacyFilterName(json.getString(JsonKeys.LEGACY_FILTER_NAME));
      }
      if (json.has(JsonKeys.FILTERS)) {
        specBuilder.setFilterOptions(ParamFiltersClobFormat.parseFiltersJson(json, JsonKeys.FILTERS));
      }
      if (json.has(JsonKeys.VIEW_FILTERS)) {
        specBuilder.setViewFilterOptions(ParamFiltersClobFormat.parseFiltersJson(json, JsonKeys.VIEW_FILTERS));
      }
      if (json.has(JsonKeys.WDK_WEIGHT)) {
        specBuilder.setAssignedWeight(json.getInt(JsonKeys.WDK_WEIGHT));
      }
      return specBuilder;
    }
    catch (JSONException e) {
      throw new RequestMisformatException("Required value is missing or incorrect type", e);
    }
  }

  public static JSONObject format(AnswerSpec answerSpec) {
    return new JSONObject()
        .put(JsonKeys.QUESTION_NAME, answerSpec.getQuestionName())
        // params and filters are sent with the same format as in the DB
        .put(JsonKeys.PARAMETERS, ParamFiltersClobFormat.formatParams(answerSpec.getQueryInstanceSpec()))
        .put(JsonKeys.FILTERS, ParamFiltersClobFormat.formatFilters(answerSpec.getFilterOptions()))
        .put(JsonKeys.VIEW_FILTERS, ParamFiltersClobFormat.formatFilters(answerSpec.getViewFilterOptions()))
        .put(JsonKeys.WDK_WEIGHT, answerSpec.getQueryInstanceSpec().getAssignedWeight())
        .put(JsonKeys.LEGACY_FILTER_NAME, answerSpec.getLegacyFilterName());
  }

}
