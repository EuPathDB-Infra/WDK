package org.gusdb.wdk.model.jspwrap;

import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gusdb.wdk.model.AnswerFilterInstance;
import org.gusdb.wdk.model.AnswerValue;
import org.gusdb.wdk.model.AttributeField;
import org.gusdb.wdk.model.Field;
import org.gusdb.wdk.model.FieldScope;
import org.gusdb.wdk.model.Group;
import org.gusdb.wdk.model.Question;
import org.gusdb.wdk.model.RecordClass;
import org.gusdb.wdk.model.TableField;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.query.param.AbstractEnumParam;
import org.gusdb.wdk.model.query.param.AnswerParam;
import org.gusdb.wdk.model.query.param.DatasetParam;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.query.param.TimestampParam;
import org.json.JSONException;

/**
 * A wrapper on a {@link Question} that provides simplified access for
 * consumption by a view
 */
public class QuestionBean {

    /**
     * Added by Jerric - to make QuestionBean serializable
     */
    private static final long serialVersionUID = 6353373897551871273L;

    Question question;

    private Map<String, String> params = new LinkedHashMap<String, String>();
    private UserBean user;
    private int weight;

    /**
     * the recordClass full name for the answerParams input type.
     */
    private String inputType;

    public QuestionBean(Question question) {
        this.question = question;
    }

    public RecordClassBean getRecordClass() {
        return new RecordClassBean(question.getRecordClass());
    }

    public ParamBean[] getParams() {
        Param[] params = question.getParams();
        ParamBean[] paramBeans = new ParamBean[params.length];
        for (int i = 0; i < params.length; i++) {
            paramBeans[i] = getParam(params[i]);
        }
        return paramBeans;
    }

    public Map<String, ParamBean> getParamsMap() {
        ParamBean[] paramBeans = getParams();
        Map<String, ParamBean> pMap = new LinkedHashMap<String, ParamBean>();
        for (int i = 0; i < paramBeans.length; i++) {
            ParamBean p = paramBeans[i];
            pMap.put(p.getName(), p);
        }
        return pMap;
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.Question#getParamMapByGroups()
     */
    public Map<GroupBean, Map<String, ParamBean>> getParamMapByGroups() {
        Map<Group, Map<String, Param>> paramGroups = question.getParamMapByGroups();
        Map<GroupBean, Map<String, ParamBean>> paramGroupBeans = new LinkedHashMap<GroupBean, Map<String, ParamBean>>();
        for (Group group : paramGroups.keySet()) {
            GroupBean groupBean = new GroupBean(group);
            Map<String, Param> paramGroup = paramGroups.get(group);
            Map<String, ParamBean> paramGroupBean = new LinkedHashMap<String, ParamBean>();
            for (String paramName : paramGroup.keySet()) {
                Param param = paramGroup.get(paramName);
                paramGroupBean.put(paramName, getParam(param));
            }
            paramGroupBeans.put(groupBean, paramGroupBean);
        }
        return paramGroupBeans;
    }

    /**
     * @param displayType
     * @return
     * @see org.gusdb.wdk.model.Question#getParamMapByGroups(java.lang.String)
     */
    public Map<GroupBean, Map<String, ParamBean>> getParamMapByGroups(
            String displayType) {
        Map<Group, Map<String, Param>> paramGroups = question.getParamMapByGroups(displayType);
        Map<GroupBean, Map<String, ParamBean>> paramGroupBeans = new LinkedHashMap<GroupBean, Map<String, ParamBean>>();
        for (Group group : paramGroups.keySet()) {
            GroupBean groupBean = new GroupBean(group);
            Map<String, Param> paramGroup = paramGroups.get(groupBean);
            Map<String, ParamBean> paramGroupBean = new LinkedHashMap<String, ParamBean>();
            for (String paramName : paramGroup.keySet()) {
                Param param = paramGroup.get(paramName);
                paramGroupBean.put(paramName, getParam(param));
            }
        }
        return paramGroupBeans;
    }

    private ParamBean getParam(Param param) {
        if (param instanceof AbstractEnumParam) {
            return new EnumParamBean((AbstractEnumParam) param);
        } else if (param instanceof AnswerParam) {
            return new AnswerParamBean((AnswerParam) param);
        } else if (param instanceof DatasetParam) {
            return new DatasetParamBean((DatasetParam) param);
        } else if (param instanceof TimestampParam) {
            return new TimestampParamBean((TimestampParam) param);
        } else {
            return new ParamBean(param);
        }
    }

    public Map<String, AttributeFieldBean> getSummaryAttributesMap() {
        Map<String, AttributeField> attribs = question.getSummaryAttributeFieldMap();
        Map<String, AttributeFieldBean> beanMap = new LinkedHashMap<String, AttributeFieldBean>();
        for (AttributeField field : attribs.values()) {
            beanMap.put(field.getName(), new AttributeFieldBean(field));
        }
        return beanMap;
    }

    public Map<String, AttributeFieldBean> getDisplayableAttributeFields() {
        Map<String, AttributeField> attribs = question.getAttributeFieldMap(FieldScope.NON_INTERNAL);
        Map<String, AttributeFieldBean> beanMap = new LinkedHashMap<String, AttributeFieldBean>();
        for (AttributeField field : attribs.values()) {
            beanMap.put(field.getName(), new AttributeFieldBean(field));
        }
        return beanMap;
    }

    public Map<String, AttributeFieldBean> getReportMakerAttributesMap() {
        Map<String, AttributeField> attribs = question.getAttributeFieldMap(FieldScope.REPORT_MAKER);
        Iterator<String> ai = attribs.keySet().iterator();

        Map<String, AttributeFieldBean> rmaMap = new LinkedHashMap<String, AttributeFieldBean>();
        while (ai.hasNext()) {
            String attribName = ai.next();
            rmaMap.put(attribName, new AttributeFieldBean(
                    attribs.get(attribName)));
        }
        return rmaMap;
    }

    public Map<String, TableFieldBean> getReportMakerTablesMap() {
        Map<String, TableField> tables = question.getRecordClass().getTableFieldMap(
                FieldScope.REPORT_MAKER);
        Iterator<String> ti = tables.keySet().iterator();

        Map<String, TableFieldBean> rmtMap = new LinkedHashMap<String, TableFieldBean>();
        while (ti.hasNext()) {
            String tableName = ti.next();
            rmtMap.put(tableName, new TableFieldBean(tables.get(tableName)));
        }
        return rmtMap;
    }

    public Map<String, FieldBean> getReportMakerFieldsMap() {
        Map<String, Field> fields = question.getFields(FieldScope.REPORT_MAKER);
        Iterator<String> fi = fields.keySet().iterator();

        Map<String, FieldBean> rmfMap = new LinkedHashMap<String, FieldBean>();
        while (fi.hasNext()) {
            String fieldName = fi.next();
            Field field = fields.get(fieldName);
            if (field instanceof AttributeField) {
                rmfMap.put(fieldName, new AttributeFieldBean(
                        (AttributeField) field));
            } else if (field instanceof TableField) {
                rmfMap.put(fieldName, new TableFieldBean((TableField) field));
            }
        }
        return rmfMap;
    }

    public Map<String, AttributeFieldBean> getAdditionalSummaryAttributesMap() {
        Map<String, AttributeFieldBean> all = getReportMakerAttributesMap();
        Map<String, AttributeFieldBean> dft = getSummaryAttributesMap();
        Map<String, AttributeFieldBean> opt = new LinkedHashMap<String, AttributeFieldBean>();
        Iterator<String> ai = all.keySet().iterator();
        while (ai.hasNext()) {
            String attribName = ai.next();
            if (dft.get(attribName) == null) {
                opt.put(attribName, all.get(attribName));
            }
        }
        return opt;
    }

    public String getName() {
        return question.getName();
    }

    public String getFullName() {
        return question.getFullName();
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.Question#getQuestionSetName()
     */
    public String getQuestionSetName() {
        return question.getQuestionSetName();
    }

    public String getDisplayName() {
        return question.getDisplayName();
    }

    public String getHelp() {
        return question.getHelp();
    }

    /**
     * Called by the controller
     * 
     * @param paramErrors
     *            Map of paramName-->value
     * @param start
     *            Index of the first record to include in the answer
     * @param end
     *            Index of the last record to include in the answer
     * @throws JSONException
     * @throws SQLException
     * @throws NoSuchAlgorithmException
     */
    public AnswerValueBean makeAnswerValue(UserBean user,
            Map<String, String> paramValues, int pageStart, int pageEnd,
            Map<String, Boolean> sortingMap, String filterName,
            int assignedWeight) throws WdkModelException, WdkUserException,
            NoSuchAlgorithmException, SQLException, JSONException {
        AnswerFilterInstance filter = null;
        if (filterName != null) {
            RecordClass recordClass = question.getRecordClass();
            filter = recordClass.getFilter(filterName);
        }
        AnswerValue answerValue = question.makeAnswerValue(user.getUser(),
                paramValues, pageStart, pageEnd, sortingMap, filter,
                assignedWeight);
        return new AnswerValueBean(answerValue);
    }

    public String getDescription() {
        return question.getDescription();
    }

    public String getSummary() {
        return question.getSummary();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.Question#getCategory()
     */
    @Deprecated
    public String getCategory() {
        return question.getCategory();
    }

    /**
     * A indicator to the controller whether this question bean should make
     * answer beans that contains all records in one page or not.
     * 
     * @return
     * @see org.gusdb.wdk.model.Question#isFullAnswer()
     */
    public boolean isFullAnswer() {
        return question.isFullAnswer();
    }

    /**
     * make an answer bean with default page size.
     * 
     * @param paramErrors
     * @return
     * @throws WdkUserException
     * @throws WdkModelException
     * @throws JSONException
     * @throws SQLException
     * @throws NoSuchAlgorithmException
     * @see org.gusdb.wdk.model.Question#makeAnswer(java.util.Map)
     */
    public AnswerValueBean makeAnswerValue(UserBean user,
            Map<String, String> paramValues, int assignedWeight)
            throws WdkUserException, WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException {
        return new AnswerValueBean(question.makeAnswerValue(user.getUser(),
                paramValues, assignedWeight));
    }

    /**
     * @param propertyListName
     * @return
     * @see org.gusdb.wdk.model.Question#getPropertyList(java.lang.String)
     */
    public String[] getPropertyList(String propertyListName) {
        return question.getPropertyList(propertyListName);
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.Question#getPropertyLists()
     */
    public Map<String, String[]> getPropertyLists() {
        return question.getPropertyLists();
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.Question#isNoSummaryOnSingleRecord()
     */
    public boolean isNoSummaryOnSingleRecord() {
        return question.isNoSummaryOnSingleRecord();
    }

    public boolean getIsBoolean() {
        return question.getQuery().isBoolean();
    }

    public boolean getIsCombined() {
        return question.getQuery().isCombined();
    }

    public boolean getIsTransform() {
        return question.getQuery().isTransform();
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }

    public List<AnswerParamBean> getTransformParams() throws WdkModelException {
        List<AnswerParamBean> beans = new ArrayList<AnswerParamBean>();
        RecordClass input = question.getWdkModel().getRecordClass(inputType);
        for (AnswerParam answerParam : question.getTransformParams(input)) {
            beans.add(new AnswerParamBean(answerParam));
        }
        return beans;
    }

    public void setParam(String nameValue) {
        String[] parts = nameValue.split("=");
        String name = parts[0].trim();
        String value = parts[1].trim();
        params.put(name, value);
    }

    public void setUser(UserBean user) {
        this.user = user;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public AnswerValueBean getAnswerValue() throws WdkUserException,
            WdkModelException, NoSuchAlgorithmException, SQLException,
            JSONException {
        if (user == null)
            throw new WdkUserException("User is not set. Please set user to "
                    + "the questionBean before calling to create answerValue.");

        AnswerValue answerValue = question.makeAnswerValue(user.getUser(),
                params, weight);

        // reset the params
        params.clear();

        return new AnswerValueBean(answerValue);
    }
}
