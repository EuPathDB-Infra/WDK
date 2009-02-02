package org.gusdb.wdk.model;

import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.dbms.ResultList;
import org.gusdb.wdk.model.query.Column;
import org.gusdb.wdk.model.query.Query;
import org.gusdb.wdk.model.query.QueryInstance;
import org.gusdb.wdk.model.user.User;
import org.json.JSONException;

public class RecordInstance extends AttributeValueContainer {

    private static Logger logger = Logger.getLogger(RecordInstance.class);

    private RecordClass recordClass;

    private Map<String, TableValue> tableValueCache = new LinkedHashMap<String, TableValue>();

    private User user;
    private AnswerValue answerValue;

    /**
     * 
     * @param recordClass
     * @param primaryKey
     * @throws WdkModelException
     * @throws WdkUserException
     * @throws JSONException
     * @throws SQLException
     * @throws NoSuchAlgorithmException
     */
    public RecordInstance(User user, RecordClass recordClass,
            Map<String, Object> pkValues) throws WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException,
            WdkUserException {
        this.user = user;
        this.recordClass = recordClass;

        pkValues = lookupPrimaryKeys(pkValues);
        PrimaryKeyAttributeValue primaryKey = new PrimaryKeyAttributeValue(
                recordClass.getPrimaryKeyAttributeField(), pkValues);
        setPrimaryKey(primaryKey);
    }

    /**
     * @throws WdkUserException
     * @throws JSONException
     * @throws SQLException
     * @throws WdkModelException
     * @throws NoSuchAlgorithmException
     * 
     */
    public RecordInstance(AnswerValue answerValue, Map<String, Object> pkValues)
            throws NoSuchAlgorithmException, WdkModelException, SQLException,
            JSONException, WdkUserException {
        this.answerValue = answerValue;
        this.recordClass = answerValue.getQuestion().getRecordClass();

        // the record instance from answer doesn't need the pk value translation
        PrimaryKeyAttributeValue primaryKey = new PrimaryKeyAttributeValue(
                recordClass.getPrimaryKeyAttributeField(), pkValues);
        setPrimaryKey(primaryKey);
    }

    /**
     * @return
     */
    public RecordClass getRecordClass() {
        return recordClass;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.AttributeValueContainer#getAttributeFieldMap()
     */
    @Override
    protected Map<String, AttributeField> getAttributeFieldMap() {
        return getAttributeFieldMap(FieldScope.ALL);
    }

    public Map<String, AttributeField> getAttributeFieldMap(FieldScope scope) {
        if (answerValue != null) return answerValue.getQuestion().getAttributeFieldMap(
                scope);
        else return recordClass.getAttributeFieldMap(scope);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.AttributeValueContainer#fillColumnAttributeValues
     * (org.gusdb.wdk.model.query.Query)
     */
    @Override
    protected void fillColumnAttributeValues(Query attributeQuery)
            throws WdkModelException, NoSuchAlgorithmException, JSONException,
            SQLException, WdkUserException {
        if (answerValue != null) {
            answerValue.integrateAttributesQuery(attributeQuery);
            return;
        }

        // prepare the attribute query, and make a new one,
        String queryName = attributeQuery.getFullName();

        Query query;
        if (answerValue != null) {
            Query dynaQuery = answerValue.getQuestion().getDynamicAttributeQuery();
            if (dynaQuery != null && dynaQuery.getFullName().equals(queryName)) {
                query = dynaQuery;
            } else {
                query = recordClass.getAttributeQuery(queryName);
            }
        } else {
            query = recordClass.getAttributeQuery(queryName);
        }
        QueryInstance instance = query.makeInstance(user,
                primaryKey.getValues());

        ResultList resultList = null;
        try {
            resultList = instance.getResults();
            if (!resultList.next()) {
                throw new WdkModelException("Attribute query " + queryName
                        + " doesn't return any row: " + instance.getSql());
            }

            Map<String, AttributeField> fields = recordClass.getAttributeFieldMap();
            for (Column column : query.getColumns()) {
                if (!fields.containsKey(column.getName())) continue;
                AttributeField field = fields.get(column.getName());
                if (!(field instanceof ColumnAttributeField)) continue;
                Object objValue = resultList.get(column.getName());
                ColumnAttributeValue value = new ColumnAttributeValue(
                        (ColumnAttributeField) field, objValue);
                addColumnAttributeValue(value);
            }
        } finally {
            if (resultList != null) resultList.close();
        }
        logger.debug("column attributes are cached.");
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.AttributeValueContainer#getAttributeField(java.lang
     * .String)
     */
    public AttributeField getAttributeField(String fieldName)
            throws WdkModelException {
        Map<String, AttributeField> attributeFields = getAttributeFieldMap();
        if (!attributeFields.containsKey(fieldName))
            throw new WdkModelException("The attribute field '" + fieldName
                    + "' does not exist in record instance "
                    + recordClass.getFullName());
        return attributeFields.get(fieldName);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.AttributeValueContainer#getPrimaryKey()
     */
    public PrimaryKeyAttributeValue getPrimaryKey() {
        return primaryKey;
    }

    public TableValue getTableValue(String tableName)
            throws NoSuchAlgorithmException, WdkModelException, SQLException,
            JSONException, WdkUserException {
        return getTableValue(tableName, FieldScope.ALL);
    }

    public TableValue getTableValue(String tableName, FieldScope scope)
            throws WdkModelException, NoSuchAlgorithmException, SQLException,
            JSONException, WdkUserException {
        TableValue value;
        // check if the table value has been cached
        if (tableValueCache.containsKey(tableName)) {
            value = tableValueCache.get(tableName);
        } else {
            // get the table field
            TableField tableField = recordClass.getTableField(tableName);
            value = new TableValue(user, primaryKey, tableField);
        }
        return value;
    }

    /**
     * @return Map of tableName -> TableFieldValue
     * @throws JSONException
     * @throws SQLException
     * @throws WdkModelException
     * @throws NoSuchAlgorithmException
     * @throws WdkUserException
     */
    public Map<String, TableValue> getTables() throws NoSuchAlgorithmException,
            WdkModelException, SQLException, JSONException, WdkUserException {
        return getTables(FieldScope.ALL);
    }

    /**
     * @param scope
     * @return
     * @throws NoSuchAlgorithmException
     * @throws WdkModelException
     * @throws SQLException
     * @throws JSONException
     * @throws WdkUserException
     */
    public Map<String, TableValue> getTables(FieldScope scope)
            throws NoSuchAlgorithmException, WdkModelException, SQLException,
            JSONException, WdkUserException {
        Map<String, TableValue> values = new LinkedHashMap<String, TableValue>();
        for (TableField field : recordClass.getTableFields()) {
            String name = field.getName();
            TableValue value = getTableValue(name, scope);
            values.put(name, value);
        }
        return values;
    }

    /**
     * @return Map of attributeName -> AttributeFieldValue
     * @throws JSONException
     * @throws SQLException
     * @throws WdkModelException
     * @throws NoSuchAlgorithmException
     * @throws WdkUserException
     */

    public Map<String, AttributeValue> getAttributeValueMap()
            throws NoSuchAlgorithmException, WdkModelException, SQLException,
            JSONException, WdkUserException {
        return getAttributeValueMap(FieldScope.ALL);
    }

    /**
     * @param scope
     * @return
     * @throws NoSuchAlgorithmException
     * @throws WdkModelException
     * @throws SQLException
     * @throws JSONException
     * @throws WdkUserException
     */
    public Map<String, AttributeValue> getAttributeValueMap(FieldScope scope)
            throws NoSuchAlgorithmException, WdkModelException, SQLException,
            JSONException, WdkUserException {
        Map<String, AttributeField> fields = getAttributeFieldMap(scope);
        Map<String, AttributeValue> values = new LinkedHashMap<String, AttributeValue>();

        for (AttributeField field : fields.values()) {
            String name = field.getName();
            values.put(name, getAttributeValue(name));
        }
        return values;
    }

    // change name of method?
    public Map<String, RecordInstance> getNestedRecordInstances()
            throws WdkModelException, WdkUserException,
            NoSuchAlgorithmException, SQLException, JSONException {

        Map<String, RecordInstance> riMap = new LinkedHashMap<String, RecordInstance>();
        Question nq[] = this.recordClass.getNestedRecordQuestions();

        if (nq != null) {
            for (int i = 0; i < nq.length; i++) {
                Question nextNq = nq[i];
                AnswerValue a = getNestedRecordAnswer(nextNq);
                // TODO
                // the reset function is no longer available; instead call
                // cloneAnswer() to get a new answer object and work on it
                // a.resetRecordInstanceCounter();
                RecordInstance[] records = a.getRecordInstances();

                if (records.length > 1) {
                    throw new WdkModelException("NestedQuestion "
                            + nextNq.getName() + " returned more than one "
                            + "RecordInstance when called from "
                            + this.recordClass.getName());
                }
                if (records.length > 0) {
                    riMap.put(nextNq.getName(), records[0]);
                }
            }
        }
        return riMap;
    }

    public Map<String, RecordInstance[]> getNestedRecordInstanceLists()
            throws WdkModelException, WdkUserException,
            NoSuchAlgorithmException, SQLException, JSONException {

        Question nql[] = this.recordClass.getNestedRecordListQuestions();
        Map<String, RecordInstance[]> riListMap = new LinkedHashMap<String, RecordInstance[]>();

        if (nql != null) {
            for (int i = 0; i < nql.length; i++) {
                Question nextNql = nql[i];
                AnswerValue a = getNestedRecordAnswer(nextNql);
                RecordInstance[] records = a.getRecordInstances();
                if (records != null) riListMap.put(nextNql.getName(), records);
            }
        }
        return riListMap;
    }

    private AnswerValue getNestedRecordAnswer(Question question)
            throws WdkModelException, WdkUserException,
            NoSuchAlgorithmException, SQLException, JSONException {
        Map<String, String> params = primaryKey.getValues();
        int pageStart = 1;
        int pageEnd = Utilities.MAXIMUM_RECORD_INSTANCES;
        Map<String, Boolean> sortingMap = question.getSortingAttributeMap();
        AnswerFilterInstance filter = question.getRecordClass().getDefaultFilter();
        // create an answer with maximium allowed rows
        return question.makeAnswerValue(user, params, pageStart, pageEnd,
                sortingMap, filter);
    }

    // maybe change this to RecordInstance[][] for jspwrap purposes?
    /*
     * public Vector getNestedRecordListInstances() throws WdkModelException,
     * WdkUserException{ NestedRecordList nrLists[] =
     * this.recordClass.getNestedRecordLists(); Vector nrVector = new Vector();
     * if (nrLists != null){ for (int i = 0; i < nrLists.length; i++){
     * NestedRecordList nextNrList = nrLists[i]; RecordInstance riList[] =
     * nextNrList.getRecordInstances(this); nrVector.add(riList); } } return
     * nrVector; }
     */

    public String print() throws WdkModelException, WdkUserException,
            NoSuchAlgorithmException, SQLException, JSONException {

        String newline = System.getProperty("line.separator");
        StringBuffer buf = new StringBuffer();

        Map<String, AttributeValue> attributeValues = getAttributeValueMap();

        Map<String, AttributeValue> summaryAttributeValues = new LinkedHashMap<String, AttributeValue>();
        Map<String, AttributeValue> nonSummaryAttributeValues = new LinkedHashMap<String, AttributeValue>();

        splitSummaryAttributeValue(attributeValues, summaryAttributeValues,
                nonSummaryAttributeValues);

        printAtts_Aux(buf, summaryAttributeValues);
        printAtts_Aux(buf, nonSummaryAttributeValues);

        Map<String, TableValue> tableValues = getTables();
        for (TableValue tableValue : tableValues.values()) {
            String displayName = tableValue.getTableField().getDisplayName();
            buf.append(newline);
            buf.append("[Table]: " + displayName).append(newline);
            tableValue.write(buf);
        }

        buf.append(newline);
        buf.append("Nested Records belonging to this RecordInstance:" + newline);
        Map<String, RecordInstance> nestedRecords = getNestedRecordInstances();
        for (String nextRecordName : nestedRecords.keySet()) {
            RecordInstance nextNr = nestedRecords.get(nextRecordName);
            buf.append("***" + nextRecordName + "***" + newline
                    + nextNr.printSummary() + newline);
        }

        buf.append("Nested Record Lists belonging to this RecordInstance:"
                + newline);

        Map<String, RecordInstance[]> nestedRecordLists = getNestedRecordInstanceLists();
        for (String nextRecordListName : nestedRecordLists.keySet()) {
            RecordInstance nextNrList[] = nestedRecordLists.get(nextRecordListName);
            buf.append("***" + nextRecordListName + "***" + newline);
            for (int i = 0; i < nextNrList.length; i++) {
                buf.append(nextNrList[i].printSummary() + newline);
            }
        }

        return buf.toString();
    }

    public String printSummary() throws WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException,
            WdkUserException {

        StringBuffer buf = new StringBuffer();

        Map<String, AttributeValue> attributeValues = getAttributeValueMap();

        Map<String, AttributeValue> summaryAttributeValues = new LinkedHashMap<String, AttributeValue>();
        Map<String, AttributeValue> nonSummaryAttributeValues = new LinkedHashMap<String, AttributeValue>();

        splitSummaryAttributeValue(attributeValues, summaryAttributeValues,
                nonSummaryAttributeValues);

        printAtts_Aux(buf, summaryAttributeValues);
        return buf.toString();
    }

    public String toXML() throws WdkModelException, WdkUserException,
            NoSuchAlgorithmException, SQLException, JSONException {
        return toXML("");
    }

    public String toXML(String ident) throws WdkModelException,
            WdkUserException, NoSuchAlgorithmException, SQLException,
            JSONException {
        String newline = System.getProperty("line.separator");
        StringBuffer buf = new StringBuffer();

        String rootStart = ident + "<" + getRecordClass().getFullName() + ">"
                + newline + ident + "<li>" + newline;
        String rootEnd = ident + "</li>" + newline + ident + "</"
                + getRecordClass().getFullName() + ">" + newline;
        ident = ident + "    ";
        buf.append(rootStart);

        Map<String, AttributeValue> attributeFields = getAttributeValueMap();
        for (String fieldName : attributeFields.keySet()) {
            AttributeValue value = attributeFields.get(fieldName);
            AttributeField field = value.getAttributeField();
            buf.append(ident + "<" + field.getName() + ">" + value.getValue()
                    + "</" + field.getName() + ">" + newline);
        }

        Map<String, TableValue> tableFields = getTables();
        for (String fieldName : tableFields.keySet()) {
            buf.append(ident + "<" + fieldName + ">" + newline);

            TableValue tableValue = tableFields.get(fieldName);
            tableValue.toXML(buf, "li", ident);
            buf.append(ident + "</" + fieldName + ">" + newline);
        }

        Map<String, RecordInstance> nestedRecords = getNestedRecordInstances();
        for (String nextRecordName : nestedRecords.keySet()) {
            RecordInstance nextNr = nestedRecords.get(nextRecordName);
            buf.append(nextNr.toXML(ident));
        }

        Map<String, RecordInstance[]> nestedRecordLists = getNestedRecordInstanceLists();
        for (String nextRecordListName : nestedRecordLists.keySet()) {
            RecordInstance nextNrList[] = nestedRecordLists.get(nextRecordListName);
            for (int i = 0; i < nextNrList.length; i++) {
                buf.append(nextNrList[i].toXML(ident) + newline);
            }
        }

        buf.append(rootEnd);

        return buf.toString();
    }

    // /////////////////////////////////////////////////////////////////////////
    // package methods
    // /////////////////////////////////////////////////////////////////////////

    public String[] getSummaryAttributeNames() {
        Map<String, AttributeField> summaryFields = getAttributeFieldMap(FieldScope.NON_INTERNAL);
        String[] names = new String[summaryFields.size()];
        summaryFields.keySet().toArray(names);
        return names;
    }

    public Map<String, AttributeValue> getSummaryAttributeValueMap()
            throws NoSuchAlgorithmException, WdkModelException, SQLException,
            JSONException, WdkUserException {
        return getAttributeValueMap(FieldScope.NON_INTERNAL);
    }

    // /////////////////////////////////////////////////////////////////////////
    // protected methods
    // /////////////////////////////////////////////////////////////////////////

    /**
     * Given a map of all attributes in this recordInstance, separate them into
     * those that are summary attributes and those that are not summary
     * attributes. Place results into
     * 
     * @param summaryAttributes
     *            and
     * @param nonSummaryAttributes
     *            .
     */

    private void splitSummaryAttributeValue(
            Map<String, AttributeValue> attributes,
            Map<String, AttributeValue> summaryAttributes,
            Map<String, AttributeValue> nonSummaryAttributes) {
        for (String fieldName : attributes.keySet()) {
            AttributeValue attribute = attributes.get(fieldName);
            if (attribute.getAttributeField().isInternal()) {
                summaryAttributes.put(fieldName, attribute);
            } else {
                nonSummaryAttributes.put(fieldName, attribute);
            }
        }
    }

    private void printAtts_Aux(StringBuffer buf,
            Map<String, AttributeValue> attributes)
            throws NoSuchAlgorithmException, WdkModelException, SQLException,
            JSONException, WdkUserException {
        String newline = System.getProperty("line.separator");
        for (String attributeName : attributes.keySet()) {
            AttributeValue attribute = attributes.get(attributeName);
            buf.append(attribute.getAttributeField().getDisplayName());
            buf.append(":   " + attribute.getBriefValue());
            buf.append(newline);
        }
    }

    private Map<String, Object> lookupPrimaryKeys(Map<String, Object> pkValues)
            throws WdkModelException, SQLException, NoSuchAlgorithmException,
            JSONException, WdkUserException {
        // nothing to look up
        Query aliasQuery = recordClass.getAliasQuery();
        if (aliasQuery == null) return pkValues;

        // get alias from the alias query
        Map<String, Object> newValue = new LinkedHashMap<String, Object>();
        Map<String, String> oldValues = new LinkedHashMap<String, String>();
        for (String param : pkValues.keySet()) {
            String oldParam = Utilities.ALIAS_OLD_KEY_COLUMN_PREFIX + param;
            String value = Utilities.parseValue(pkValues.get(param));
            oldValues.put(oldParam, value);
        }

        QueryInstance instance = aliasQuery.makeInstance(user, oldValues);
        ResultList resultList = instance.getResults();
        if (resultList.next()) {
            for (String param : pkValues.keySet()) {
                newValue.put(param, resultList.get(param));
            }
            // no alias found, use the original ones
        } else newValue.putAll(pkValues);
        resultList.close();

        return newValue;
    }
}
