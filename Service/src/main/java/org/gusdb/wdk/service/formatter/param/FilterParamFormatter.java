package org.gusdb.wdk.service.formatter.param;

import java.util.Map;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.query.param.EnumParamVocabInstance;
import org.gusdb.wdk.model.query.param.FilterParam;
import org.gusdb.wdk.model.query.param.values.ValidStableValuesFactory.ValidStableValues;
import org.gusdb.wdk.model.user.User;
import org.gusdb.wdk.service.formatter.Keys;
import org.json.JSONException;
import org.json.JSONObject;

public class FilterParamFormatter extends AbstractEnumParamFormatter {

  @SuppressWarnings("unused")
  private static final Logger LOG = Logger.getLogger(FilterParamFormatter.class);

  protected FilterParam filterParam; 

  FilterParamFormatter(FilterParam param) {
    super(param);
    this.filterParam = param;
  }

  @Override
  public JSONObject getJson(User user, ValidStableValues dependedParamValues)
      throws JSONException, WdkModelException, WdkUserException {
    JSONObject pJson = super.getJson(user, dependedParamValues);
    EnumParamVocabInstance vocabInstance = getVocabInstance(user, dependedParamValues);
    pJson.put(Keys.VOCABULARY, getVocabArrayJson(vocabInstance));
    // pJson.put("metaData", getMetaDataJson(user, dependedParamValues));
    pJson.put("metaDataSpec", getMetaDataSpecJson(user, dependedParamValues));
    return pJson;
  }

  public JSONObject getMetaDataJson(User user, ValidStableValues dependedParamValues)
      throws JSONException, WdkModelException, WdkUserException {
    Map<String, Map<String, String>> metaDataMap = filterParam.getMetadata(user, dependedParamValues);
    JSONObject metaDataJson = new JSONObject();
    for (String term : metaDataMap.keySet()) {
      JSONObject termJson = new JSONObject();
      for (String prop : metaDataMap.get(term).keySet()) termJson.put(prop, metaDataMap.get(term).get(prop));
      metaDataJson.put(term, termJson);
    }
    return metaDataJson; 
  }

  public JSONObject getMetaDataSpecJson(User user, ValidStableValues dependedParamValues) throws JSONException, WdkModelException, WdkUserException {
    Map<String, Map<String, String>> metaDataSpecMap = filterParam.getMetadataSpec(user, dependedParamValues);
    JSONObject metaDataSpecJson = new JSONObject();
    for (String prop : metaDataSpecMap.keySet()) {
      JSONObject propJson = new JSONObject();
      for (String specProp : metaDataSpecMap.get(prop).keySet()) propJson.put(specProp, metaDataSpecMap.get(prop).get(specProp));
      metaDataSpecJson.put(prop, propJson);
    }
    return metaDataSpecJson; 
  }
}
