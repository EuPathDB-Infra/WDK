package org.gusdb.wdk.model.query.param;

import java.util.LinkedHashMap;
import java.util.Map;

import org.gusdb.fgputil.cache.NoUpdateItemFetcher;
import org.gusdb.fgputil.cache.UnfetchableItemException;
import org.gusdb.fgputil.json.JsonUtil;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.dbms.ResultList;
import org.gusdb.wdk.model.query.Query;
import org.gusdb.wdk.model.query.QueryInstance;
import org.gusdb.wdk.model.query.param.values.ValidStableValuesFactory.CompleteValidStableValues;
import org.gusdb.wdk.model.user.User;
import org.json.JSONException;
import org.json.JSONObject;

public class MetaDataSpecItemFetcher extends NoUpdateItemFetcher<String, Map<String, Map<String, String>>> {

  private static final String QUERY_NAME_KEY = "queryName";
  private static final String DEPENDED_PARAM_VALUES_KEY = "dependedParamValues";

  private final CompleteValidStableValues _paramValues;
  private final User _user;

  public MetaDataSpecItemFetcher(CompleteValidStableValues paramValues, User user) {
    _paramValues = paramValues;
    _user = user;
  }

  @Override
  public Map<String, Map<String, String>> fetchItem(String cacheKey) throws UnfetchableItemException {
    try {
      QueryInstance<?> instance = _paramValues.getQuery().makeInstance(_user, _paramValues);
      Map<String, Map<String, String>> metadata = new LinkedHashMap<>();
      try (ResultList resultList = instance.getResults()) {
        while (resultList.next()) {
          String property = (String) resultList.get(FilterParam.COLUMN_PROPERTY);
          String info = (String) resultList.get(FilterParam.COLUMN_SPEC_PROPERTY);
          String data = (String) resultList.get(FilterParam.COLUMN_SPEC_VALUE);
          Map<String, String> propertyMeta = metadata.get(property);
          if (propertyMeta == null) {
            propertyMeta = new LinkedHashMap<>();
            metadata.put(property, propertyMeta);
          }
          propertyMeta.put(info, data);
        }
      }
      return metadata;
    }
    catch (WdkModelException | WdkUserException ex) {
      throw new UnfetchableItemException(ex);
    }
  }

  public String getCacheKey() throws JSONException {
    Query query = _paramValues.getQuery();
    JSONObject cacheKeyJson = new JSONObject();
    cacheKeyJson.put(QUERY_NAME_KEY, query.getName());
    JSONObject paramValuesJson = new JSONObject();
    for (String paramName : _paramValues.keySet())
      if (query.getParamMap() != null && query.getParamMap().containsKey(paramName))
        paramValuesJson.put(paramName, _paramValues.get(paramName));
    cacheKeyJson.put(DEPENDED_PARAM_VALUES_KEY, paramValuesJson);
    return JsonUtil.serialize(cacheKeyJson);
  }
}
