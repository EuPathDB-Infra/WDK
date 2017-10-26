package org.gusdb.wdk.model.query.param;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.ListBuilder;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.user.User;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * {
 *   "filters": [
 *     {
 *       "value": {
 *         "min": 1.82,
 *         "max": 2.19,
 *       },
 *       "field": "age"
 *       "includeUnknowns": true    (optional)
 *     },
 *     {
 *       "value": [
 *         "female"
 *       ],
 *       "field": "biological sex"
 *       "includeUnknowns": true    (optional)
 *     }
 *   ]
 * }
 */
public class FilterParamNewStableValue {

  private static final Logger LOG = Logger.getLogger(FilterParamNewStableValue.class);

  static final String FILTERS_KEY = "filters";
  static final String FILTERS_FIELD = "field";
  static final String FILTERS_VALUE = "value";
  static final String FILTERS_MIN = "min";
  static final String FILTERS_MAX = "max";
  static final String FILTERS_INCLUDE_UNKNOWN = "includeUnknown";
  static final String FILTERS_IS_RANGE = "isRange";
  static final String FILTERS_TYPE = "type";

  private FilterParamNew _param;
  private JSONObject _stableValueJson;
  private List<Filter> _filters = null;

  public FilterParamNewStableValue(String stableValueString, FilterParamNew param) throws WdkModelException {
    _param = param;
    try {
      _stableValueJson = new JSONObject(stableValueString);
    }
    catch (JSONException e) {
      throw new WdkModelException(e);
    }
  }

  public FilterParamNewStableValue(JSONObject stableValueJson, FilterParamNew param) {
    _param = param;
    _stableValueJson = stableValueJson;
  }

  List<Filter> getFilters() throws WdkModelException {
    initWithThrow();
    return Collections.unmodifiableList(_filters);
  }

  /**
   * validate the syntax and, for semantics, compare the field names and values against ontology and metadata
   * 
   * @return err message if any. null if valid
   */
   String validateSyntaxAndSemantics(User user, Map<String, String> contextParamValues, DataSource dataSource) throws WdkModelException {

    // validate syntax
    List<String> errors = new ArrayList<String>();
    String errmsg = validateSyntax();

    if (errmsg != null) errors.add(errmsg);
    
    // validate fields against ontology; collect fields that are not isRange
    Map<String, OntologyItem> ontology = _param.getOntology(user, contextParamValues);
    Set<MembersFilter> memberFilters = new HashSet<MembersFilter>();
    for (Filter filter : _filters) {
      String field = filter.getField();
      if (!ontology.containsKey(field)) {
        errors.add("'" + field + "'" + " is not a recognized ontology term");
        continue;
      }
      if (!ontology.get(field).getIsRange()) memberFilters.add((MembersFilter)filter);
    }
    
    // run metadata query to find distinct values for each member field
    if (memberFilters.size() != 0) {
      Set<String> relevantOntologyTerms = memberFilters.stream().map(FilterParamNewStableValue.MembersFilter::getField).collect(Collectors.toSet());
      Map<String, Set<String>> metadataMembers = _param.getDistinctMetaDataValues(user, contextParamValues,
          relevantOntologyTerms, ontology, dataSource);

      // iterate through our member filters, validating the values of each
      for (MembersFilter mf : memberFilters) {
        String err = mf.validateValues(metadataMembers.get(mf.getField()));
        if (err != null)
          errors.add(err);
      }
    }

    if (errors.size() != 0) return errors.stream().collect(Collectors.joining("', '"));
    return null;
  }
   

  /**
   * validate the syntax. does not validate semantics (ie, compare against ontology).
   * 
   * @return err message if any. null if valid
   */
  String validateSyntax() {
    return init();
  }

  String toSignatureString() throws WdkModelException {
    initWithThrow();
    List<String> filterSigs = new ArrayList<String>();
    for (Filter filter : getFilters()) {
      filterSigs.add(filter.getSignature());
    }
    Collections.sort(filterSigs);
    return "[" + FormatUtil.join(filterSigs, ",") + "]";
  }

  private void initWithThrow() throws WdkModelException {
    String errmsg = init();
    if (errmsg != null) throw new WdkModelException(errmsg);
  }

  /**
   * @return error message if any; null if valid
   */
  private String init() {

    if (_filters == null) {
      _filters = new ArrayList<Filter>();
      try {

        JSONArray jsFilters = _stableValueJson.getJSONArray(FILTERS_KEY);
        if (jsFilters == null) {
          return "Stable value for parameter " + _param.getFullName() + " missing the array: " + FILTERS_KEY;
        }

        for (int i = 0; i < jsFilters.length(); i++) {

          JSONObject jsFilter = jsFilters.getJSONObject(i);

          if (!jsFilter.has(FILTERS_INCLUDE_UNKNOWN) && !jsFilter.has(FILTERS_VALUE)) {
            return "A value filter must have at minimum one of" + " the following properties: '" +
                FILTERS_INCLUDE_UNKNOWN + "', '" + FILTERS_VALUE + "'.";
          }

          String field = jsFilter.getString(FILTERS_FIELD);
          if (field == null) {
            return "Stable value for parameter " + _param.getFullName() + " does not specify an ontology term";
          }

          boolean isRange = inferIsRange(jsFilter);
          OntologyItemType type = (isRange ?
              inferRangeType(jsFilter.getJSONObject(FILTERS_VALUE)) :
              inferMemberType(jsFilter.getJSONArray(FILTERS_VALUE)));

          Filter filter = null;
          Boolean includeUnknowns = jsFilter.isNull(FILTERS_INCLUDE_UNKNOWN) ? false : jsFilter.getBoolean(FILTERS_INCLUDE_UNKNOWN);

          try {
            switch (type) {
              case DATE:
                filter = new DateRangeFilter(jsFilter.getJSONObject(FILTERS_VALUE), includeUnknowns, field);
                break;
              case NUMBER:
                filter = (isRange ?
                    new NumberRangeFilter(jsFilter.getJSONObject(FILTERS_VALUE), includeUnknowns, field) :
                    new NumberMembersFilter(jsFilter.getJSONArray(FILTERS_VALUE), includeUnknowns, field));
                break;
              case STRING:
                filter = new StringMembersFilter(jsFilter.getJSONArray(FILTERS_VALUE), includeUnknowns, field);
                break;
              default:
                throw new WdkModelException("Unsupported filter type: " + type.name());
            }
          }
          catch (WdkModelException e) {
            LOG.error("Could not create filter from JSON value.", e);
            return e.getMessage();
          }

          _filters.add(filter);
        }
        Collections.sort(_filters);
      }
      catch (JSONException e) {
        return "Invalid stable value. Can't parse JSON. " + e.getMessage();
      }
    }
    return null;
  }

  // temporary hack
  private boolean inferIsRange(JSONObject jsFilter) {
    boolean isRange = true;
    try {
      if (!jsFilter.isNull(FILTERS_VALUE)) jsFilter.getJSONObject(FILTERS_VALUE);
    }
    catch (JSONException e) {
      isRange = false;
    }
    return isRange;
  }

  // temporary hack
  private OntologyItemType inferRangeType(JSONObject jsValue) {
    OntologyItemType type = OntologyItemType.NUMBER;
    try {
      // use JSONException to expose date type (i.e. not number type)
      if (!jsValue.isNull(FILTERS_MIN)) jsValue.getDouble(FILTERS_MIN);
      if (!jsValue.isNull(FILTERS_MAX)) jsValue.getDouble(FILTERS_MAX);
    }
    catch (JSONException e) {
      type = OntologyItemType.DATE;
    }
    return type;
  }

  // temporary hack
  private OntologyItemType inferMemberType(JSONArray jsValue) {
    OntologyItemType type = OntologyItemType.NUMBER;
    try {
      if (!jsValue.isNull(0)) jsValue.getDouble(0);
    }
    catch (JSONException e) {
      type = OntologyItemType.STRING;
    }
    return type;
  }

  /**
   * @param user  
   * @param contextParamValues 
   */
  String getDisplayValue(User user, Map<String, String> contextParamValues) throws WdkModelException {

    initWithThrow();

    String displayValue;
    if (_filters.size() == 0) {
      displayValue = "unspecified";
    }
    else {

      List<String> filterDisplays = new ArrayList<String>();
      for (Filter filter : _filters) {
        filterDisplays.add(
            filter.getDisplayValue() + (filter.getIncludeUnknowns() ? " (include unknowns)" : ""));
      }
      displayValue = FormatUtil.join(filterDisplays, System.lineSeparator());
    }
    return displayValue;
  }

  //////////////////// inner classes to represent different types of filter //////////////////////////

  abstract class Filter implements Comparable<Filter> {

    String field = null; // the stable value did not include a value field
    Boolean includeUnknowns = null;
    boolean valueIsNull;

    public Filter(boolean valueIsNull, Boolean includeUnknowns, String field) {
      this.valueIsNull = valueIsNull;
      this.includeUnknowns = includeUnknowns;
      this.field = field;
    }

    public String getField() {
      return field;
    }

    abstract String getDisplayValue();

    abstract Boolean getIncludeUnknowns();

    protected abstract String getValueSqlClause(String columnName, String metadataTableName) throws WdkModelException;

    abstract String getSignature();

    // include in where clause a filter by ontology_id
    String getFilterAsWhereClause(String metadataTableName, Map<String, OntologyItem> ontology) throws WdkModelException {

      OntologyItem ontologyItem = ontology.get(field);
      OntologyItemType type = ontologyItem.getType();
      String columnName = type.getMetadataQueryColumn();

      String whereClause = " WHERE " + FilterParamNew.COLUMN_ONTOLOGY_ID + " = '" + ontologyItem.getOntologyId() + "'";

      String unknownClause = includeUnknowns ? metadataTableName + "." + columnName + " is NULL OR " : " 1=0 OR ";

      String innerAndClause = valueIsNull ?
          metadataTableName + "." + columnName + " is not NULL" :
          getValueSqlClause(columnName, metadataTableName);

      // at least one of `unknownClause` or `innerAndClause` will be non-empty, due to validation check above.
      return whereClause + " AND (" + unknownClause + innerAndClause + ")";
    }
    
    @Override
    public int compareTo(Filter f) {
      return field.compareTo(f.getField());
    }
  }

  abstract class RangeFilter extends Filter {

    /**
     * 
     * @param jsValue
     *          the FILTERS_VALUE portion of the stable value
     * @param includeUnknowns
     * @param field
     */
    RangeFilter(JSONObject jsValue, Boolean includeUnknowns, String field) {
      super(jsValue == null, includeUnknowns, field);

      /*
       * if (jsValue != null && jsValue.isNull(FILTERS_MAX) && jsValue.isNull(FILTERS_MIN)) throw new
       * WdkModelException("Stable value for parameter " + _param.getFullName() + " " + FILTERS_FIELD + " " +
       * field + "has no " + FILTERS_MIN + " or " + FILTERS_MAX);
       */
    }

    @Override
    String getDisplayValue() {
      String min = getMinString();
      String max = getMaxString();
      return min == null ?
          (max == null ? "any values" : "less than " + max) :
          (max == null ? "greater than " + min : "between " + min + " and " + max);
    }

    @Override
    Boolean getIncludeUnknowns() {
      return includeUnknowns;
    }

    @Override
    protected String getValueSqlClause(String columnName, String metadataTableName) throws WdkModelException {

      String minStr = getMinStringSql();
      String maxStr = getMaxStringSql();
      String rowValue = metadataTableName + "." + columnName;
      List<String> conditions = new ListBuilder<String>()
        .addIf(minStr != null, rowValue + " >= " + minStr)
        .addIf(maxStr != null, rowValue + " <= " + maxStr)
        .toList();
      return (minStr == null && maxStr == null ?
          " 1=1 " : // return "true" because we want to retain all values (i.e. no filter on existing range)
          " ( " + FormatUtil.join(conditions, " AND ") + " ) ");
    }

    @Override
    String getSignature() {
      return "" + getMinString() + "," + getMaxString() + "," + includeUnknowns;
    }

    abstract String getMaxString();

    abstract String getMinString();

    abstract String getMaxStringSql();

    abstract String getMinStringSql();
  }

  private class DateRangeFilter extends RangeFilter {

    private String min = null;
    private String max = null;

    DateRangeFilter(JSONObject jsValue, Boolean includeUnknowns, String field) throws WdkModelException {
      super(jsValue, includeUnknowns, field);
      try {
        if (!jsValue.isNull(FILTERS_MIN)) min = jsValue.getString(FILTERS_MIN);
        if (!jsValue.isNull(FILTERS_MAX)) max = jsValue.getString(FILTERS_MAX);
      }
      catch (JSONException j) {
        throw new WdkModelException(j);
      }
    }

    @Override
    String getMinString() {
      return min;
    }

    @Override
    String getMaxString() {
      return max;
    }

    @Override
    String getMinStringSql() {
      return min == null ? null : "date '" + min + "'";
    }

    @Override
    String getMaxStringSql() {
      return max == null ? null : "date '" + max + "'";
    }

  }

  private class NumberRangeFilter extends RangeFilter {

    private Double min = null;
    private Double max = null;

    NumberRangeFilter(JSONObject jsValue, Boolean includeUnknowns, String field) throws WdkModelException {
      super(jsValue, includeUnknowns, field);
      try {
        if (!jsValue.isNull(FILTERS_MIN)) min = jsValue.getDouble(FILTERS_MIN);
        if (!jsValue.isNull(FILTERS_MAX)) max = jsValue.getDouble(FILTERS_MAX);
      }
      catch (JSONException j) {
        throw new WdkModelException(j);
      }
    }

    @Override
    String getMinString() {
      return min == null ? null : min.toString();
    }

    @Override
    String getMaxString() {
      return max == null ? null : max.toString();
    }

    @Override
    String getMinStringSql() {
      return min == null ? null : min.toString();
    }

    @Override
    String getMaxStringSql() {
      return max == null ? null : max.toString();
    }

  }

  abstract class MembersFilter extends Filter {

    MembersFilter(JSONArray jsArray, Boolean includeUnknowns, String field) throws WdkModelException {

      super(jsArray == null, includeUnknowns, field);

      if (jsArray != null) {
        try {
          setMembers(jsArray);
        }
        catch (JSONException j) {
          throw new WdkModelException(j);
        }
      }
    }

    @Override
    Boolean getIncludeUnknowns() {
      return includeUnknowns;
    }

    /**
     * @return String with list of error values; null if no errors
     */
    String validateValues(Set<String> validValuesMap) {
      List<String> errList = new ArrayList<String>();
      for (String value : getMembersAsStrings()) if (!validValuesMap.contains(value)) errList.add(value);
      if (errList.size() != 0) return FormatUtil.join(errList, ", ");
      return null;
    }

   abstract void setMembers(JSONArray jsArray) throws JSONException;
   
   abstract List<String> getMembersAsStrings();

  }

  private class NumberMembersFilter extends MembersFilter {

    private List<Double> members;

    NumberMembersFilter(JSONArray jsArray, Boolean includeUnknowns, String field) throws WdkModelException {
      super(jsArray, includeUnknowns, field);
    }

    @Override
    void setMembers(JSONArray jsArray) throws JSONException {
      members = new ArrayList<Double>();
      for (int i = 0; i < jsArray.length(); i++) {
        members.add(jsArray.getDouble(i));
      }
      Collections.sort(members);  // sort for stability.  required for caching.
    }

    @Override
    String getDisplayValue() {
      Collections.sort(members);
      return FormatUtil.join(members, ",");
    }

    @Override
    protected String getValueSqlClause(String columnName, String metadataTableName) {
      if (members.size() == 0) return "1 != 1";
      return metadataTableName + "." + columnName + " IN (" + FormatUtil.join(members, ", ") + ") ";
    }

    @Override
    String getSignature() {
      List<Double> list = new ArrayList<Double>(members);
      Collections.sort(list);
      return FormatUtil.join(list, ",") + " --" + includeUnknowns;
    }

    @Override
    List<String> getMembersAsStrings() {
      List<String> list = new ArrayList<String>();
      for (Double mem : members) list.add(mem.toString());
      return list;
    }

  }

  private class StringMembersFilter extends MembersFilter {

    private List<String> members;

    StringMembersFilter(JSONArray jsArray, Boolean includeUnknowns, String field)
        throws WdkModelException {
      super(jsArray, includeUnknowns, field);
    }

    @Override
    void setMembers(JSONArray jsArray) throws JSONException {
      members = new ArrayList<String>();
      for (int i = 0; i < jsArray.length(); i++) {
        members.add(jsArray.getString(i));
      }
      Collections.sort(members);
    }

    @Override
    String getDisplayValue() {
      return FormatUtil.join(members, ",");
    }

    @Override
    protected String getValueSqlClause(String columnName, String metadataTableName) {
      if (members.size() == 0) return "1 != 1";
      return metadataTableName + "." + columnName + " IN ('" + FormatUtil.join(members, "', '") + "') ";
    }

    @Override
    String getSignature() {
      List<String> list = new ArrayList<String>(members);
      Collections.sort(list);
      return FormatUtil.join(list, ",") + " --" + includeUnknowns;
    }

    @Override
    List<String> getMembersAsStrings() {
      return members;
    }

  }
}
