package org.gusdb.wdk.model;

import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gusdb.wdk.model.dbms.DBPlatform;
import org.gusdb.wdk.model.query.Column;
import org.gusdb.wdk.model.query.ColumnType;
import org.gusdb.wdk.model.query.Query;
import org.gusdb.wdk.model.query.SqlQuery;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.query.param.ParamSet;
import org.gusdb.wdk.model.query.param.ParamValuesSet;
import org.gusdb.wdk.model.query.param.StringParam;
import org.gusdb.wdk.model.user.BasketFactory;
import org.gusdb.wdk.model.user.User;
import org.json.JSONException;

public class RecordClass extends WdkModelBase implements
        AttributeFieldContainer {

    // private static final Logger logger = Logger.getLogger(RecordClass.class);

    public static Query prepareQuery(WdkModel wdkModel, Query query,
            String[] paramNames) throws WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException,
            WdkUserException {
        Map<String, Column> columns = query.getColumnMap();
        Map<String, Param> originalParams = query.getParamMap();
        Query newQuery = query.clone();
        // do not cache the single-line query
        newQuery.setIsCacheable(false);

        // find the new params to be created
        List<String> newParams = new ArrayList<String>();
        for (String column : paramNames) {
            if (!originalParams.containsKey(column)) newParams.add(column);
        }

        // create the missing primary key params for the query
        ParamSet paramSet = wdkModel.getParamSet(Utilities.INTERNAL_PARAM_SET);
        for (String columnName : newParams) {
            StringParam param;
            if (paramSet.contains(columnName)) {
                param = (StringParam) paramSet.getParam(columnName);
            } else {
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
                if (firstColumn) firstColumn = false;
                else builder.append(" AND ");
                builder.append("f.").append(columnName);
                builder.append(" = $$").append(columnName).append("$$");
            }

            // replace the id_sql macro
            StringBuilder idqBuilder = new StringBuilder();
            for (String column : paramNames) {
                if (idqBuilder.length() == 0) idqBuilder.append("(SELECT ");
                else idqBuilder.append(", ");
                idqBuilder.append("$$" + column + "$$ AS " + column);
            }
            DBPlatform platform = wdkModel.getQueryPlatform();
            idqBuilder.append(platform.getDummyTable());
            idqBuilder.append(")");

            String idSql = idqBuilder.toString();
            String sql = builder.toString();
            sql = sql.replace(Utilities.MACRO_ID_SQL, idSql);

            ((SqlQuery) newQuery).setSql(sql);
        }
        return newQuery;
    }

    private WdkModel wdkModel;

    private RecordClassSet recordClassSet;

    private List<AttributeQueryReference> attributesQueryRefList = new ArrayList<AttributeQueryReference>();

    private Map<String, Query> attributeQueries = new LinkedHashMap<String, Query>();
    private Map<String, Query> tableQueries = new LinkedHashMap<String, Query>();

    private List<AttributeField> attributeFieldList = new ArrayList<AttributeField>();
    private Map<String, AttributeField> attributeFieldsMap = new LinkedHashMap<String, AttributeField>();

    private PrimaryKeyAttributeField primaryKeyField;

    private List<TableField> tableFieldList = new ArrayList<TableField>();
    private Map<String, TableField> tableFieldsMap = new LinkedHashMap<String, TableField>();

    private String name;
    private String fullName;
    private String displayName;
    private String shortDisplayName;
    private String attributeOrdering;
    private String type;

    private List<NestedRecord> nestedRecordQuestionRefList = new ArrayList<NestedRecord>();
    private Map<String, NestedRecord> nestedRecordQuestionRefs = new LinkedHashMap<String, NestedRecord>();

    private List<NestedRecordList> nestedRecordListQuestionRefList = new ArrayList<NestedRecordList>();
    private Map<String, NestedRecordList> nestedRecordListQuestionRefs = new LinkedHashMap<String, NestedRecordList>();

    // for sanity testing
    private boolean doNotTest = false;
    private List<ParamValuesSet> unexcludedParamValuesSets = new ArrayList<ParamValuesSet>();
    private ParamValuesSet paramValuesSet;

    /**
     * This object is not initialized until the first time the RecordClass is
     * asked for a nestedRecordQuestion. At that point it is given the questions
     * in <code>nestedRecordQuestionRefs</code>;
     */
    private Map<String, Question> nestedRecordQuestions;

    /**
     * This object is not initialized until the first time the RecordClass is
     * asked for a nestedRecordListQuestion. At that point it is given the
     * questions in <code>nestedRecordListQuestionRefs</code>;
     */
    private Map<String, Question> nestedRecordListQuestions;

    /**
     * the reference to a query that returns a list of alias ids of the given
     * gene id
     */
    private String aliasQueryRef = null;
    private Query aliasQuery = null;

    private List<ReporterRef> reporterList = new ArrayList<ReporterRef>();
    private Map<String, ReporterRef> reporterMap = new LinkedHashMap<String, ReporterRef>();

    private List<AnswerFilter> filterList = new ArrayList<AnswerFilter>();
    private Map<String, AnswerFilterInstance> filterMap = new LinkedHashMap<String, AnswerFilterInstance>();

    private List<AnswerFilterLayout> filterLayoutList = new ArrayList<AnswerFilterLayout>();
    private Map<String, AnswerFilterLayout> filterLayoutMap = new LinkedHashMap<String, AnswerFilterLayout>();

    private AnswerFilterInstance defaultFilter;
    private AnswerFilterInstance booleanExpansionFilter;

    private List<AttributeList> attributeLists = new ArrayList<AttributeList>();

    private String[] defaultSummaryAttributeNames;
    private Map<String, AttributeField> defaultSummaryAttributeFields = new LinkedHashMap<String, AttributeField>();
    private Map<String, Boolean> defaultSortingMap = new LinkedHashMap<String, Boolean>();

    private String allRecordsQueryRef;
    private Query allRecordsQuery;

    private List<FavoriteReference> favorites = new ArrayList<FavoriteReference>();
    private String favoriteNoteFieldName;
    private AttributeField favoriteNoteField;

    // ////////////////////////////////////////////////////////////////////
    // Called at model creation time
    // ////////////////////////////////////////////////////////////////////

    public WdkModel getWdkModel() {
        return wdkModel;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return (displayName == null) ? getType() : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @param attList
     *            comma separated list of attributes in a summary containing
     *            this recordClass.
     */
    /*
     * public void setSummaryAttributeList (String attList){
     * this.summaryAttributeList = attList; }
     */

    public void setAttributeOrdering(String attOrder) {
        this.attributeOrdering = attOrder;
    }

    public void setAliasQueryRef(String queryRef) {
        this.aliasQueryRef = queryRef;
    }

    public PrimaryKeyAttributeField getPrimaryKeyAttributeField() {
        return primaryKeyField;
    }

    /**
     * @param attributesQueryRef
     *            two part query name (set.name)
     */
    public void addAttributesQueryRef(AttributeQueryReference attributesQueryRef) {
        attributesQueryRefList.add(attributesQueryRef);
    }

    public void addAttributeField(AttributeField attributeField)
            throws WdkModelException {
        attributeField.setRecordClass(this);
        attributeField.setContainer(this);
        attributeFieldList.add(attributeField);
    }

    public void addTableField(TableField tableField) {
        tableField.setRecordClass(this);
        tableFieldList.add(tableField);
    }

    public void addNestedRecordQuestion(Question q) {

        nestedRecordQuestions.put(q.getFullName(), q);
    }

    public void addNestedRecordListQuestion(Question q) {
        nestedRecordListQuestions.put(q.getFullName(), q);
    }

    public void addNestedRecordQuestionRef(NestedRecord nr) {
        nestedRecordQuestionRefList.add(nr);
    }

    public void addNestedRecordListQuestionRef(NestedRecordList nrl) {

        nestedRecordListQuestionRefList.add(nrl);
    }

    public void addReporterRef(ReporterRef reporter) {
        reporterList.add(reporter);
    }

    public void setDoNotTest(boolean doNotTest) {
        this.doNotTest = doNotTest;
    }

    public boolean getDoNotTest() {
        return doNotTest;
    }

    public void addParamValuesSet(ParamValuesSet paramValuesSet) {
        unexcludedParamValuesSets.add(paramValuesSet);
    }

    public ParamValuesSet getParamValuesSet() {
        return paramValuesSet == null ? new ParamValuesSet() : paramValuesSet;
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
            if (scope == FieldScope.ALL
                    || (scope == FieldScope.NON_INTERNAL && !field.isInternal())
                    || (scope == FieldScope.REPORT_MAKER && field.isInReportMaker()))
                fields.put(field.getName(), field);
        }
        return fields;
    }

    public TableField[] getTableFields() {
        Map<String, TableField> tables = getTableFieldMap();
        TableField[] array = new TableField[tables.size()];
        tables.values().toArray(array);
        return array;
    }

    public Map<String, AttributeField> getAttributeFieldMap() {
        return getAttributeFieldMap(FieldScope.ALL);
    }

    public Map<String, AttributeField> getAttributeFieldMap(FieldScope scope) {
        Map<String, AttributeField> fields = new LinkedHashMap<String, AttributeField>();

        // always put primary key field as the first one
        fields.put(primaryKeyField.getName(), primaryKeyField);

        for (AttributeField field : attributeFieldsMap.values()) {
            if (scope == FieldScope.ALL
                    || (scope == FieldScope.NON_INTERNAL && !field.isInternal())
                    || (scope == FieldScope.REPORT_MAKER && field.isInReportMaker()))
                fields.put(field.getName(), field);
        }
        return fields;
    }

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

    public Question[] getNestedRecordQuestions() {
        if (nestedRecordQuestions == null) {
            initNestedRecords();
        }
        Question[] returnedNq = new Question[nestedRecordQuestions.size()];
        nestedRecordQuestions.values().toArray(returnedNq);
        return returnedNq;
    }

    public Question[] getNestedRecordListQuestions() {
        if (nestedRecordListQuestions == null) {
            initNestedRecords();
        }
        Question[] returnedNq = new Question[nestedRecordListQuestions.size()];
        nestedRecordListQuestions.values().toArray(returnedNq);
        return returnedNq;
    }

    public Reference getReference() throws WdkModelException {
        return new Reference(getFullName());
    }

    public Map<String, ReporterRef> getReporterMap() {
        return new LinkedHashMap<String, ReporterRef>(reporterMap);
    }

    public String toString() {
        String newline = System.getProperty("line.separator");
        StringBuffer buf = new StringBuffer("Record: name='" + name + "'").append(newline);

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
     * <sanityRecord ref="GeneRecordClasses.GeneRecordClass"
     * primaryKey="PF11_0344"/>
     */
    public String getSanityTestSuggestion() throws WdkModelException {
        String indent = "    ";
        String newline = System.getProperty("line.separator");
        StringBuffer buf = new StringBuffer(newline + newline + indent
                + "<sanityRecord ref=\"" + getFullName() + "\"" + newline
                + indent + indent + indent + "primaryKey=\"FIX_pk\">" + newline);
        buf.append(indent + "</sanityRecord>");
        return buf.toString();
    }

    // /////////////////////////////////////////////////////////////////////////
    // package scope methods
    // /////////////////////////////////////////////////////////////////////////

    /**
     * @param recordSetName
     *            name of the recordSet to which this record belongs.
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
        return new LinkedHashMap<String, Query>(this.attributeQueries);
    }

    AttributeField getAttributeField(String attributeName)
            throws WdkModelException {
        AttributeField attributeField = attributeFieldsMap.get(attributeName);
        if (attributeField == null) {
            String message = "RecordClass " + getName()
                    + " doesn't have an attribute field with name '"
                    + attributeName + "'.";
            throw new WdkModelException(message);
        }
        return attributeField;
    }

    TableField getTableField(String tableName) throws WdkModelException {
        TableField tableField = tableFieldsMap.get(tableName);
        if (tableField == null) {
            String message = "Record " + getName()
                    + " does not have a table field with name '" + tableName
                    + "'.";
            throw new WdkModelException(message);
        }
        return tableField;
    }

    @Override
    public void resolveReferences(WdkModel model) throws WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException,
            WdkUserException {
        if (resolved) return;

        this.wdkModel = model;

        if (name.length() == 0 || name.indexOf('\'') >= 0)
            throw new WdkModelException("recordClass name cannot be empty or "
                    + "having single quotes: " + name);

        // resolve the references for attribute queries
        resolveAttributeQueryReferences(model);

        // resolve references for the attribute fields
        for (AttributeField field : attributeFieldsMap.values()) {
            field.resolveReferences(model);
        }

        // resolve the alias query
        resolveAliasQuery(model);

        // resolve the references for table queries
        resolveTableFieldReferences(model);

        if (attributeOrdering != null) {
            Map<String, AttributeField> orderedAttributes = sortAllAttributes();
            attributeFieldsMap = orderedAttributes;
        }

        for (NestedRecord nestedRecord : nestedRecordQuestionRefs.values()) {
            nestedRecord.setParentRecordClass(this);
            nestedRecord.resolveReferences(model);
        }

        for (NestedRecordList nestedRecordList : nestedRecordListQuestionRefs.values()) {
            nestedRecordList.setParentRecordClass(this);
            nestedRecordList.resolveReferences(model);
        }

        // resolve reference for alias query

        // resolve the filter and layout.
        resolveFilterReferences(model);

        // resolve default summary attributes
        if (defaultSummaryAttributeNames != null) {
            Map<String, AttributeField> attributeFields = getAttributeFieldMap();
            for (String fieldName : defaultSummaryAttributeNames) {
                AttributeField field = attributeFields.get(fieldName);
                if (field == null)
                    throw new WdkModelException("Summary attribute field ["
                            + fieldName + "] defined in question ["
                            + getFullName() + "] is invalid.");
                defaultSummaryAttributeFields.put(fieldName, field);
            }
        }
        defaultSummaryAttributeNames = null;

        // create column attribute fields for primary keys if needed.
        createPrimaryKeySubFields();

        // resolve the reference to the all records query
        if (allRecordsQueryRef != null) {
            Query query = (Query) model.resolveReference(allRecordsQueryRef);
            // the query should return all pk columns
            validateQuery(query);
            // and it shouldn't have any params
            if (query.getParams().length > 0)
                throw new WdkModelException("All records query "
                        + query.getFullName() + " should not have any params.");
            this.allRecordsQuery = query;
        }

        // resolve the favorite note reference to attribute field
        if (favoriteNoteFieldName != null) {
            favoriteNoteField = attributeFieldsMap.get(favoriteNoteFieldName);
            if (favoriteNoteField == null)
                throw new WdkModelException("The attribute '"
                        + favoriteNoteFieldName + "' for the default favorite "
                        + "note content of recordClass '" + getFullName()
                        + "' is invalid.");
        }

        resolved = true;
    }

    private void resolveAttributeQueryReferences(WdkModel wdkModel)
            throws WdkModelException, NoSuchAlgorithmException,
            WdkUserException, SQLException, JSONException {
        String[] paramNames = primaryKeyField.getColumnRefs();
        for (AttributeQueryReference reference : attributesQueryRefList) {
            // validate attribute query
            Query query = (Query) wdkModel.resolveReference(reference.getTwoPartName());
            validateBulkQuery(query);

            // add fields into record level, and associate columns
            Map<String, AttributeField> fields = reference.getAttributeFieldMap();
            Map<String, Column> columns = query.getColumnMap();
            for (AttributeField field : fields.values()) {
                field.setRecordClass(this);
                field.setContainer(this);
                String fieldName = field.getName();
                // check if the attribute is duplicated
                if (attributeFieldsMap.containsKey(fieldName))
                    throw new WdkModelException("The AttributeField "
                            + fieldName + " is duplicated in the recordClass "
                            + getFullName());

                // link columnAttributes with columns
                if (field instanceof ColumnAttributeField) {
                    Column column = columns.get(fieldName);
                    if (column == null)
                        throw new WdkModelException("Column is missing for "
                                + "the columnAttributeField " + fieldName
                                + " in recordClass " + getFullName());
                    ((ColumnAttributeField) field).setColumn(column);
                }
                attributeFieldsMap.put(fieldName, field);
            }
            // add user param into the original attribute query, if needed
            if (!query.getParamMap().containsKey(Utilities.PARAM_USER_ID)) {
                query.addParam(getUserParam());
            }

            Query attributeQuery = RecordClass.prepareQuery(wdkModel, query,
                    paramNames);
            attributeQueries.put(query.getFullName(), attributeQuery);
        }
    }

    private void resolveAliasQuery(WdkModel wdkModel) throws WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException,
            WdkUserException {
        if (aliasQueryRef != null) {
            Query query = (SqlQuery) wdkModel.resolveReference(aliasQueryRef);

            validateBulkQuery(query);

            Map<String, Column> columnMap = query.getColumnMap();
            // make sure the attribute query also returns old primary key
            // columns
            for (String column : primaryKeyField.getColumnRefs()) {
                column = Utilities.ALIAS_OLD_KEY_COLUMN_PREFIX + column;
                if (!columnMap.containsKey(column))
                    throw new WdkModelException("The attribute query "
                            + query.getFullName() + " of " + getFullName()
                            + " does not return the required old primary key "
                            + "column " + column);
            }

            // the alias query should also return columns for old primary key
            // columns, with a prefix "old_".
            String[] pkColumns = primaryKeyField.getColumnRefs();
            String[] paramNames = new String[pkColumns.length];
            for (int i = 0; i < pkColumns.length; i++) {
                paramNames[i] = Utilities.ALIAS_OLD_KEY_COLUMN_PREFIX
                        + pkColumns[i];
            }

            this.aliasQuery = RecordClass.prepareQuery(wdkModel, query,
                    paramNames);
        }
    }

    private void resolveTableFieldReferences(WdkModel wdkModel)
            throws WdkModelException, NoSuchAlgorithmException,
            WdkUserException, SQLException, JSONException {
        String[] paramNames = primaryKeyField.getColumnRefs();

        // resolve the references for table queries
        for (TableField tableField : tableFieldsMap.values()) {
            tableField.resolveReferences(wdkModel);

            Query query = tableField.getQuery();
            query = (Query) wdkModel.resolveReference(query.getFullName());

            // add user param into the original table query, if needed
            if (!query.getParamMap().containsKey(Utilities.PARAM_USER_ID)) {
                query.addParam(getUserParam());
            }
            Query tableQuery = RecordClass.prepareQuery(wdkModel, query,
                    paramNames);
            tableQueries.put(query.getFullName(), tableQuery);
        }

    }

    private void resolveFilterReferences(WdkModel wdkModel)
            throws WdkModelException, NoSuchAlgorithmException,
            WdkUserException, SQLException, JSONException {
        // resolve references for filter instances
        for (AnswerFilter filter : filterList) {
            filter.resolveReferences(wdkModel);

            Map<String, AnswerFilterInstance> instances = filter.getInstances();
            for (String filterName : instances.keySet()) {
                if (filterMap.containsKey(filterName))
                    throw new WdkModelException("Filter instance ["
                            + filterName + "] of type " + getFullName()
                            + " is included more than once");
                AnswerFilterInstance instance = instances.get(filterName);
                filterMap.put(filterName, instance);

                if (instance.isDefault()) {
                    if (defaultFilter != null)
                        throw new WdkModelException(
                                "The default filter of type " + getFullName()
                                        + " is defined more than once: ["
                                        + defaultFilter.getName() + "], ["
                                        + instance.getName() + "]");
                    defaultFilter = instance;
                }
                if (instance.isBooleanExpansion()) {
                    if (booleanExpansionFilter != null)
                        throw new WdkModelException("The boolean expansion "
                                + "filter of type " + getFullName()
                                + " is defined more " + "than once: ["
                                + booleanExpansionFilter.getName() + "] and ["
                                + instance.getName() + "]");
                    booleanExpansionFilter = instance;
                }
            }
        }
        filterList = null;

        // resolve references for the filter layout instances
        for (AnswerFilterLayout layout : filterLayoutMap.values()) {
            layout.resolveReferences(wdkModel);
        }
    }

    private Param getUserParam() throws WdkModelException {
        // create the missing user_id param for the attribute query
        ParamSet paramSet = wdkModel.getParamSet(Utilities.INTERNAL_PARAM_SET);
        if (paramSet.contains(Utilities.PARAM_USER_ID))
            return paramSet.getParam(Utilities.PARAM_USER_ID);

        StringParam userParam = new StringParam();
        userParam.setName(Utilities.PARAM_USER_ID);
        userParam.setNumber(true);

        userParam.excludeResources(wdkModel.getProjectId());
        userParam.resolveReferences(wdkModel);
        userParam.setResources(wdkModel);
        paramSet.addParam(userParam);
        return userParam;
    }

    void validateBulkQuery(Query query) throws WdkModelException {
        validateQuery(query);

        // Further limit the attribute/table query to have only user_id param
        // (optional). This is required to enable bulk query rewriting.
        String message = "Bulk query '" + query.getFullName()
                + "' can have only a '" + Utilities.PARAM_USER_ID
                + "' param, and it is optional.";
        Param[] params = query.getParams();
        if (params.length > 1) throw new WdkModelException(message);
        else if (params.length == 1
                && !params[0].getName().equals(Utilities.PARAM_USER_ID))
            throw new WdkModelException(message);
    }

    /**
     * validate a query, and make sure it returns primary key columns, and the
     * params of it can have only primary_key-column-mapped params (optional)
     * and user_id param (optional).
     * 
     * @param query
     * @throws WdkModelException
     */
    void validateQuery(Query query) throws WdkModelException {
        String[] pkColumns = primaryKeyField.getColumnRefs();
        Map<String, String> pkColumnMap = new LinkedHashMap<String, String>();
        for (String column : pkColumns)
            pkColumnMap.put(column, column);

        // make sure the params contain only primary key params, and (optional)
        // user_id param; but they can have less params than primary key
        // columns. WDK will append the missing ones automatically.
        for (Param param : query.getParams()) {
            String paramName = param.getName();
            if (paramName.equals(Utilities.PARAM_USER_ID)) continue;
            if (!pkColumnMap.containsKey(paramName))
                throw new WdkModelException("The attribute or table query "
                        + query.getFullName() + " has param " + paramName
                        + ", and it doesn't match with any of the primary key "
                        + "columns.");
        }

        // make sure the attribute/table query returns primary key columns
        Map<String, Column> columnMap = query.getColumnMap();
        for (String column : primaryKeyField.getColumnRefs()) {
            if (!columnMap.containsKey(column))
                throw new WdkModelException("The query " + query.getFullName()
                        + " of " + getFullName() + " doesn't return the "
                        + "required primary key column " + column);
        }
    }

    public void setResources(WdkModel wdkModel) {
        // set the resource in reporter
        for (ReporterRef reporter : reporterMap.values()) {
            reporter.setResources(wdkModel);
        }
    }

    /**
     * Called when the RecordClass is asked for a NestedRecordQuestion or
     * NestedRecordQuestionList. Cannot be done upon RecordClass initialization
     * because the Questions are not guaranteed to have their resources set,
     * which throws a NullPointerException when the Question is asked for the
     * name of its QuestionSet.
     */

    public void initNestedRecords() {
        nestedRecordQuestions = new LinkedHashMap<String, Question>();
        for (NestedRecord nextNr : nestedRecordQuestionRefs.values()) {
            nextNr.setParentRecordClass(this);
            Question q = nextNr.getQuestion();
            addNestedRecordQuestion(q);
        }

        nestedRecordListQuestions = new LinkedHashMap<String, Question>();
        for (NestedRecordList nextNrl : nestedRecordListQuestionRefs.values()) {
            nextNrl.setParentRecordClass(this);
            Question q = nextNrl.getQuestion();
            addNestedRecordListQuestion(q);
        }
    }

    private Map<String, AttributeField> sortAllAttributes()
            throws WdkModelException {
        String orderedAtts[] = attributeOrdering.split(",");
        Map<String, AttributeField> orderedAttsMap = new LinkedHashMap<String, AttributeField>();

        // primaryKey first
        orderedAttsMap.put(primaryKeyField.getName(), primaryKeyField);

        for (String nextAtt : orderedAtts) {
            nextAtt = nextAtt.trim();
            if (!orderedAttsMap.containsKey(nextAtt)) {
                AttributeField nextAttField = attributeFieldsMap.get(nextAtt);

                if (nextAttField == null) {
                    String message = "RecordClass " + getFullName()
                            + " defined attribute " + nextAtt + " in its "
                            + "attribute ordering, but that is not a valid "
                            + "attribute for this RecordClass";
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

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.WdkModelBase#excludeResources(java.lang.String)
     */
    @Override
    public void excludeResources(String projectId) throws WdkModelException {
        // exclude reporters
        for (ReporterRef reporter : reporterList) {
            if (reporter.include(projectId)) {
                reporter.excludeResources(projectId);
                String reporterName = reporter.getName();
                if (reporterMap.containsKey(reporterName))
                    throw new WdkModelException("The reporter " + reporterName
                            + " is duplicated in recordClass "
                            + this.getFullName());
                reporterMap.put(reporterName, reporter);
            }
        }
        reporterList = null;

        // exclude attributes
        for (AttributeField field : attributeFieldList) {
            if (field.include(projectId)) {
                field.excludeResources(projectId);
                String fieldName = field.getName();
                if (field instanceof PrimaryKeyAttributeField) {
                    if (this.primaryKeyField != null)
                        throw new WdkModelException("primary key field is "
                                + "duplicated in recordClass " + getFullName());
                    this.primaryKeyField = (PrimaryKeyAttributeField) field;
                } else { // other attribute fields
                    if (attributeFieldsMap.containsKey(fieldName))
                        throw new WdkModelException("The attributeField "
                                + fieldName + " is duplicated in recordClass "
                                + getFullName());
                }
                attributeFieldsMap.put(fieldName, field);
            }
        }
        attributeFieldList = null;

        // make sure there is a primary key
        if (primaryKeyField == null)
            throw new WdkModelException("The primaryKeyField of recordClass "
                    + getFullName() + " is not set. Please define a "
                    + "<primaryKeyAttribute> in the recordClass.");
        this.aliasQueryRef = primaryKeyField.getAliasQueryRef();

        // exclude table fields
        for (TableField field : tableFieldList) {
            if (field.include(projectId)) {
                field.excludeResources(projectId);
                String fieldName = field.getName();
                if (attributeFieldsMap.containsKey(fieldName))
                    throw new WdkModelException("The table " + fieldName
                            + " is duplicated in recordClass " + getFullName());
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
                    throw new WdkModelException("recordClass " + getFullName()
                            + " has more than one attributeQueryRef \""
                            + refName + "\"");
                } else {
                    queryRef.excludeResources(projectId);
                    attributesQueryRefs.put(refName, queryRef);
                }
            }
        }
        attributesQueryRefList.clear();
        attributesQueryRefList.addAll(attributesQueryRefs.values());

        // exclude nested records
        for (NestedRecord nestedRecord : nestedRecordQuestionRefList) {
            if (nestedRecord.include(projectId)) {
                String refName = nestedRecord.getTwoPartName();
                if (nestedRecordQuestionRefs.containsKey(refName)) {
                    throw new WdkModelException("recordClass " + getFullName()
                            + " has more than one nestedRecord \"" + refName
                            + "\"");
                } else {
                    nestedRecord.excludeResources(projectId);
                    nestedRecordQuestionRefs.put(refName, nestedRecord);
                }
            }
        }
        nestedRecordQuestionRefList = null;

        // exclude nested record lists
        for (NestedRecordList recordList : nestedRecordListQuestionRefList) {
            if (recordList.include(projectId)) {
                String refName = recordList.getTwoPartName();
                if (nestedRecordListQuestionRefs.containsKey(refName)) {
                    throw new WdkModelException("recordClass " + getFullName()
                            + " has more than one nestedRecordList \""
                            + refName + "\"");
                } else {
                    recordList.excludeResources(projectId);
                    nestedRecordListQuestionRefs.put(refName, recordList);
                }
            }
        }
        nestedRecordListQuestionRefList = null;

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
                    throw new WdkModelException("Filter layout [" + layoutName
                            + "] of type " + getFullName()
                            + " is included more than once");
                filterLayoutMap.put(layoutName, layout);
            }
        }
        filterLayoutList = null;

        // exclude paramValuesSets
        for (ParamValuesSet pvs : unexcludedParamValuesSets) {
            if (pvs.include(projectId)) {
                if (paramValuesSet != null)
                    throw new WdkModelException(
                            "Duplicate <paramErrors> included in record class "
                                    + getName() + " for projectId " + projectId);
                paramValuesSet = pvs;

            }
        }

        // exclude summary and sorting attribute list
        boolean hasAttributeList = false;
        for (AttributeList attributeList : attributeLists) {
            if (attributeList.include(projectId)) {
                if (hasAttributeList) {
                    throw new WdkModelException("The question " + getFullName()
                            + " has more than one <attributesList> for "
                            + "project " + projectId);
                } else {
                    this.defaultSummaryAttributeNames = attributeList.getSummaryAttributeNames();
                    this.defaultSortingMap = attributeList.getSortingAttributeMap();
                    hasAttributeList = true;
                }
            }
        }
        attributeLists = null;

        // exlcude favorite references
        for (FavoriteReference favorite : favorites) {
            if (favorite.include(projectId)) {
                if (favoriteNoteFieldName != null)
                    throw new WdkModelException("The favorite tag is "
                            + "duplicated on the recordClass " + getFullName());
                this.favoriteNoteFieldName = favorite.getNoteField();
            }
        }
        favorites = null;
    }

    public void addFilter(AnswerFilter filter) {
        filter.setRecordClass(this);
        this.filterList.add(filter);
    }

    public Map<String, AnswerFilterInstance> getFilterMap() {
        return new LinkedHashMap<String, AnswerFilterInstance>(filterMap);
    }

    public AnswerFilterInstance[] getFilters() {
        AnswerFilterInstance[] instances = new AnswerFilterInstance[filterMap.size()];
        filterMap.values().toArray(instances);
        return instances;
    }

    public AnswerFilterInstance getFilter(String filterName)
            throws WdkModelException {
        if (filterName == null) return null;
        AnswerFilterInstance instance = filterMap.get(filterName);
        if (instance == null)
            throw new WdkModelException("The name [" + filterName
                    + "] does not " + "match any filter instance of type "
                    + getFullName());
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

    public AnswerFilterLayout getFilterLayout(String layoutName)
            throws WdkModelException {
        AnswerFilterLayout layout = filterLayoutMap.get(layoutName);
        if (layout == null)
            throw new WdkModelException("The name [" + layoutName + "] does "
                    + "not match any filter layout of type " + getFullName());
        return layout;
    }

    public AnswerFilterInstance getDefaultFilter() {
        return defaultFilter;
    }

    public AnswerFilterInstance getBooleanExpansionFilter() {
        return booleanExpansionFilter;
    }

    /**
     * @return the type
     */
    public String getType() {
        return (type == null) ? getFullName() : type;
    }

    /**
     * @param type
     *            the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Make sure all pk columns has a corresponding ColumnAttributeField
     */
    private void createPrimaryKeySubFields() {
        // make sure the record has at least one attribute query, otherwise skip
        // this process
        if (attributeQueries.size() == 0) return;

        String[] pkColumns = primaryKeyField.getColumnRefs();
        // use the first attribute query as the underlying query for the column
        // attribute fields for the primary key; every atribute query returns
        // primary key columns.
        Query attributeQuery = attributeQueries.values().iterator().next();
        Map<String, Column> columns = attributeQuery.getColumnMap();
        for (String name : pkColumns) {
            if (attributeFieldsMap.containsKey(name)) continue;

            ColumnAttributeField field = new ColumnAttributeField();
            field.setName(name);
            field.setInternal(true);
            field.setRecordClass(this);
            field.setContainer(this);
            Column column = columns.get(name);
            field.setColumn(column);
            attributeFieldsMap.put(name, field);
        }
    }

    public void addAttributeList(AttributeList attributeList) {
        this.attributeLists.add(attributeList);
    }

    public Map<String, AttributeField> getSummaryAttributeFieldMap() {
        Map<String, AttributeField> attributeFields = new LinkedHashMap<String, AttributeField>();

        // always put primary key as the first field
        attributeFields.put(primaryKeyField.getName(), primaryKeyField);

        if (defaultSummaryAttributeFields.size() > 0) {
            attributeFields.putAll(defaultSummaryAttributeFields);
        } else {
            Map<String, AttributeField> nonInternalFields = getAttributeFieldMap(FieldScope.NON_INTERNAL);
            for (String fieldName : nonInternalFields.keySet()) {
                attributeFields.put(fieldName, nonInternalFields.get(fieldName));
                if (attributeFields.size() >= Utilities.DEFAULT_SUMMARY_ATTRIBUTE_SIZE)
                    break;
            }
        }
        return attributeFields;
    }

    public Map<String, Boolean> getSortingAttributeMap() {
        Map<String, Boolean> map = new LinkedHashMap<String, Boolean>();
        int count = 0;
        for (String attrName : defaultSortingMap.keySet()) {
            map.put(attrName, defaultSortingMap.get(attrName));
            count++;
            if (count >= User.SORTING_LEVEL) break;
        }

        // has to sort at least on something, primary key as default
        if (map.size() == 0) {
            String pkName = primaryKeyField.getName();
            map.put(pkName, true);
        }

        return map;
    }

    public Query getAliasQuery() {
        return aliasQuery;
    }

    public String getChecksum() {
        return null;
    }

    public void setAllRecordsQueryRef(String queryRef) {
        this.allRecordsQueryRef = queryRef;
    }

    public String getAllRecordsQueryRef() {
        return allRecordsQueryRef;
    }

    public Query getAllRecordsQuery() {
        return allRecordsQuery;
    }

    public boolean hasBasket() {
        return (allRecordsQuery != null || allRecordsQueryRef != null);
    }

    public Question getRealtimeBasketQuestion() throws WdkModelException {
        String questionName = Utilities.INTERNAL_QUESTION_SET + ".";
        questionName += getFullName().replace('.', '_');
        questionName += BasketFactory.REALTIME_BASKET_QUESTION_SUFFIX;
        return (Question) wdkModel.resolveReference(questionName);
    }

    public Question getSnapshotBasketQuestion() throws WdkModelException {
        String questionName = Utilities.INTERNAL_QUESTION_SET + ".";
        questionName += getFullName().replace('.', '_');
        questionName += BasketFactory.SNAPSHOT_BASKET_QUESTION_SUFFIX;
        return (Question) wdkModel.resolveReference(questionName);
    }

    public Question[] getTransformQuestions(boolean changeType) {
        List<Question> list = new ArrayList<Question>();
        for (QuestionSet questionSet : wdkModel.getAllQuestionSets()) {
            for (Question question : questionSet.getQuestions()) {
                if (!question.getQuery().isTransform()) continue;
                if (question.getTransformParams(this).length == 0) continue;
                String outType = question.getRecordClass().getFullName();
                if (changeType || this.getFullName().equals(outType))
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

    /**
     * @param shortDisplayName
     *            the shortDisplayName to set
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
}
