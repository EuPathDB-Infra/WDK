/**
 * 
 */
package org.gusdb.wdk.model.query.param;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.dbms.ResultList;
import org.gusdb.wdk.model.jspwrap.EnumParamCache;
import org.gusdb.wdk.model.query.Column;
import org.gusdb.wdk.model.query.Query;
import org.gusdb.wdk.model.query.QueryInstance;
import org.gusdb.wdk.model.user.User;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author jerric
 * 
 *         The filter param is similar to FlatVocabParam in many aspects, but the most important difference
 *         between these two are that the stable value of filter param is a JSON string with values for the
 *         metadata and terms for the param itself, but the stable value of a flatVocabParam is just a string
 *         representation of comma separated list of terms.
 * 
 *         raw value: a JSON string of both term list and metadata list.
 * 
 *         stable value: same as raw value, a JSON string of both term list and metadata list.
 * 
 *         signature: a checksum of the JSON string, but values are sorted
 * 
 *         internal: the internal value of the param, same as the flatVocabParam aspect of the param.
 * 
 */
public class FilterParam extends FlatVocabParam {

  private static final String COLUMN_PROPERTY = "property";
  private static final String COLUMN_VALUE = "value";

  private static final String COLUMN_SPEC_PROPERTY = "spec_property";
  private static final String COLUMN_SPEC_VALUE = "spec_value";

  private String metadataQueryRef;
  private Query metadataQuery;

  private String metadataSpecQueryRef;
  private Query metadataSpecQuery;

  private String defaultColumns;

  // remove non-terminal nodes with a single child
  private boolean trimMetadataTerms = true;

  /**
   * 
   */
  public FilterParam() {
    super();

    // register handlers
    setHandler(new FilterParamHandler());
  }

  /**
   * @param param
   */
  public FilterParam(FilterParam param) {
    super(param);
    this.metadataQueryRef = param.metadataQueryRef;
    if (param.metadataQuery != null)
      this.metadataQuery = param.metadataQuery.clone();
    this.metadataSpecQueryRef = param.metadataSpecQueryRef;
    if (param.metadataSpecQuery != null)
      this.metadataSpecQuery = param.metadataSpecQuery.clone();
    if (param.defaultColumns != null)
      this.defaultColumns = param.defaultColumns;
    this.trimMetadataTerms = param.trimMetadataTerms;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.Param#clone()
   */
  @Override
  public Param clone() {
    return new FilterParam(this);
  }

  /**
   * @return the propertyQueryName
   */
  public String getMetadataQueryRef() {
    return metadataQueryRef;
  }

  /**
   * @param metadataQueryRef
   *          the propertyQueryName to set
   */
  public void setMetadataQueryRef(String metadataQueryRef) {
    this.metadataQueryRef = metadataQueryRef;
  }

  /**
   * @return the propertyQuery
   */
  public Query getMetadataQuery() {
    return metadataQuery;
  }

  /**
   * @param propertyQuery
   *          the propertyQuery to set
   */
  public void setMetadataQuery(Query metadataQuery) {
    this.metadataQuery = metadataQuery;
  }

  /**
   * @return the metadataSpec Query Name
   */
  public String getMetadataSpecQueryRef() {
    return metadataSpecQueryRef;
  }

  /**
   * @param metadataSpecQueryRef
   *          the metadataQueryName to set
   */
  public void setMetadataSpecQueryRef(String metadataSpecQueryRef) {
    this.metadataSpecQueryRef = metadataSpecQueryRef;
  }

  /**
   * @return the metadataQuery
   */
  public Query getMetadataSpecQuery() {
    return metadataSpecQuery;
  }

  /**
   * @param metadataSpecQuery
   *          the metadataQuery to set
   */
  public void setMetadataSpecQuery(Query metadataSpecQuery) {
    this.metadataSpecQuery = metadataSpecQuery;
  }

  /**
   * @return the defaultColumns
   */
  public String getDefaultColumns() {
    return defaultColumns;
  }

  /**
   * @param defaultColumns
   *          the metadataQuery to set
   */
  public void setDefaultColumns(String defaultColumns) {
    this.defaultColumns = defaultColumns;
  }

  /**
   * @return the trimMetadataTerms
   */
  public boolean getTrimMetadataTerms() {
    return trimMetadataTerms;
  }

  /**
   * @param trimMetadataTerms
   *          the metadataQuery to set
   */
  public void setTrimMetadataTerms(boolean trimMetadataTerms) {
    this.trimMetadataTerms = trimMetadataTerms;
  }

  @Override
  public void resolveReferences(WdkModel model) throws WdkModelException {
    super.resolveReferences(model);

    // resolve property query, the property query should have the same params as vocab query
    if (metadataQueryRef != null) {
      this.metadataQuery = resolveQuery(model, metadataQueryRef, "property query");

      // the propertyQuery must have exactly 3 columns: term, property, and value.
      Map<String, Column> columns = metadataQuery.getColumnMap();
      if (columns.size() != 3 || !columns.containsKey(COLUMN_TERM) || !columns.containsKey(COLUMN_PROPERTY) ||
          !columns.containsKey(COLUMN_VALUE))
        throw new WdkModelException("The propertyQuery " + metadataQueryRef + " in flatVocabParam " +
            getFullName() + " must have exactly three columns: " + COLUMN_TERM + ", " + COLUMN_PROPERTY +
            ", and " + COLUMN_VALUE + ".");
    }

    // resolve metadataSpec query, which should not have any param
    if (metadataSpecQueryRef != null) {
      // no need to clone metadataSpec query, since it's not overriden in any way here.
      this.metadataSpecQuery = (Query) model.resolveReference(metadataSpecQueryRef);

      // the metadataSpec query must have exactly 3 columns: property, info, data.
      Map<String, Column> columns = metadataSpecQuery.getColumnMap();
      if (columns.size() != 3 || !columns.containsKey(COLUMN_PROPERTY) ||
          !columns.containsKey(COLUMN_SPEC_PROPERTY) || !columns.containsKey(COLUMN_SPEC_VALUE))
        throw new WdkModelException("The metadataQuery " + metadataSpecQueryRef + " in flatVocabParam " +
            getFullName() + " must have exactly three columns: " + COLUMN_PROPERTY + ", " +
            COLUMN_SPEC_PROPERTY + ", and " + COLUMN_SPEC_VALUE + ".");
    }
  }

  /**
   * @return <propertyName, <infoKey, infoValue>>, or null if metadataQuery is not specified
   * @throws WdkModelException
   * @throws WdkUserException
   */
  public Map<String, Map<String, String>> getMetadataSpec(User user, Map<String, String> contextValues)
      throws WdkModelException, WdkUserException {
    if (metadataSpecQuery == null)
      return null;

    // run metadataQuery.
    QueryInstance instance = metadataSpecQuery.makeInstance(user, contextValues, true, 0,
        new HashMap<String, String>());
    Map<String, Map<String, String>> metadata = new LinkedHashMap<>();
    ResultList resultList = instance.getResults();
    try {
      while (resultList.next()) {
        String property = (String) resultList.get(COLUMN_PROPERTY);
        String info = (String) resultList.get(COLUMN_SPEC_PROPERTY);
        String data = (String) resultList.get(COLUMN_SPEC_VALUE);
        Map<String, String> propertyMeta = metadata.get(property);
        if (propertyMeta == null) {
          propertyMeta = new LinkedHashMap<>();
          metadata.put(property, propertyMeta);
        }
        propertyMeta.put(info, data);
      }
    }
    finally {
      resultList.close();
    }
    return metadata;
  }

  /**
   * @return <term, <propertyName, propertyValue>>, or null if propertyQuery is not specified.
   * @throws WdkModelException
   * @throws WdkUserException
   */
  public Map<String, Map<String, String>> getMetadata(User user, Map<String, String> contextValues)
      throws WdkModelException, WdkUserException {
    if (metadataQuery == null)
      return null;

    QueryInstance instance = metadataQuery.makeInstance(user, contextValues, true, 0,
        new HashMap<String, String>());
    Map<String, Map<String, String>> properties = new LinkedHashMap<>();
    ResultList resultList = instance.getResults();
    try {
      while (resultList.next()) {
        String term = (String) resultList.get(COLUMN_TERM);
        String property = (String) resultList.get(COLUMN_PROPERTY);
        String value = (String) resultList.get(COLUMN_VALUE);
        Map<String, String> termProp = properties.get(term);
        if (termProp == null) {
          termProp = new LinkedHashMap<>();
          properties.put(term, termProp);
        }
        termProp.put(property, value);
      }
    }
    finally {
      resultList.close();
    }
    return properties;
  }

  @Override
  public JSONObject getJsonValues(User user, Map<String, String> contextValues, EnumParamCache cache)
      throws WdkModelException, WdkUserException {
    JSONObject jsParam = super.getJsonValues(user, contextValues, cache);
    try { // add additional info into the json
      appendJsonFilterValue(jsParam, user, contextValues);
    }
    catch (JSONException ex) {
      throw new WdkModelException(ex);
    }
    return jsParam;
  }

  private void appendJsonFilterValue(JSONObject jsParam, User user, Map<String, String> contextValues)
      throws JSONException, WdkModelException, WdkUserException {
    if (metadataSpecQuery == null)
      return;

    // create json for the metadata
    JSONObject jsMetadataSpec = new JSONObject();
    Map<String, Map<String, String>> metadataSpec = getMetadataSpec(user, contextValues);
    for (String property : metadataSpec.keySet()) {
      JSONObject jsSpec = new JSONObject();
      Map<String, String> spec = metadataSpec.get(property);
      for (String specProp : spec.keySet()) {
        jsSpec.put(specProp, spec.get(specProp));
      }
      jsMetadataSpec.put(property, jsSpec);
    }
    jsParam.put("metadataSpec", jsMetadataSpec);

    // create json for the properties
    JSONObject jsMetadata = new JSONObject();
    Map<String, Map<String, String>> metadata = getMetadata(user, contextValues);
    for (String term : metadata.keySet()) {
      JSONObject jsProperty = new JSONObject();
      Map<String, String> property = metadata.get(term);
      for (String propName : property.keySet()) {
        jsProperty.put(propName, property.get(propName));
      }
      jsMetadata.put(term, jsProperty);
    }
    jsParam.put("metadata", jsMetadata);
  }

  @Override
  protected String getValidStableValue(User user, String stableValue, Map<String, String> contextValues,
      EnumParamCache cache) throws WdkModelException {
    try {
      if (stableValue == null || stableValue.length() == 0) {
        JSONArray jsTerms = convert(getDefault());
        JSONObject jsNewStableValue = new JSONObject();
        jsNewStableValue.put(FilterParamHandler.TERMS_KEY, jsTerms);
        jsNewStableValue.put(FilterParamHandler.FILTERS_KEY, new JSONArray());
        return jsNewStableValue.toString();
      }

      JSONObject jsStableValue = new JSONObject(stableValue);
      JSONArray jsTerms = jsStableValue.getJSONArray(FilterParamHandler.TERMS_KEY);
      Map<String, String> termMap = cache.getVocabMap();
      Set<String> validValues = new LinkedHashSet<>();
      for (int i = 0; i < jsTerms.length(); i++) {
        String term = jsTerms.getString(i);
        if (termMap.containsKey(term))
          validValues.add(term);
      }

      JSONArray jsNewTerms;
      if (validValues.size() > 0) {
        jsNewTerms = new JSONArray();
        for (String term : validValues) {
          jsNewTerms.put(term);
        }
      }
      else
        jsNewTerms = convert(getDefault());

      jsStableValue.put(FilterParamHandler.TERMS_KEY, jsNewTerms);
      return jsStableValue.toString();
    }
    catch (JSONException ex) {
      throw new WdkModelException(ex);
    }
  }

  private JSONArray convert(String value) {
    JSONArray jsTerms = new JSONArray();
    for (String term : value.split(",")) {
      jsTerms.put(term.trim());
    }
    return jsTerms;
  }

  @Override
  public String[] getTerms(User user, String stableValue, Map<String, String> contextValues)
      throws WdkModelException {
    if (stableValue == null || stableValue.length() == 0)
      return new String[0];

    try {
      JSONObject jsStableValue = new JSONObject(stableValue);
      JSONArray jsTerms = jsStableValue.getJSONArray(FilterParamHandler.TERMS_KEY);
      String[] terms = new String[jsTerms.length()];
      for (int i = 0; i < terms.length; i++) {
        terms[i] = jsTerms.getString(i);
      }
      return terms;
    }
    catch (JSONException ex) {
      throw new WdkModelException(ex);
    }

  }

  @Override
  public String getDefault() throws WdkModelException {
    String defaultValue = super.getDefault();
    return fixDefaultValue(defaultValue);
  }

  @Override
  public String getDefault(User user, Map<String, String> contextParamValues) throws WdkModelException {
    String defaultValue = super.getDefault(user, contextParamValues);
    return fixDefaultValue(defaultValue);
  }

  private String fixDefaultValue(String defaultValue) throws WdkModelException {
    if (defaultValue == null || defaultValue.startsWith("{"))
      return defaultValue;
    JSONObject jsStableValue = new JSONObject();
    JSONArray jsTerms = new JSONArray();
    if (defaultValue.length() > 0) {
      for (String term : defaultValue.split(",")) {
        jsTerms.put(term);
      }
    }
    try {
      jsStableValue.put(FilterParamHandler.TERMS_KEY, jsTerms);
    }
    catch (JSONException ex) {
      throw new WdkModelException(ex);
    }
    return jsStableValue.toString();
  }

  @Override
  public String[] convertToTerms(String stableValue) {
    JSONObject jsValue = new JSONObject(stableValue);
    JSONArray jsTerms = jsValue.getJSONArray(FilterParamHandler.TERMS_KEY);
    String[] terms = new String[jsTerms.length()];
    for (int i = 0; i < terms.length; i++) {
      terms[i] = jsTerms.getString(i);
    }
    return terms;
  }
}
