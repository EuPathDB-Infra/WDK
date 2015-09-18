package org.gusdb.wdk.model.jspwrap;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.TreeNode;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.answer.AnswerFilterInstance;
import org.gusdb.wdk.model.answer.AnswerValue;
import org.gusdb.wdk.model.filter.FilterSummary;
import org.gusdb.wdk.model.query.BooleanQuery;
import org.gusdb.wdk.model.query.param.AnswerParam;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.question.Question;
import org.gusdb.wdk.model.record.FieldScope;
import org.gusdb.wdk.model.record.RecordClass;
import org.gusdb.wdk.model.record.RecordInstance;
import org.gusdb.wdk.model.record.TableField;
import org.gusdb.wdk.model.record.attribute.AttributeField;
import org.gusdb.wdk.model.report.Reporter;
import org.gusdb.wdk.model.user.Step;
import org.gusdb.wdk.model.user.User;
import org.json.JSONObject;

/**
 * A wrapper on a {@link AnswerValue} that provides simplified access for
 * consumption by a view
 */
public class AnswerValueBean {

    private class RecordBeanList implements Iterator<RecordBean> {

        private RecordInstance[] instances;
        private int position = 0;

        private RecordBeanList(RecordInstance[] instances) {
            this.instances = instances;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Iterator#hasNext()
         */
        @Override
        public boolean hasNext() {
            return position < instances.length;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Iterator#next()
         */
        @Override
        public RecordBean next() {
            return new RecordBean(answerValue.getUser(), instances[position++]);
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Iterator#remove()
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported");
        }

    }

    private static Logger logger = Logger.getLogger(AnswerValueBean.class);

    private AnswerValue answerValue;
    Map<?, ?> downloadConfigMap = null;

    String customName = null;

    public AnswerValueBean(AnswerValue answerValue) {
        this.answerValue = answerValue;
    }
    
    public AnswerValue getAnswerValue() {
        return answerValue;
    }

    /**
     * @return A Map of param displayName --> param value.
     */
    public Map<String, String> getParams() {
        return answerValue.getParamDisplays();
    }

    public Map<String, String> getInternalParams() {
        return answerValue.getIdsQueryInstance().getParamStableValues();
    }

    public String getChecksum() throws WdkModelException, WdkUserException {
        return answerValue.getChecksum();
    }

    /**
     * @return opertation for boolean answer
     */
    public String getBooleanOperation() {
        if (!getIsBoolean()) {
            throw new RuntimeException("getBooleanOperation can not be called"
                    + " on simple AnswerBean");
        }
        Map<String, String> params = answerValue.getIdsQueryInstance().getParamStableValues();
        return params.get(BooleanQuery.OPERATOR_PARAM);
    }

    /**
     * @return first child answer for boolean answer, null if it is an answer
     *         for a simple question.
     * @throws WdkUserException 
     */
    public AnswerValueBean getFirstChildAnswer()
            throws WdkModelException, WdkUserException {
        if (!getIsCombined()) {
            throw new RuntimeException("getFirstChildAnswer can not be called"
                    + " on simple AnswerBean");
        }
        AnswerParam param = null;
        Map<String, String> params = answerValue.getIdsQueryInstance().getParamStableValues();
        if (getIsBoolean()) {
            BooleanQuery query = (BooleanQuery) answerValue.getIdsQueryInstance().getQuery();
            param = query.getLeftOperandParam();
        } else {
            Map<String, Param> paramMap = answerValue.getIdsQueryInstance().getQuery().getParamMap();
            for (Param p : paramMap.values()) {
                if (p instanceof AnswerParam) {
                    param = (AnswerParam) p;
                    break;
                }
            }
            if (param == null)
                throw new RuntimeException(
                        "combined question has no AnswerParam.");
        }
        String stableValue = params.get(param.getName());
        User user = answerValue.getUser();
        Step step = (Step)param.getRawValue(user, stableValue, params);
        return new AnswerValueBean(step.getAnswerValue());
    }

    /**
     * @return second child answer for boolean answer, null if it is an answer
     *         for a simple question.
     * @throws WdkUserException 
     */
    public AnswerValueBean getSecondChildAnswer()
            throws WdkModelException, WdkUserException {
        if (!getIsBoolean()) {
            throw new RuntimeException("getSecondChildAnswer can not be called"
                    + " on simple AnswerBean");
        }
        BooleanQuery query = (BooleanQuery) answerValue.getIdsQueryInstance().getQuery();
        Map<String, String> params = answerValue.getIdsQueryInstance().getParamStableValues();
        AnswerParam param = query.getRightOperandParam();
        String stableValue = params.get(param.getName());
        User user = answerValue.getUser();
        Step step = (Step)param.getRawValue(user, stableValue, params);
        return new AnswerValueBean(step.getAnswerValue());
    }

    public int getPageSize() throws WdkModelException, WdkUserException {
        return answerValue.getPageSize();
    }

    public int getPageCount() throws WdkModelException, WdkUserException {
        return answerValue.getPageCount();
    }

    public int getResultSize() throws WdkModelException, WdkUserException {
        return answerValue.getResultSize();
    }

    public Map<String, Integer> getResultSizesByProject()
            throws WdkModelException, WdkUserException {
        return answerValue.getResultSizesByProject();
    }

    public boolean getIsBoolean() {
        return answerValue.getIdsQueryInstance().getQuery().isBoolean();
    }

    public boolean getIsCombined() {
        return answerValue.getIdsQueryInstance().getQuery().isCombined();
    }

    public boolean getIsTransform() {
        return answerValue.getIdsQueryInstance().getQuery().isTransform();
    }

    public RecordClassBean getRecordClass() {
        return new RecordClassBean(answerValue.getQuestion().getRecordClass());
    }

    public QuestionBean getQuestion() throws WdkModelException {
        return new QuestionBean(answerValue.getQuestion());
    }

    /**
     * @return A list of {@link RecordBean}s.
     * @throws WdkUserException 
     */
    public Iterator<RecordBean> getRecords() throws WdkModelException, WdkUserException {
      try {
        return new RecordBeanList(answerValue.getRecordInstances());
      }
      catch (WdkModelException | WdkUserException ex) {
        logger.error(ex.getMessage(), ex);
        throw ex;
      }
    }
    
    public void setDownloadConfigMap(Map<?, ?> downloadConfigMap) {
        this.downloadConfigMap = downloadConfigMap;
    }

    public AttributeFieldBean[] getSummaryAttributes() throws WdkModelException {
        Map<String, AttributeField> attribs = answerValue.getSummaryAttributeFieldMap();
        AttributeFieldBean[] beans = new AttributeFieldBean[attribs.size()];
        int index = 0;
        for (AttributeField field : attribs.values()) {
            beans[index++] = new AttributeFieldBean(field);
        }

        return beans;
    }

    public String[] getSummaryAttributeNames() throws WdkModelException {
        AttributeFieldBean[] sumAttribs = getSummaryAttributes();
        String[] names = new String[sumAttribs.length];
        for (int i = 0; i < sumAttribs.length; i++) {
            names[i] = sumAttribs[i].getName();
        }
        return names;
    }

    public AttributeFieldBean[] getDownloadAttributes() throws WdkModelException {
        AttributeFieldBean[] sumAttribs = getSummaryAttributes();
        if (downloadConfigMap == null || downloadConfigMap.size() == 0) {
            return sumAttribs;
        }

        AttributeFieldBean[] rmAttribs = getAllReportMakerAttributes();
        Vector<AttributeFieldBean> v = new Vector<AttributeFieldBean>();
        for (int i = 0; i < rmAttribs.length; i++) {
            String attribName = rmAttribs[i].getName();
            Object configStatus = downloadConfigMap.get(attribName);
            // System.err.println("DEBUG AnswerBean: configStatus for " +
            // attrName + " is " + configStatus);
            if (configStatus != null) {
                v.add(rmAttribs[i]);
            }
        }
        int size = v.size();
        AttributeFieldBean[] downloadAttribs = new AttributeFieldBean[size];
        v.copyInto(downloadAttribs);
        return downloadAttribs;
    }

    public AttributeFieldBean[] getAllReportMakerAttributes() {
        Question question = answerValue.getQuestion();
        Map<String, AttributeField> attribs = question.getAttributeFieldMap(FieldScope.REPORT_MAKER);
        Iterator<String> ai = attribs.keySet().iterator();
        Vector<AttributeFieldBean> v = new Vector<AttributeFieldBean>();
        while (ai.hasNext()) {
            String attribName = ai.next();
            v.add(new AttributeFieldBean(attribs.get(attribName)));
        }
        int size = v.size();
        AttributeFieldBean[] rmAttribs = new AttributeFieldBean[size];
        v.toArray(rmAttribs);
        return rmAttribs;
    }

	  // adding "yes" will hint RecordClass to use table display names 
    public TableFieldBean[] getAllReportMakerTables() {
        RecordClass recordClass = answerValue.getQuestion().getRecordClass();
        Map<String, TableField> tables = recordClass.getTableFieldMap(FieldScope.REPORT_MAKER,"yes");
				// sorting alphabetically by internal table name
				Map<String, TableField> treeMapTables = new TreeMap<String, TableField>(tables);
        Iterator<String> ti = treeMapTables.keySet().iterator();
        Vector<TableFieldBean> v = new Vector<TableFieldBean>();
        while (ti.hasNext()) {
            String tableName = ti.next();
            v.add(new TableFieldBean(treeMapTables.get(tableName)));
        }
        int size = v.size();
        TableFieldBean[] rmTables = new TableFieldBean[size];
        v.toArray(rmTables);
        return rmTables;
    }

    public String[] getDownloadAttributeNames() throws WdkModelException {
        AttributeFieldBean[] downloadAttribs = getDownloadAttributes();
        Vector<String> v = new Vector<String>();
        for (int i = 0; i < downloadAttribs.length; i++) {
            v.add(downloadAttribs[i].getName());
        }
        int size = v.size();
        String[] downloadAttribNames = new String[size];
        v.copyInto(downloadAttribNames);
        return downloadAttribNames;
    }
    
    public void setCustomName(String name) {
        customName = name;
    }

    public String getCustomName() {
        return customName;
    }

    public boolean getIsDynamic() {
        return answerValue.isDynamic();
    }

    /**
     * for controller: reset counter for download purpose
     */
    public void resetAnswerRowCursor() {
        logger.debug("reset answer cursor");
        int startIndex = answerValue.getStartIndex();
        int endIndex = answerValue.getEndIndex();
        answerValue = new AnswerValue(answerValue, startIndex, endIndex);
    }

    /**
     * for controller: reset counter for download purpose
     */
    public boolean getResetAnswerRowCursor() {
        resetAnswerRowCursor();
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.Answer#getResultMessage()
     */
    public String getResultMessage() {
        String message = answerValue.getResultMessage();
        System.out.println("Result message from AnswerBean: " + message);
        return message;
    }

    /**
     * @param reporterName
     * @param config
     * @return
     * @throws WdkUserException 
     * @see org.gusdb.wdk.model.AnswerValue#getReport(java.lang.String,
     *      java.util.Map)
     */
    public Reporter createReport(String reporterName, Map<String, String> config)
            throws WdkModelException, WdkUserException {
        return answerValue.createReport(reporterName, config);
    }

    /**
     * @param reporterName
     * @param config
     * @return
     * @throws WdkUserException 
     * @see org.gusdb.wdk.model.AnswerValue#getReport(java.lang.String,
     *      java.util.Map)
     */
    public Reporter createReport(String reporterName, JSONObject config)
            throws WdkModelException, WdkUserException {
        return answerValue.createReport(reporterName, config);
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.AnswerValue#getSortingAttributeNames()
     */
    public String[] getSortingAttributeNames() {
        Map<String, Boolean> sortingFields = answerValue.getSortingMap();
        String[] array = new String[sortingFields.size()];
        sortingFields.keySet().toArray(array);
        return array;
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.AnswerValue#getSortingAttributeOrders()
     */
    public boolean[] getSortingAttributeOrders() {
        Map<String, Boolean> sortingFields = answerValue.getSortingMap();
        boolean[] array = new boolean[sortingFields.size()];
        int index = 0;
        for (boolean order : sortingFields.values()) {
            array[index++] = order;
        }
        return array;
    }

    public AttributeFieldBean[] getDisplayableAttributes() {
        List<AttributeField> fields = answerValue.getDisplayableAttributes();
        AttributeFieldBean[] fieldBeans = new AttributeFieldBean[fields.size()];
        int index = 0;
        for (AttributeField field : fields) {
            fieldBeans[index] = new AttributeFieldBean(field);
            index++;
        }
        return fieldBeans;
    }

    public TreeNode getDisplayableAttributeTree() throws WdkModelException {
    	return answerValue.getDisplayableAttributeTree();
    }

    public TreeNode getReportMakerAttributeTree() throws WdkModelException {
    	return answerValue.getReportMakerAttributeTree();
    }

    public void setFilter(String filterName) {
        answerValue.setFilterInstance(filterName);
    }

    public int getFilterSize(String filterName)
            throws WdkModelException, WdkUserException {
        return answerValue.getFilterSize(filterName);
    }
    
    

    public Map<String, Integer> getFilterSizes() {
      return answerValue.getFilterSizes();
    }

    public AnswerFilterInstanceBean getFilter() {
        AnswerFilterInstance filter = answerValue.getFilter();
        if (filter == null) return null;
        return new AnswerFilterInstanceBean(filter);
    }

    public List<String[]> getAllIds() throws WdkModelException, WdkUserException {
        return answerValue.getAllIds();
    }

    /**
     * @return
     * @throws WdkUserException 
     * @see org.gusdb.wdk.model.AnswerValue#getAllPkValues()
     */
    public String getAllIdList() throws WdkModelException, WdkUserException {
        List<String[]> pkValues = answerValue.getAllIds();
        StringBuffer buffer = new StringBuffer();
        for (String[] pkValue : pkValues) {
            if (buffer.length() > 0) buffer.append("\n");
            for (int i = 0; i < pkValue.length; i++) {
                if (i > 0) buffer.append(", ");
                buffer.append(pkValue[i]);
            }
        }
        return buffer.toString();
    }

    public AnswerValueBean makeAnswerValue(int pageStart, int pageEnd) {
        AnswerValue answerValue = new AnswerValue(this.answerValue, pageStart,
                pageEnd);
        return new AnswerValueBean(answerValue);
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.AnswerValue#getEndIndex()
     */
    public int getEndIndex() {
        return answerValue.getEndIndex();
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.AnswerValue#getStartIndex()
     */
    public int getStartIndex() {
        return answerValue.getStartIndex();
    }

    /**
     * @param startIndex
     * @param endIndex
     * @see org.gusdb.wdk.model.AnswerValue#setPageIndex(int, int)
     */
    public void setPageIndex(int startIndex, int endIndex) {
        answerValue.setPageIndex(startIndex, endIndex);
    }
    
    /**
     * Temporary method to allow easy on/off of checkbox tree
     * for value selection.
     * 
     * @return whether checkbox tree should be used (columns layout otherwise)
     */
    public boolean getUseCheckboxTree() {
    	return true;
    }

    public FilterSummary getFilterSummary(String filterName) throws WdkModelException, WdkUserException {
      return answerValue.getFilterSummary(filterName);
    }
    
    public String getIdSql() throws WdkModelException, WdkUserException {
      return answerValue.getIdSql();
    }    
}
