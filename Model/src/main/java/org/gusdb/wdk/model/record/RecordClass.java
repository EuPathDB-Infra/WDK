package org.gusdb.wdk.model.record;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.MapBuilder;
import org.gusdb.fgputil.Named;
import org.gusdb.fgputil.db.platform.DBPlatform;
import org.gusdb.fgputil.functional.Functions;
import org.gusdb.wdk.model.Reference;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelBase;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.analysis.StepAnalysis;
import org.gusdb.wdk.model.analysis.StepAnalysisXml;
import org.gusdb.wdk.model.answer.AnswerFilter;
import org.gusdb.wdk.model.answer.AnswerFilterInstance;
import org.gusdb.wdk.model.answer.AnswerFilterLayout;
import org.gusdb.wdk.model.answer.SummaryView;
import org.gusdb.wdk.model.filter.ColumnFilter;
import org.gusdb.wdk.model.filter.Filter;
import org.gusdb.wdk.model.filter.FilterDefinition;
import org.gusdb.wdk.model.filter.FilterReference;
import org.gusdb.wdk.model.filter.StepFilter;
import org.gusdb.wdk.model.filter.StepFilterDefinition;
import org.gusdb.wdk.model.query.BooleanQuery;
import org.gusdb.wdk.model.query.Column;
import org.gusdb.wdk.model.query.ColumnType;
import org.gusdb.wdk.model.query.Query;
import org.gusdb.wdk.model.query.SqlQuery;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.query.param.ParamSet;
import org.gusdb.wdk.model.query.param.ParamValuesSet;
import org.gusdb.wdk.model.query.param.StringParam;
import org.gusdb.wdk.model.question.AttributeList;
import org.gusdb.wdk.model.question.CategoryList;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.question.QuestionSet;
import org.gusdb.wdk.model.record.attribute.AttributeCategory;
import org.gusdb.wdk.model.record.attribute.AttributeCategoryTree;
import org.gusdb.wdk.model.record.attribute.AttributeField;
import org.gusdb.wdk.model.record.attribute.AttributeFieldContainer;
import org.gusdb.wdk.model.record.attribute.DerivedAttributeField;
import org.gusdb.wdk.model.record.attribute.IdAttributeField;
import org.gusdb.wdk.model.record.attribute.PkColumnAttributeField;
import org.gusdb.wdk.model.record.attribute.QueryColumnAttributeField;
import org.gusdb.wdk.model.report.ReporterRef;
import org.gusdb.wdk.model.test.sanity.OptionallyTestable;
import org.gusdb.wdk.model.user.BasketFactory;
import org.gusdb.wdk.model.user.FavoriteReference;
import org.gusdb.wdk.model.user.User;
import org.gusdb.wdk.model.user.UserPreferences;

/**
 * <p>
 * RecordClass is the core entity in WDK, and it defined the type of the data that is presented in WDK driven
 * system.
 * </p>
 * 
 * <p>
 * Records are normally retrieved by running questions, and each question is associated with one recordClass
 * type.
 * </p>
 * 
 * <p>
 * A recordClass defines the attribute fields and table fields for records, and for a given primary key,
 * a RecordInstance can be instantiated, and the instance will holds attribute values and table values.
 * </p>
 * 
 * <p>
 * A record can have multiple attributes, but for each attribute, it can have only one value; the tables can
 * have multiple attributes, and each attribute might have zero or more values. Please refer to the
 * AttributeQueryReference and TableQueryReference for details about defining the attribute and table queries.
 * </p>
 * 
 * @author jerric
 */
public class RecordClass extends WdkModelBase implements AttributeFieldContainer, OptionallyTestable {

  private static final Logger logger = Logger.getLogger(RecordClass.class);

  private static final Set<Character> VOWELS = new HashSet<>(Arrays.asList('a', 'e', 'i', 'o', 'u'));

  /**
   * This method takes in a bulk attribute or table query, and adds the primary key columns as params into the
   * SQL, and return the a Query with the params.
   * 
   * @param wdkModel
   * @param query
   * @param paramNames
   * @return
   * @throws WdkModelException
   */
  public static Query prepareQuery(WdkModel wdkModel, Query query, String[] paramNames)
      throws WdkModelException {
    Map<String, Column> columns = query.getColumnMap();
    Map<String, Param> originalParams = query.getParamMap();
    Query newQuery = query.clone();
    // do not cache the single-line query
    newQuery.setIsCacheable(false);

    // find the new params to be created
    List<String> newParams = new ArrayList<String>();
    for (String column : paramNames) {
      if (!originalParams.containsKey(column))
        newParams.add(column);
    }

    // create the missing primary key params for the query
    ParamSet paramSet = wdkModel.getParamSet(Utilities.INTERNAL_PARAM_SET);
    for (String columnName : newParams) {
      StringParam param;
      if (paramSet.contains(columnName)) {
        param = (StringParam) paramSet.getParam(columnName);
      }
      else {
        param = new StringParam();
        Column column = columns.get(columnName);
        ColumnType type = column.getType();
        boolean number = !type.isText();
        param.setName(columnName);
        param.setNumber(number);
        // param.setAllowEmpty(true);

        param.excludeResources(wdkModel.getProjectId());
        param.resolveReferences(wdkModel);
        param.setResources(wdkModel);
        paramSet.addParam(param);
      }
      newQuery.addParam(param);
    }

    // if the new query is SqlQuery, modify the sql
    if (newQuery instanceof SqlQuery && newParams.size() > 0) {
      StringBuilder builder = new StringBuilder("SELECT f.* FROM (");
      builder.append(((SqlQuery) newQuery).getSql());
      builder.append(") f WHERE ");
      boolean firstColumn = true;
      for (String columnName : newParams) {
        if (firstColumn)
          firstColumn = false;
        else
          builder.append(" AND ");
        builder.append("f.").append(columnName);
        builder.append(" = $$").append(columnName).append("$$");
      }

      // replace the id_sql macro
      StringBuilder idqBuilder = new StringBuilder();
      for (String column : paramNames) {
        if (idqBuilder.length() == 0)
          idqBuilder.append("(SELECT ");
        else
          idqBuilder.append(", ");
        idqBuilder.append("SUBSTR($$" + column + "$$, 1, 4000) AS " + column);
      }
      DBPlatform platform = wdkModel.getAppDb().getPlatform();
      idqBuilder.append(platform.getDummyTable());
      idqBuilder.append(")");

      String idSql = idqBuilder.toString();
      String sql = builder.toString();
      sql = sql.replace(Utilities.MACRO_ID_SQL, idSql);
      sql = sql.replace(Utilities.MACRO_ID_SQL_NO_FILTERS, idSql);

      ((SqlQuery) newQuery).setSql(sql);
    }
    return newQuery;
  }

  private RecordClassSet recordClassSet;
  
  private List<AttributeQueryReference> attributesQueryRefList = new ArrayList<AttributeQueryReference>();

  private Map<String, Query> attributeQueries = new LinkedHashMap<String, Query>();
  private Map<String, Query> tableQueries = new LinkedHashMap<String, Query>();

  private PrimaryKeyDefinition primaryKeyDefinition;
  private IdAttributeField idAttributeField;

  private List<AttributeField> attributeFieldList = new ArrayList<AttributeField>();
  private Map<String, AttributeField> attributeFieldsMap = new LinkedHashMap<String, AttributeField>();

  private List<TableField> tableFieldList = new ArrayList<TableField>();
  private Map<String, TableField> tableFieldsMap = new LinkedHashMap<String, TableField>();

  private String name;
  private String fullName;

  /**
   * the native versions are the real name of the record class.  the non-native are potentially different,
   * for display purposes.  This can happen if a ResultSizeQueryReference is supplied, that provides non-native
   * result counts and display names
   */
  private String nativeDisplayName;
  private String nativeDisplayNamePlural;
  private String nativeShortDisplayName;
  private String nativeShortDisplayNamePlural;
  private String displayName;
  private String description;
  private String displayNamePlural;
  private String shortDisplayName;
  private String shortDisplayNamePlural;

  /**
   * An option that provides SQL to post-process an Answer result, providing a custom result size count. 
   * If present, induces construction of a non-default result size plugin that uses this sql
   */
  private ResultSizeQueryReference resultSizeQueryRef = null;

  /**
   * An option that provides SQL to post-process an Answer result, providing a custom property value. 
   */
  private ResultPropertyQueryReference resultPropertyQueryRef = null;

  /**
   * A pluggable way to compute the result size.  For example, count the number of genes in a list of transcripts.
   * The default is overridden with a plugin supplied in the XML model, if provided.
   */
  private ResultSize resultSizePlugin = new DefaultResultSizePlugin();
  
  /**
   * A pluggable way to compute a result property.  For example, count the number of genes in a list of transcripts that are missing transcripts.
   */
  private ResultProperty resultPropertyPlugin = null;

  private String customBooleanQueryClassName = null;
  
  private BooleanQuery booleanQuery;
  
  private String attributeOrdering;

  private AttributeCategoryTree attributeCategoryTree;

  // for sanity testing
  private boolean doNotTest = false;
  private List<ParamValuesSet> unexcludedParamValuesSets = new ArrayList<ParamValuesSet>();
  private ParamValuesSet paramValuesSet;

  private List<ReporterRef> reporterList = new ArrayList<ReporterRef>();
  private Map<String, ReporterRef> reporterMap = new LinkedHashMap<String, ReporterRef>();

  private List<AnswerFilter> filterList = new ArrayList<AnswerFilter>();
  private Map<String, AnswerFilterInstance> filterMap = new LinkedHashMap<String, AnswerFilterInstance>();

  private List<AnswerFilterLayout> filterLayoutList = new ArrayList<AnswerFilterLayout>();
  private Map<String, AnswerFilterLayout> filterLayoutMap = new LinkedHashMap<String, AnswerFilterLayout>();

  private AnswerFilterInstance defaultFilter;
  /**
   * If the filter is set, in all the boolean operations of the record page, the operands will first be
   * filtered by this filter, and then the results of these will be used in boolean operation.
   */
  private AnswerFilterInstance booleanExpansionFilter;

  private List<AttributeList> attributeLists = new ArrayList<AttributeList>();

  private String[] defaultSummaryAttributeNames;
  private Map<String, AttributeField> defaultSummaryAttributeFields = new LinkedHashMap<String, AttributeField>();
  private Map<String, Boolean> defaultSortingMap = new LinkedHashMap<String, Boolean>();

  /**
   * if true, the basket feature will be turn on for the records of this type.
   */
  private boolean useBasket = true;

  private List<FavoriteReference> favorites = new ArrayList<FavoriteReference>();
  private String favoriteNoteFieldName;
  private AttributeField favoriteNoteField;

  private List<SummaryView> summaryViewList = new ArrayList<>();
  private Map<String, SummaryView> summaryViewMap = new LinkedHashMap<>();

  private List<RecordView> recordViewList = new ArrayList<>();
  private Map<String, RecordView> recordViewMap = new LinkedHashMap<>();

  private List<StepAnalysisXml> stepAnalysisList = new ArrayList<>();
  private Map<String, StepAnalysis> stepAnalysisMap = new LinkedHashMap<>();

  private List<FilterReference> _filterReferences = new ArrayList<>();
  private Map<String, StepFilter> _stepFilters = new LinkedHashMap<>();
  
  private CategoryList _categoryList;

  private String _urlSegment;

  // ////////////////////////////////////////////////////////////////////
  // Called at model creation time
  // ////////////////////////////////////////////////////////////////////

  @Override
  public WdkModel getWdkModel() {
    return _wdkModel;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDisplayName() {
    return (displayName == null) ? getName() : displayName;
  }

  public String getNativeDisplayName() {
    return (nativeDisplayName == null) ? getName() : nativeDisplayName;
  }

  public String getDescription() {
    return (description == null) ? "" : description;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
    this.nativeDisplayName = displayName;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getDisplayNamePlural() {
    if (displayNamePlural != null)
      return displayNamePlural;

    return getPlural(getDisplayName());
  }

  public String getNativeDisplayNamePlural() {
      if (nativeDisplayNamePlural != null)
        return nativeDisplayNamePlural;

      return getPlural(getNativeDisplayName());
    }

  public void setDisplayNamePlural(String displayNamePlural) {
    this.displayNamePlural = displayNamePlural;
    this.nativeDisplayNamePlural = displayNamePlural;
  }

  public String getShortDisplayNamePlural() {
    if (shortDisplayNamePlural != null)
      return shortDisplayNamePlural;

    return getPlural(getShortDisplayName());
  }

  public String getNativeShortDisplayNamePlural() {
      if (nativeShortDisplayNamePlural != null)
        return nativeShortDisplayNamePlural;

      return getPlural(getNativeShortDisplayName());
    }

  public void setShortDisplayNamePlural(String shortDisplayNamePlural) {
    this.shortDisplayNamePlural = shortDisplayNamePlural;
    this.nativeShortDisplayNamePlural = shortDisplayNamePlural;
  }

  public void setUrlName(String urlName) {
    // XML Model alias for URL segment
    setUrlSegment(urlName);
  }

  public void setUrlSegment(String urlSegment) {
    this._urlSegment = urlSegment;
  }
  public String getUrlSegment() {
    return this._urlSegment;
  }

  private static String getPlural(String recordClassName) {
    if (recordClassName == null || recordClassName.length() == 0)
      return recordClassName;

    int length = recordClassName.length();
    char last = recordClassName.charAt(length - 1);
    if (last == 'o')
      return recordClassName + "es";
    if (last == 'y') {
      char second = recordClassName.charAt(length - 2);
      if (!VOWELS.contains(second))
        return recordClassName.substring(0, length - 1) + "ies";
    }
    return recordClassName + "s";
  }
  
  public ResultSize getResultSizePlugin() {
    return resultSizePlugin;
  }

  public ResultProperty getResultPropertyPlugin() {
    return resultPropertyPlugin;
  }
  
  public String getCustomBooleanQueryClassName() {
    return customBooleanQueryClassName;
  }

  public void setAttributeOrdering(String attOrder) {
    this.attributeOrdering = attOrder;
  }

  public void setPrimaryKeyDefinition(PrimaryKeyDefinition primaryKeyDefinition) {
    this.primaryKeyDefinition = primaryKeyDefinition;
    this.primaryKeyDefinition.setRecordClass(this);
  }

  public PrimaryKeyDefinition getPrimaryKeyDefinition() {
    return primaryKeyDefinition;
  }

  /**
   * @param attributesQueryRef
   *          two part query name (set.name)
   */
  public void addAttributesQueryRef(AttributeQueryReference attributesQueryRef) {
    attributesQueryRefList.add(attributesQueryRef);
  }

  public void addAttributeField(AttributeField attributeField) {
    assignFieldParent(attributeField);
    attributeFieldList.add(attributeField);
  }

  public void addTableField(TableField tableField) {
    tableField.setRecordClass(this);
    tableFieldList.add(tableField);
  }

  public void addReporterRef(ReporterRef reporter) {
    reporterList.add(reporter);
  }
  
  public void setDoNotTest(boolean doNotTest) {
    this.doNotTest = doNotTest;
  }

  @Override
  public boolean getDoNotTest() {
    return doNotTest;
  }

  public void addParamValuesSet(ParamValuesSet newParamValuesSet) {
    unexcludedParamValuesSets.add(newParamValuesSet);
  }

  public ParamValuesSet getParamValuesSet() {
    return paramValuesSet == null ? new ParamValuesSet() : paramValuesSet;
  }

  public void setAttributeCategoryTree(AttributeCategoryTree tree) {
    attributeCategoryTree = tree;
  }
  
  public void setResultSizeQueryRef(ResultSizeQueryReference ref) {
    resultSizeQueryRef = ref;
  }
  
  public void setResultPropertyQueryRef(ResultPropertyQueryReference ref) {
    resultPropertyQueryRef = ref;
  }
  
  public void setCustomBooleanQueryClassName(String className) {
    this.customBooleanQueryClassName = className;
  }

  // ////////////////////////////////////////////////////////////
  // public getters
  // ////////////////////////////////////////////////////////////

  public String getName() {
    return name;
  }

  public String getFullName() {
    return fullName;
  }

  public Map<String, TableField> getTableFieldMap() {
    return getTableFieldMap(FieldScope.ALL);
  }

  public Map<String, TableField> getTableFieldMap(FieldScope scope) {
    Map<String, TableField> fields = new LinkedHashMap<String, TableField>();
    for (TableField field : tableFieldsMap.values()) {
      if (scope.isFieldInScope(field)) {
        fields.put(field.getName(), field);
      }
    }
    return fields;
  }

  // used by report maker, adding display names in map so later the tables show sorted by display name
  public Map<String, TableField> getTableFieldMap(FieldScope scope, boolean useDisplayNamesAsKeys) {
    if (!useDisplayNamesAsKeys) {
      return getTableFieldMap(scope);
    }
    Map<String, TableField> fields = new LinkedHashMap<String, TableField>();
    for (TableField field : tableFieldsMap.values()) {
      if (scope.isFieldInScope(field)) {
        fields.put(field.getDisplayName(), field);
      }
    }
    return fields;
  }

  public TableField[] getTableFields() {
    Map<String, TableField> tables = getTableFieldMap();
    TableField[] array = new TableField[tables.size()];
    tables.values().toArray(array);
    return array;
  }

  @Override
  public Map<String, AttributeField> getAttributeFieldMap() {
    return getAttributeFieldMap(FieldScope.ALL);
  }

  public Map<String, AttributeField> getAttributeFieldMap(FieldScope scope) {
    Map<String, AttributeField> fields = new LinkedHashMap<String, AttributeField>();

    // always put primary key field as the first one
    fields.put(idAttributeField.getName(), idAttributeField);

    for (AttributeField field : attributeFieldsMap.values()) {
      if (scope.isFieldInScope(field)) {
        fields.put(field.getName(), field);
      }
    }
    return fields;
  }

  @Override
  public AttributeField[] getAttributeFields() {
    Map<String, AttributeField> attributes = getAttributeFieldMap();
    AttributeField[] array = new AttributeField[attributes.size()];
    attributes.values().toArray(array);
    return array;
  }

  public Field[] getFields() {
    int attributeCount = attributeFieldsMap.size();
    int tableCount = tableFieldsMap.size();
    Field[] fields = new Field[attributeCount + tableCount];
    // copy attribute fields
    attributeFieldsMap.values().toArray(fields);
    // copy table fields
    TableField[] tableFields = getTableFields();
    System.arraycopy(tableFields, 0, fields, attributeCount, tableCount);
    return fields;
  }

  public Reference getReference() throws WdkModelException {
    return new Reference(getFullName());
  }

  public Map<String, ReporterRef> getReporterMap() {
    return new LinkedHashMap<String, ReporterRef>(reporterMap);
  }

  public AttributeCategoryTree getAttributeCategoryTree(FieldScope scope) {
    return attributeCategoryTree.getTrimmedCopy(scope);
  }
  
  public ResultSizeQueryReference getResultSizeQueryRef() {
    return resultSizeQueryRef;  
  }
  
  public ResultPropertyQueryReference getResultPropertyQueryRef() {
    return resultPropertyQueryRef;  
  }
  
  public BooleanQuery getBooleanQuery() {
    return booleanQuery;
  }

  @Override
  public String toString() {
    String newline = System.getProperty("line.separator");
    StringBuffer buf = new StringBuffer("Record: name='" + name + "'").append(newline);

    buf.append("--- Attribute Category Tree (with attribute count per category) ---").append(newline);
    buf.append(attributeCategoryTree);

    buf.append("--- Attributes ---").append(newline);
    for (AttributeField attribute : attributeFieldsMap.values()) {
      buf.append(attribute.getName()).append(newline);
    }

    buf.append("--- Tables ---").append(newline);
    for (TableField table : tableFieldsMap.values()) {
      buf.append(table.getName()).append(newline);
    }
    return buf.toString();
  }

  /*
   * <sanityRecord ref="GeneRecordClasses.GeneRecordClass" primaryKey="PF11_0344"/>
   */
  public String getSanityTestSuggestion() {
    String indent = "    ";
    String newline = System.getProperty("line.separator");
    StringBuffer buf = new StringBuffer(newline + newline + indent + "<sanityRecord ref=\"" + getFullName() +
        "\"" + newline + indent + indent + indent + "primaryKey=\"FIX_pk\">" + newline);
    buf.append(indent + "</sanityRecord>");
    return buf.toString();
  }

  // /////////////////////////////////////////////////////////////////////////
  // package scope methods
  // /////////////////////////////////////////////////////////////////////////

  /**
   * @param recordSetName
   *          name of the recordSet to which this record belongs.
   */
  void setRecordClassSet(RecordClassSet recordClassSet) {
    this.recordClassSet = recordClassSet;
    this.fullName = recordClassSet.getName() + "." + name;
  }

  public RecordClassSet getRecordClassSet() {
    return recordClassSet;
  }

  Query getAttributeQuery(String queryFullName) {
    return attributeQueries.get(queryFullName);
  }

  public Map<String, Query> getAttributeQueries() {
    return new LinkedHashMap<String, Query>(attributeQueries);
  }

  AttributeField getAttributeField(String attributeName) throws WdkModelException {
    AttributeField attributeField = attributeFieldsMap.get(attributeName);
    if (attributeField == null) {
      String message = "RecordClass " + getName() + " doesn't have an attribute field with name '" +
          attributeName + "'.";
      throw new WdkModelException(message);
    }
    return attributeField;
  }

  public Map<String, Query> getTableQueries() {
    return new LinkedHashMap<String, Query>(tableQueries);
  }

  TableField getTableField(String tableName) throws WdkModelException {
    TableField tableField = tableFieldsMap.get(tableName);
    if (tableField == null) {
      String message = "Record " + getName() + " does not have a table field with name '" + tableName + "'.";
      throw new WdkModelException(message);
    }
    return tableField;
  }

  @Override
  public void resolveReferences(WdkModel model) throws WdkModelException {
    if (_resolved)
      return;
    super.resolveReferences(model);
    this._wdkModel = model;

    if (name.length() == 0 || name.indexOf('\'') >= 0)
      throw new WdkModelException("recordClass name cannot be empty or " + "having single quotes: " + name);

    // resolve primary key references
    primaryKeyDefinition.resolveReferences(model);

    // resolve the references for attribute queries
    resolveAttributeQueryReferences(model);

    // create column attribute fields for primary key columns if they don't already exist
    createPrimaryKeySubFields(model.getProjectId());

    // resolve references for the attribute fields
    for (AttributeField field : attributeFieldsMap.values()) {
      try {
        field.resolveReferences(model);
      }
      catch (WdkModelException e) {
        throw new WdkModelException("Unable to resolve reference of field '" + field.getName() +
            "' in RecordClass '" + getFullName() + "'.", e);
      }
    }

    if (resultSizeQueryRef != null) {
      resultSizeQueryRef.resolveReferences(model);
      displayName = resultSizeQueryRef.getRecordDisplayName();
      shortDisplayName = resultSizeQueryRef.getRecordShortDisplayName();
      displayNamePlural = resultSizeQueryRef.getRecordDisplayNamePlural();
      shortDisplayNamePlural = resultSizeQueryRef.getRecordShortDisplayNamePlural();
        Query query = (Query) _wdkModel.resolveReference(resultSizeQueryRef.getTwoPartName());
      resultSizePlugin = new SqlQueryResultSizePlugin(query);
    }
    
    if (resultPropertyQueryRef != null) {
      resultPropertyQueryRef.resolveReferences(model);
        Query query = (Query) _wdkModel.resolveReference(resultPropertyQueryRef.getTwoPartName());
      resultPropertyPlugin = new SqlQueryResultPropertyPlugin(query, resultPropertyQueryRef.getPropertyName());
    }
    
    if (customBooleanQueryClassName != null) {
    String errmsg = "Can't create java class for customBooleanQueryClassName from class name '" + customBooleanQueryClassName + "'";
    try {
      Class<? extends BooleanQuery> classs = Class.forName(
          customBooleanQueryClassName).asSubclass(BooleanQuery.class);
      booleanQuery = classs.newInstance();
      booleanQuery.setRecordClass(this);
    } catch (ClassNotFoundException ex) {
      throw new WdkModelException(errmsg, ex);
    } catch (InstantiationException ex) {
      throw new WdkModelException(errmsg, ex);
    } catch (IllegalAccessException ex) {
      throw new WdkModelException(errmsg, ex);
    }     
    } else booleanQuery = new BooleanQuery(this);

    // resolve the references for table queries
    resolveTableFieldReferences(model);

    if (attributeOrdering != null) {
      Map<String, AttributeField> orderedAttributes = sortAllAttributes();
      attributeFieldsMap = orderedAttributes;
    }

    // resolve the filter and layout.
    resolveFilterReferences(model);

    // resolve default summary attributes
    if (defaultSummaryAttributeNames != null) {
      Map<String, AttributeField> attributeFields = getAttributeFieldMap();
      for (String fieldName : defaultSummaryAttributeNames) {
        AttributeField field = attributeFields.get(fieldName);
        String fieldDefStr = "Summary attribute field [" + fieldName + "] defined in RecordClass [" + getFullName() + "]";
        if (field == null) throw new WdkModelException(fieldDefStr + " is invalid.");
        if (field.isInternal()) throw new WdkModelException(fieldDefStr + " is internal.");
        defaultSummaryAttributeFields.put(fieldName, field);
      }
    }
    defaultSummaryAttributeNames = null;

    // resolve the favorite note reference to attribute field
    if (favoriteNoteFieldName != null) {
      favoriteNoteField = attributeFieldsMap.get(favoriteNoteFieldName);
      if (favoriteNoteField == null)
        throw new WdkModelException("The attribute '" + favoriteNoteFieldName +
            "' for the default favorite " + "note content of recordClass '" + getFullName() + "' is invalid.");
    }

    // resolve references in the attribute category tree
    resolveCategoryTreeReferences(model);

    // resolve references for views
    for (SummaryView summaryView : summaryViewMap.values()) {
      summaryView.resolveReferences(model);
    }
    for (RecordView recordView : recordViewMap.values()) {
      recordView.resolveReferences(model);
    }

    // resolve step analysis refs
    for (StepAnalysis stepAnalysisRef : stepAnalysisMap.values()) {
      ((StepAnalysisXml) stepAnalysisRef).resolveReferences(model);
    }

    // resolve reporters
    for (ReporterRef reporterRef : reporterMap.values()) {
      reporterRef.resolveReferences(model);
    }

    // register this URL segment with the model to ensure uniqueness
    _wdkModel.registerRecordClassUrlSegment(_urlSegment, getFullName());

    _resolved = true;
  }

  private void resolveCategoryTreeReferences(WdkModel model) throws WdkModelException {
    // ensure attribute categories are unique, then add attribute
    // references to appropriate places on category tree
    if (attributeCategoryTree == null) {
      // no categories were specified for this record class
      // must still create tree to hold all (uncategorized) attributes
      attributeCategoryTree = new AttributeCategoryTree();
    }

    // this must be called before the attributes are added....
    attributeCategoryTree.resolveReferences(model);

    for (AttributeQueryReference queryRef : attributesQueryRefList) {
      for (AttributeField attribute : queryRef.getAttributeFields()) {
        attributeCategoryTree.addAttributeToCategories(attribute);
      }
    }
    for (AttributeField attribute : attributeFieldList) {
      if (attribute != idAttributeField) {
        attributeCategoryTree.addAttributeToCategories(attribute);
      }
    }
  }

  private void resolveAttributeQueryReferences(WdkModel wdkModel) throws WdkModelException {
    String[] pkColumns = primaryKeyDefinition.getColumnRefs();
    List<String> pkColumnList = Arrays.asList(pkColumns);
    for (AttributeQueryReference reference : attributesQueryRefList) {
      // validate attribute query
      Query query = (Query) wdkModel.resolveReference(reference.getTwoPartName());
      validateBulkQuery(query);
      
      // resolving dynamic column attribute fields
      reference.resolveReferences(wdkModel);

      // add fields into record level, and associate columns
      Map<String, AttributeField> fields = reference.getAttributeFieldMap();
      Map<String, Column> columns = query.getColumnMap();
      for (AttributeField field : fields.values()) {
        assignFieldParent(field);
        String fieldName = field.getName();
        // check if the attribute is duplicated
        if (attributeFieldsMap.containsKey(fieldName))
          throw new WdkModelException("The attribute " + fieldName +
              " is duplicated in the recordClass " + getFullName());

        // check if attribute name is same as a table
        if (tableFieldsMap.containsKey(fieldName))
          throw new WdkModelException("The attribute " + fieldName +
              " has the same name as a table in the recordClass " + getFullName());

        // check if attribute name is same as a pk column
        if (pkColumnList.contains(fieldName)) {
          throw new WdkModelException("The attribute " + fieldName + " in attributeQueryRef " +
              reference.getTwoPartName() + " cannot be the same as a primary key column.  Use a " +
              "pkColumnAttribute tag to declare non-default PK column attribute field behavior.");
        }

        // link columnAttributes with columns
        if (field instanceof QueryColumnAttributeField) {
          Column column = columns.get(fieldName);
          if (column == null) {
            throw new WdkModelException("Column is missing for " + "the QueryColumnAttributeField " +
                fieldName + " in recordClass " + getFullName());
          }
          ((QueryColumnAttributeField) field).setColumn(column);
        }
        attributeFieldsMap.put(fieldName, field);
      }

      Query attributeQuery = RecordClass.prepareQuery(wdkModel, query, pkColumns);
      attributeQueries.put(query.getFullName(), attributeQuery);
    }
  }

  private void assignFieldParent(AttributeField field) {
    field.setContainerName(getFullName());
    if (field instanceof DerivedAttributeField) {
      ((DerivedAttributeField)field).setContainer(this);
    }
  }

  private void resolveTableFieldReferences(WdkModel wdkModel) throws WdkModelException {
    String[] paramNames = primaryKeyDefinition.getColumnRefs();

    // resolve the references for table queries
    for (TableField tableField : tableFieldsMap.values()) {
      tableField.resolveReferences(wdkModel);

      Query query = tableField.getUnwrappedQuery();

      Query tableQuery = RecordClass.prepareQuery(wdkModel, query, paramNames);
      tableQueries.put(query.getFullName(), tableQuery);
    }

  }

  private void resolveFilterReferences(WdkModel wdkModel) throws WdkModelException {
    // resolve references for filter instances
    for (AnswerFilter filter : filterList) {
      filter.resolveReferences(wdkModel);

      Map<String, AnswerFilterInstance> instances = filter.getInstances();
      for (String filterName : instances.keySet()) {
        if (filterMap.containsKey(filterName))
          throw new WdkModelException("Filter instance [" + filterName + "] of type " + getFullName() +
              " is included more than once");
        AnswerFilterInstance instance = instances.get(filterName);
        filterMap.put(filterName, instance);

        if (instance.isDefault()) {
          if (defaultFilter != null)
            throw new WdkModelException("The default filter of type " + getFullName() +
                " is defined more than once: [" + defaultFilter.getName() + "], [" + instance.getName() + "]");
          defaultFilter = instance;
        }
        if (instance.isBooleanExpansion()) {
          if (booleanExpansionFilter != null)
            throw new WdkModelException("The boolean expansion " + "filter of type " + getFullName() +
                " is defined more " + "than once: [" + booleanExpansionFilter.getName() + "] and [" +
                instance.getName() + "]");
          booleanExpansionFilter = instance;
        }
      }
    }
    filterList = null;

    // resolve references for the filter layout instances
    for (AnswerFilterLayout layout : filterLayoutMap.values()) {
      layout.resolveReferences(wdkModel);
    }

    // resolve step filter references
    for (FilterReference reference : _filterReferences) {
      StepFilter filter = resolveStepFilterReferenceByName(reference.getName(), _wdkModel, "recordClass " + getFullName());
      if (_stepFilters.containsKey(filter.getKey()))
        throw new WdkModelException("Same filter \"" + name + "\" is referenced in attribute " + getName() +
            " of recordClass " + getFullName() + " twice.");
      _stepFilters.put(filter.getKey(), filter);
    }
    _filterReferences.clear();

  }

  public static StepFilter resolveStepFilterReferenceByName(String name, WdkModel wdkModel, String location) throws WdkModelException {
    FilterDefinition definition = (FilterDefinition) wdkModel.resolveReference(name);
    if (definition instanceof StepFilterDefinition) {
      return ((StepFilterDefinition) definition).getStepFilter();
    }
    else {
      throw new WdkModelException("The filter ref '" + name + "', declared at " + location + ", is not a stepFilter.");
    }
  }

  /**
   * A bulk query is either an original attribute or table query, that is, it either doesn't any param, or
   * just one param with the name of Utilities.PARAM_USER_ID.
   * 
   * @param query
   * @throws WdkModelException
   */
  void validateBulkQuery(Query query) throws WdkModelException {
    validateQuery(query);

    // Further limit the attribute/table query to have only user_id param
    // (optional). This is required to enable bulk query rewriting.
    String message = "Bulk query '" + query.getFullName() + "' can have only a '" + Utilities.PARAM_USER_ID +
        "' param, and it is optional.";
    Param[] params = query.getParams();
    if (params.length > 1)
      throw new WdkModelException(message);
    else if (params.length == 1 && !params[0].getName().equals(Utilities.PARAM_USER_ID))
      throw new WdkModelException(message);
  }

  /**
   * validate a query, and make sure it returns primary key columns, and the params of it can have only
   * primary_key-column-mapped params (optional) and user_id param (optional).
   * 
   * @param query
   * @throws WdkModelException
   */
  void validateQuery(Query query) throws WdkModelException {
    String[] pkColumns = primaryKeyDefinition.getColumnRefs();
    Map<String, String> pkColumnMap = new LinkedHashMap<String, String>();
    for (String column : pkColumns)
      pkColumnMap.put(column, column);

    // make sure the params contain only primary key params, and (optional)
    // user_id param; but they can have less params than primary key
    // columns. WDK will append the missing ones automatically.
    for (Param param : query.getParams()) {
      String paramName = param.getName();
      if (paramName.equals(Utilities.PARAM_USER_ID))
        continue;
      if (!pkColumnMap.containsKey(paramName))
        throw new WdkModelException("The attribute or table query " + query.getFullName() + " has param " +
            paramName + ", and it doesn't match with any of the primary key " + "columns.");
    }

    // make sure the attribute/table query returns primary key columns
    Map<String, Column> columnMap = query.getColumnMap();
    for (String column : primaryKeyDefinition.getColumnRefs()) {
      if (!columnMap.containsKey(column))
        throw new WdkModelException("The query " + query.getFullName() + " of " + getFullName() +
            " doesn't return the " + "required primary key column " + column);
    }
  }

  public void setResources(WdkModel wdkModel) {
    // set the resource in reporter
    for (ReporterRef reporter : reporterMap.values()) {
      reporter.setResources(wdkModel);
    }
  }

  private Map<String, AttributeField> sortAllAttributes() throws WdkModelException {
    String orderedAtts[] = attributeOrdering.split(",");
    Map<String, AttributeField> orderedAttsMap = new LinkedHashMap<String, AttributeField>();

    // primaryKey first
    orderedAttsMap.put(idAttributeField.getName(), idAttributeField);

    for (String nextAtt : orderedAtts) {
      nextAtt = nextAtt.trim();
      if (!orderedAttsMap.containsKey(nextAtt)) {
        AttributeField nextAttField = attributeFieldsMap.get(nextAtt);

        if (nextAttField == null) {
          String message = "RecordClass " + getFullName() + " defined attribute " + nextAtt + " in its " +
              "attribute ordering, but that is not a valid " + "attribute for this RecordClass";
          throw new WdkModelException(message);
        }
        orderedAttsMap.put(nextAtt, nextAttField);
      }
    }
    // add all attributes not in the ordering
    for (String nextAtt : attributeFieldsMap.keySet()) {
      if (!orderedAttsMap.containsKey(nextAtt)) {
        AttributeField nextField = attributeFieldsMap.get(nextAtt);
        orderedAttsMap.put(nextAtt, nextField);
      }
    }
    return orderedAttsMap;
  }

  @Override
  public void excludeResources(String projectId) throws WdkModelException {
    // exclude reporters
    for (ReporterRef reporter : reporterList) {
      if (reporter.include(projectId)) {
        reporter.excludeResources(projectId);
        String reporterName = reporter.getName();
        if (reporterMap.containsKey(reporterName))
          throw new WdkModelException("The reporter " + reporterName + " is duplicated in recordClass " +
              this.getFullName());
        reporterMap.put(reporterName, reporter);
      }
    }
    reporterList = null;

    // make sure there is a primary key
    if (primaryKeyDefinition == null) {
      throw new WdkModelException("The primaryKey of recordClass " + getFullName() +
          " is not set.  Please define a <primaryKey> tag in the recordClass.");
    }

    // exclude primary key
    primaryKeyDefinition.excludeResources(projectId);

    // exclude attributes
    List<AttributeField> newFieldList = new ArrayList<AttributeField>();
    for (AttributeField field : attributeFieldList) {
      if (field.include(projectId)) {
        field.excludeResources(projectId);
        String fieldName = field.getName();
        // make sure only one ID attribute field exists
        if (field instanceof IdAttributeField) {
          if (this.idAttributeField != null)
            throw new WdkModelException("More than one ID attribute field present in recordClass " + getFullName());
          this.idAttributeField = (IdAttributeField) field;
        }
        // make sure PK column fields and only PK column fields match PK columns
        if (field instanceof PkColumnAttributeField && !primaryKeyDefinition.hasColumn(fieldName)) {
          throw new WdkModelException("PkColumnAttributes can only be defined with the name of a PK column.");
        }
        if (!(field instanceof PkColumnAttributeField) && primaryKeyDefinition.hasColumn(fieldName)) {
          throw new WdkModelException("Only PkColumnAttributes can be defined with the name of a PK column.");
        }
        if (attributeFieldsMap.containsKey(fieldName)) {
          throw new WdkModelException("The attribute " + fieldName + " is duplicated in recordClass " + getFullName());
        }
        if (tableFieldsMap.containsKey(fieldName)) {
          throw new WdkModelException("The attribute " + fieldName + " has the same name as a table in the recordClass " + getFullName());
        }
        attributeFieldsMap.put(fieldName, field);
        newFieldList.add(field);
      }
    }
    attributeFieldList = newFieldList;

    // make sure there is an ID attribute
    if (idAttributeField == null) {
      throw new WdkModelException("The idAttribute of recordClass " + getFullName() +
          " is not set. Please define a <idAttribute> tag in the recordClass.");
    }

    // exclude table fields
    for (TableField field : tableFieldList) {
      if (field.include(projectId)) {
        field.excludeResources(projectId);
        String fieldName = field.getName();
        if (tableFieldsMap.containsKey(fieldName))
          throw new WdkModelException("The table " + fieldName + " is duplicated in recordClass " +
              getFullName());
        if (attributeFieldsMap.containsKey(fieldName))
          throw new WdkModelException("The table" + fieldName +
              " has the same name as an attribute in the recordClass " + getFullName());


        tableFieldsMap.put(fieldName, field);
      }
    }
    tableFieldList = null;

    // exclude query refs
    Map<String, AttributeQueryReference> attributesQueryRefs = new LinkedHashMap<String, AttributeQueryReference>();
    for (AttributeQueryReference queryRef : attributesQueryRefList) {
      if (queryRef.include(projectId)) {
        String refName = queryRef.getTwoPartName();
        if (attributesQueryRefs.containsKey(refName)) {
          throw new WdkModelException("recordClass " + getFullName() +
              " has more than one attributeQueryRef \"" + refName + "\"");
        }
        else {
          queryRef.excludeResources(projectId);
          attributesQueryRefs.put(refName, queryRef);
        }
      }
    }
    attributesQueryRefList.clear();
    attributesQueryRefList.addAll(attributesQueryRefs.values());

    // exclude filter instances
    List<AnswerFilter> newFilters = new ArrayList<AnswerFilter>();
    for (AnswerFilter filter : filterList) {
      if (filter.include(projectId)) {
        filter.excludeResources(projectId);
        newFilters.add(filter);
      }
    }
    filterList = newFilters;

    // exclude filter layout
    for (AnswerFilterLayout layout : filterLayoutList) {
      if (layout.include(projectId)) {
        layout.excludeResources(projectId);
        String layoutName = layout.getName();
        if (filterLayoutMap.containsKey(layoutName))
          throw new WdkModelException("Filter layout [" + layoutName + "] of type " + getFullName() +
              " is included more than once");
        filterLayoutMap.put(layoutName, layout);
      }
    }
    filterLayoutList = null;

    // exclude paramValuesSets
    for (ParamValuesSet pvs : unexcludedParamValuesSets) {
      if (pvs.include(projectId)) {
        if (paramValuesSet != null)
          throw new WdkModelException("Duplicate <paramErrors> included in record class " + getName() +
              " for projectId " + projectId);
        paramValuesSet = pvs;

      }
    }

    // exclude summary and sorting attribute list
    boolean hasAttributeList = false;
    for (AttributeList attributeList : attributeLists) {
      if (attributeList.include(projectId)) {
        if (hasAttributeList) {
          throw new WdkModelException("The question " + getFullName() +
              " has more than one <attributesList> for " + "project " + projectId);
        }
        else {
          this.defaultSummaryAttributeNames = attributeList.getSummaryAttributeNames();
          this.defaultSortingMap = attributeList.getSortingAttributeMap();
          hasAttributeList = true;
        }
      }
    }
    attributeLists = null;

    // exclude favorite references
    for (FavoriteReference favorite : favorites) {
      if (favorite.include(projectId)) {
        if (favoriteNoteFieldName != null)
          throw new WdkModelException("The favorite tag is " + "duplicated on the recordClass " +
              getFullName());
        this.favoriteNoteFieldName = favorite.getNoteField();
      }
    }
    favorites = null;

    // exclude the summary views
    Map<String, SummaryView> summaryViews = new LinkedHashMap<String, SummaryView>();
    for (SummaryView view : summaryViewList) {
      if (view.include(projectId)) {
        view.excludeResources(projectId);
        String summaryViewName = view.getName();
        if (summaryViews.containsKey(summaryViewName))
          throw new WdkModelException("The summary view '" + summaryViewName + "' is duplicated in record " +
              getFullName());

        summaryViews.put(summaryViewName, view);
      }
    }
    summaryViewList = null;

    // add WDK supported views to all record classes, first
    for (SummaryView view : SummaryView.createSupportedSummaryViews(this)) {
      view.excludeResources(projectId);
      summaryViewMap.put(view.getName(), view);
    }

    // then add user defined views to override WDK supported ones
    for (SummaryView view : summaryViews.values()) {
      summaryViewMap.put(view.getName(), view);
    }

    // exclude step analyses
    for (StepAnalysisXml analysis : stepAnalysisList) {
      if (analysis.include(projectId)) {
        analysis.excludeResources(projectId);
        String stepAnalysisName = analysis.getName();
        if (stepAnalysisMap.containsKey(stepAnalysisName)) {
          throw new WdkModelException("The step analysis '" + stepAnalysisName + "' is duplicated in question " +
              getFullName());
        }
        stepAnalysisMap.put(stepAnalysisName, analysis);
      }
    }
    stepAnalysisList = null;

    // exclude the summary views
    Map<String, RecordView> recordViews = new LinkedHashMap<String, RecordView>();
    for (RecordView view : recordViewList) {
      if (view.include(projectId)) {
        view.excludeResources(projectId);
        String recordViewName = view.getName();
        if (recordViews.containsKey(recordViewName))
          throw new WdkModelException("The record view '" + recordViewName + "' is duplicated in record " +
              getFullName());

        recordViews.put(recordViewName, view);
      }
    }
    recordViewList = null;

    // add WDK supported views to all record classes first
    for (RecordView view : RecordView.createSupportedRecordViews()) {
      view.excludeResources(projectId);
      recordViewMap.put(view.getName(), view);
    }

    // then add user defined views to override WDK supported ones
    for (RecordView view : recordViews.values()) {
      recordViewMap.put(view.getName(), view);
    }

    // exclude filter references
    List<FilterReference> references = new ArrayList<>();
    for (FilterReference reference : _filterReferences) {
      if (reference.include(projectId)) {
        reference.excludeResources(projectId);
        references.add(reference);
      }
    }
    _filterReferences.clear();
    _filterReferences.addAll(references);

  }

  public void addFilter(AnswerFilter filter) {
    filter.setRecordClass(this);
    this.filterList.add(filter);
  }

  /**
   * @return a map of filter instances available to this record class.
   */
  public Map<String, AnswerFilterInstance> getFilterMap() {
    return new LinkedHashMap<String, AnswerFilterInstance>(filterMap);
  }

  public AnswerFilterInstance[] getFilterInstances() {
    AnswerFilterInstance[] instances = new AnswerFilterInstance[filterMap.size()];
    filterMap.values().toArray(instances);
    return instances;
  }

  public AnswerFilterInstance getFilterInstance(String filterName) {
    if (filterName == null)
      return null;
    AnswerFilterInstance instance = filterMap.get(filterName);

    // ignore the invalid filter name
    // if (instance == null)
    // throw new WdkModelException("The name [" + filterName
    // + "] does not " + "match any filter instance of type "
    // + getFullName());
    return instance;
  }

  public void addFilterLayout(AnswerFilterLayout layout) {
    layout.setRecordClass(this);
    this.filterLayoutList.add(layout);
  }

  public Map<String, AnswerFilterLayout> getFilterLayoutMap() {
    return new LinkedHashMap<String, AnswerFilterLayout>(filterLayoutMap);
  }

  public AnswerFilterLayout[] getFilterLayouts() {
    AnswerFilterLayout[] layouts = new AnswerFilterLayout[filterLayoutMap.size()];
    filterLayoutMap.values().toArray(layouts);
    return layouts;
  }

  public AnswerFilterLayout getFilterLayout(String layoutName) throws WdkModelException {
    AnswerFilterLayout layout = filterLayoutMap.get(layoutName);
    if (layout == null)
      throw new WdkModelException("The name [" + layoutName + "] does " +
          "not match any filter layout of type " + getFullName());
    return layout;
  }

  public AnswerFilterInstance getDefaultFilter() {
    return defaultFilter;
  }

  /**
   * If the filter is not null, in all the boolean operations of the record page, the operands will first be
   * filtered by this filter, and then the results of these will be used in boolean operation.
   */
  public AnswerFilterInstance getBooleanExpansionFilter() {
    return booleanExpansionFilter;
  }

  /**
   * Make sure all pk columns has a corresponding ColumnAttributeField
   * @throws WdkModelException 
   */
  private void createPrimaryKeySubFields(String projectId) throws WdkModelException {
    String[] pkColumns = primaryKeyDefinition.getColumnRefs();
    logger.debug("[" + getName() + "] Creating PK subfields for columns: " + FormatUtil.arrayToString(pkColumns));
    for (String pkColumnName : pkColumns) {
      if (attributeFieldsMap.containsKey(pkColumnName)) {
        AttributeField pkColumnField = attributeFieldsMap.get(pkColumnName);
        if (pkColumnField instanceof PkColumnAttributeField) {
          // model defined a PkColumnAttributeField for this column; don't generate
          continue;
        }
        // model defined an attribute but NOT a pkColumnAttribute for this PK column; error
        throw new WdkModelException("RecordClass [" + getFullName() +
            "] contains attribute [" + pkColumnName + "] with the same name as a primary key column.  " +
            "Columns declared in the primary key are automatically given internal PkColumnAttributeFields," +
            "or you may declare them as <pkColumnAttribute> to expose them or assign additional properties.");
      }

      // model did not define field for this PK column; create
      PkColumnAttributeField field = new PkColumnAttributeField();
      field.setName(pkColumnName);
      field.setInternal(true);
      assignFieldParent(field);
      field.excludeResources(projectId);
      logger.debug("Adding PkColumnAttributeField '" + pkColumnName + "' to attributeFieldsMap of '" + getFullName() + "'.");
      attributeFieldsMap.put(pkColumnName, field);
    }
  }

  public void addAttributeList(AttributeList attributeList) {
    this.attributeLists.add(attributeList);
  }

  public Map<String, AttributeField> getSummaryAttributeFieldMap() {
    Map<String, AttributeField> attributeFields = new LinkedHashMap<String, AttributeField>();

    // always put primary key as the first field
    attributeFields.put(idAttributeField.getName(), idAttributeField);

    if (defaultSummaryAttributeFields.size() > 0) {
      attributeFields.putAll(defaultSummaryAttributeFields);
    }
    else {
      Map<String, AttributeField> nonInternalFields = getAttributeFieldMap(FieldScope.NON_INTERNAL);
      for (String fieldName : nonInternalFields.keySet()) {
        attributeFields.put(fieldName, nonInternalFields.get(fieldName));
        if (attributeFields.size() >= Utilities.DEFAULT_SUMMARY_ATTRIBUTE_SIZE)
          break;
      }
    }
    return attributeFields;
  }
  
  @Override
  public Map<String, Boolean> getSortingAttributeMap() {
    Map<String, Boolean> map = new LinkedHashMap<String, Boolean>();
    int count = 0;
    for (String attrName : defaultSortingMap.keySet()) {
      map.put(attrName, defaultSortingMap.get(attrName));
      count++;
      if (count >= UserPreferences.SORTING_LEVEL)
        break;
    }

    // has to sort at least on something, primary key as default
    if (map.isEmpty()) {
      return getIdSortingAttributeMap();
    }

    return map;
  }

  public Map<String, Boolean> getIdSortingAttributeMap() {
    return new MapBuilder<String, Boolean>(new LinkedHashMap<String, Boolean>())
        .put(idAttributeField.getName(), true).toMap();
  }

  public void addCategoryList(CategoryList categoryList) {
    _categoryList = categoryList;
  }
  
  public List<AttributeCategory> getCollapsedCategories() {
    if (_categoryList == null) {
      return null;
    }
    return _categoryList.getCollapsed(attributeCategoryTree);
  }

  public String getChecksum() {
    return null;
  }

  public void setUseBasket(boolean useBasket) {
    this.useBasket = useBasket;
  }

  /**
   * @return if true, the basket feature will be available for this record type.
   */
  public boolean isUseBasket() {
    return useBasket;
  }

  /**
   * The real time question is used on the basket page to display the current records in the basket.
   * 
   * @return
   * @throws WdkModelException
   */
  public Question getRealtimeBasketQuestion() throws WdkModelException {
    String questionName = Utilities.INTERNAL_QUESTION_SET + ".";
    questionName += getFullName().replace('.', '_');
    questionName += BasketFactory.REALTIME_BASKET_QUESTION_SUFFIX;
    return (Question) _wdkModel.resolveReference(questionName);
  }

  /**
   * The snapshot question is used when exporting basket to a strategy, and the step will use this question to
   * get a snapshot of those records in basket, and store them in the
   * 
   * @return
   * @throws WdkModelException
   */
  public Question getSnapshotBasketQuestion() throws WdkModelException {
    String questionName = Utilities.INTERNAL_QUESTION_SET + ".";
    questionName += getFullName().replace('.', '_');
    questionName += BasketFactory.SNAPSHOT_BASKET_QUESTION_SUFFIX;
    return (Question) _wdkModel.resolveReference(questionName);
  }

  public Question[] getTransformQuestions(boolean allowTypeChange) {
    List<Question> list = new ArrayList<Question>();
    for (QuestionSet questionSet : _wdkModel.getAllQuestionSets()) {
      for (Question question : questionSet.getQuestions()) {
        if (!question.getQuery().isTransform())
          continue;
        if (question.getTransformParams(this).length == 0)
          continue;
        String outType = question.getRecordClass().getFullName();
        if (allowTypeChange || this.getFullName().equals(outType))
          list.add(question);
      }
    }
    Question[] array = new Question[list.size()];
    list.toArray(array);
    return array;
  }

  /**
   * @return the shortDisplayName
   */
  public String getShortDisplayName() {
    return (shortDisplayName != null) ? shortDisplayName : getDisplayName();
  }

  public String getNativeShortDisplayName() {
      return (nativeShortDisplayName != null) ? nativeShortDisplayName : getNativeDisplayName();
    }

  /**
   * @param shortDisplayName
   *          the shortDisplayName to set
   */
  public void setShortDisplayName(String shortDisplayName) {
    this.shortDisplayName = shortDisplayName;
  }

  public void addFavorite(FavoriteReference favorite) {
    this.favorites.add(favorite);
  }

  public AttributeField getFavoriteNoteField() {
    return favoriteNoteField;
  }

  public Map<String, SummaryView> getSummaryViews() {
    return new LinkedHashMap<String, SummaryView>(summaryViewMap);
  }

  public SummaryView getSummaryView(String viewName) throws WdkUserException {
    if (summaryViewMap.containsKey(viewName)) {
      return summaryViewMap.get(viewName);
    }
    else {
      throw new WdkUserException("Unknown summary view for record class " + "[" + getFullName() + "]: " +
          viewName);
    }
  }

  public void addSummaryView(SummaryView view) {
    if (summaryViewList == null)
      summaryViewMap.put(view.getName(), view);
    else
      summaryViewList.add(view);
  }

  public Map<String, StepAnalysis> getStepAnalyses() {
    return new LinkedHashMap<String, StepAnalysis>(stepAnalysisMap);
  }

  public StepAnalysis getStepAnalysis(String analysisName) throws WdkUserException {
    if (stepAnalysisMap.containsKey(analysisName)) {
      return stepAnalysisMap.get(analysisName);
    }
    else {
      throw new WdkUserException("Unknown step analysis for record class " + "[" + getFullName() + "]: " +
          analysisName);
    }
  }

  public void addStepAnalysis(StepAnalysisXml analysis) {
    if (stepAnalysisList == null)
      stepAnalysisMap.put(analysis.getName(), analysis);
    else
      stepAnalysisList.add(analysis);
  }

  public Map<String, RecordView> getRecordViews() {
    return new LinkedHashMap<String, RecordView>(recordViewMap);
  }

  public RecordView getRecordView(String viewName) throws WdkUserException {
    if (recordViewMap.containsKey(viewName)) {
      return recordViewMap.get(viewName);
    }
    else {
      throw new WdkUserException("Unknown record view for record class " + "[" + getFullName() + "]: " +
          viewName);
    }
  }

  public RecordView getDefaultRecordView() {
    for (RecordView view : recordViewMap.values()) {
      if (view.isDefault())
        return view;
    }

    if (recordViewMap.size() > 0)
      return recordViewMap.values().iterator().next();

    return null;
  }

  public void addRecordView(RecordView view) {
    if (recordViewList == null)
      recordViewMap.put(view.getName(), view);
    else
      recordViewList.add(view);
  }

  public boolean hasMultipleRecords(User user, Map<String, Object> pkValues) throws WdkModelException,
      WdkUserException {
    List<Map<String, Object>> records = lookupPrimaryKeys(user, pkValues);
    return records.size() > 1;
  }

  /**
   * use alias query to lookup old ids and convert to new ids
   */
  public List<Map<String, Object>> lookupPrimaryKeys(User user, Map<String, Object> pkValues)
      throws WdkModelException, WdkUserException {
    return primaryKeyDefinition.lookUpPrimaryKeys(user, pkValues);
  }

  public String[] getIndexColumns() {
    // only need to index the pk columns;
    return primaryKeyDefinition.getColumnRefs();
  }

  public final void printDependency(PrintWriter writer, String indent) throws WdkModelException {
    writer.println(indent + "<recordClass name=\"" + getName() + "\">");
    String indent1 = indent + WdkModel.INDENT;
    String indent2 = indent1 + WdkModel.INDENT;

    // print attributes
    if (attributeFieldsMap.size() > 0) {
      writer.println(indent1 + "<attributes size=\"" + attributeFieldsMap.size() + "\">");
      String[] attributeNames = attributeFieldsMap.keySet().toArray(new String[0]);
      Arrays.sort(attributeNames);
      for (String attributeName : attributeNames) {
        attributeFieldsMap.get(attributeName).printDependency(writer, indent2);
      }
      writer.println(indent1 + "</attributes>");
    }

    // print attribute queries
    if (attributeQueries.size() > 0) {
      writer.println(indent1 + "<attributeQueries size=\"" + attributeQueries.size() + "\">");
      String[] queryNames = attributeQueries.keySet().toArray(new String[0]);
      Arrays.sort(queryNames);
      for (String queryName : queryNames) {
        attributeQueries.get(queryName).printDependency(writer, indent2);
      }
      writer.println(indent1 + "</attributeQueries>");
    }

    // print tables
    if (tableFieldsMap.size() > 0) {
      writer.println(indent1 + "<tables size=\"" + tableFieldsMap.size() + "\">");
      String[] tableNames = tableFieldsMap.keySet().toArray(new String[0]);
      Arrays.sort(tableNames);
      for (String tableName : tableNames) {
        tableFieldsMap.get(tableName).printDependency(writer, indent2);
      }
      writer.println(indent1 + "</tables>");
    }

    writer.println(indent + "</recordClass>");
  }

  public void addFilterReference(FilterReference reference) {
    _filterReferences.add(reference);
  }

  public void addStepFilter(StepFilter filter) {
    _stepFilters.put(filter.getKey(), filter);
  }

  /** 
   * try to find a filter with the associated key.  
   * @param key
   * @return null if not found
   * @throws WdkModelException
   */
  public Filter getFilter(String key) throws WdkModelException {
    Filter filter = getStepFilter(key);
    if (filter == null)
      filter = getColumnFilter(key);
    return filter;
  }

  public StepFilter getStepFilter(String key) {
    return _stepFilters.get(key);
  }

  public ColumnFilter getColumnFilter(String key) {
    for (AttributeField attribute : getAttributeFields()) {
      if (attribute instanceof QueryColumnAttributeField) {
        QueryColumnAttributeField columnAttribute = (QueryColumnAttributeField) attribute;
        for (ColumnFilter filter : columnAttribute.getColumnFilters()) {
          if (filter.getKey().equals(key))
            return filter;
        }
      }
    }
    return null;
  }

  public Map<String, StepFilter> getStepFilters() {
    return new LinkedHashMap<>(_stepFilters);
  }

  /**
   * Returns a set of filters (by name) for this question.  Only non-view-only
   * filters are included in this list.  View-only filters are only available
   * by name.
   * 
   * @return map of all non-view-only filters, from filter name to filter
   */
  public Map<String, Filter> getFilters() {
    // get all step filters
    logger.debug("RECORDCLASS: GETTING ALL FILTERs");
    Map<String, Filter> filters = new LinkedHashMap<>();
    for (StepFilter filter : _stepFilters.values()) {
      if (!filter.getIsViewOnly()) {
        logger.debug("RECORDCLASS: filter name: " + filter.getKey());
        filters.put(filter.getKey(), filter);
      }
    }

    // get all column filters
    for (AttributeField attribute : getAttributeFields()) {
      if (attribute instanceof QueryColumnAttributeField) {
        QueryColumnAttributeField columnAttribute = (QueryColumnAttributeField) attribute;
        for (ColumnFilter filter : columnAttribute.getColumnFilters()) {
          if (!filter.getIsViewOnly())
            filters.put(filter.getKey(), filter);
        }
      }
    }
    return filters;
  }

  public IdAttributeField getIdAttributeField() {
    return idAttributeField;
  }

  public boolean idAttributeHasNonPkMacros() throws WdkModelException {
    List<String> idAttrRefs = Functions.mapToList(idAttributeField.getDependencies(), Named.TO_NAME);
    List<String> pkColumnRefs = Arrays.asList(primaryKeyDefinition.getColumnRefs());
    for (String idAttrRef : idAttrRefs) {
      if (!pkColumnRefs.contains(idAttrRef)) {
        return true;
      }
    }
    return false;
  }
}
