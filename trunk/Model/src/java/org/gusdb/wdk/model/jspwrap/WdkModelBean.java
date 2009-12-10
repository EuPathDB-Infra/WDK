package org.gusdb.wdk.model.jspwrap;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.gusdb.wdk.model.Category;
import org.gusdb.wdk.model.Question;
import org.gusdb.wdk.model.QuestionSet;
import org.gusdb.wdk.model.RecordClass;
import org.gusdb.wdk.model.RecordClassSet;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.xml.XmlQuestionSet;
import org.gusdb.wdk.model.xml.XmlRecordClassSet;

/**
 * A wrapper on a {@link WdkModel} that provides simplified access for
 * consumption by a view
 */
public class WdkModelBean {

    WdkModel model;

    public WdkModelBean(WdkModel model) {
        this.model = model;
    }

    public Map<String, String> getProperties() {
        return model.getProperties();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.WdkModel#getVersion()
     */
    public String getVersion() {
        return model.getVersion();
    }

    public String getDisplayName() {
        return model.getDisplayName();
    }

    public String getIntroduction() {
        return model.getIntroduction();
    }

    // to do: figure out how to do this without using getModel()
    public WdkModel getModel() {
        return this.model;
    }

    /**
     * used by the controller
     */
    public RecordClassBean findRecordClass(String recClassRef)
            throws WdkUserException, WdkModelException {
        return new RecordClassBean(model.getRecordClass(recClassRef));
    }

    public Map<String, CategoryBean> getCategoryMap() {
        Map<String, CategoryBean> beans = new LinkedHashMap<String, CategoryBean>();
        for (Category category : model.getCategoryMap().values()) {
            CategoryBean bean = new CategoryBean(category);
            beans.put(category.getName(), bean);
        }
        return beans;
    }

    public Map<String, CategoryBean> getRootCategoryMap() {
        Map<String, CategoryBean> beans = new LinkedHashMap<String, CategoryBean>();
        for (Category category : model.getRootCategoryMap().values()) {
            CategoryBean bean = new CategoryBean(category);
            beans.put(category.getName(), bean);
        }
        return beans;
    }

    /**
     * @return Map of questionSetName --> {@link QuestionSetBean}
     */
    public Map<String, QuestionSetBean> getQuestionSetsMap() {
        Map<String, QuestionSet> qSets = model.getQuestionSets();
        Map<String, QuestionSetBean> qSetBeans = new LinkedHashMap<String, QuestionSetBean>();
        for (String qSetKey : qSets.keySet()) {
            QuestionSetBean qSetBean = new QuestionSetBean(qSets.get(qSetKey));
            qSetBeans.put(qSetKey, qSetBean);
        }
        return qSetBeans;
    }

    public QuestionSetBean[] getQuestionSets() {
        Map<String, QuestionSetBean> qSetMap = getQuestionSetsMap();
        QuestionSetBean[] qSetBeans = new QuestionSetBean[qSetMap.size()];
        qSetMap.values().toArray(qSetBeans);
        return qSetBeans;
    }

    public RecordClassBean[] getRecordClasses() {

        Vector<RecordClassBean> recordClassBeans = new Vector<RecordClassBean>();
        RecordClassSet sets[] = model.getAllRecordClassSets();
        for (int i = 0; i < sets.length; i++) {
            RecordClassSet nextSet = sets[i];
            RecordClass recordClasses[] = nextSet.getRecordClasses();
            for (int j = 0; j < recordClasses.length; j++) {
                RecordClass nextClass = recordClasses[j];
                RecordClassBean bean = new RecordClassBean(nextClass);
                recordClassBeans.addElement(bean);
            }
        }

        RecordClassBean[] returnedBeans = new RecordClassBean[recordClassBeans.size()];
        for (int i = 0; i < recordClassBeans.size(); i++) {
            RecordClassBean nextReturnedBean = recordClassBeans.elementAt(i);
            returnedBeans[i] = nextReturnedBean;
        }
        return returnedBeans;
    }

    public Map<String, RecordClassBean> getRecordClassMap() {
        Map<String, RecordClassBean> recordClassMap = new LinkedHashMap<String, RecordClassBean>();
        RecordClassSet[] rcsets = model.getAllRecordClassSets();
        for (RecordClassSet rcset : rcsets) {
            RecordClass[] rcs = rcset.getRecordClasses();
            for (RecordClass rc : rcs) {
                recordClassMap.put(rc.getFullName(), new RecordClassBean(rc));
            }
        }
        return recordClassMap;
    }

    public Map<String, String> getRecordClassTypes() {
        RecordClassBean[] recClasses = getRecordClasses();
        Map<String, String> types = new LinkedHashMap<String, String>();
        for (RecordClassBean r : recClasses) {
            types.put(r.getFullName(), r.getType());
        }
        return types;
    }

    public XmlQuestionSetBean[] getXmlQuestionSets() {
        XmlQuestionSet[] qsets = model.getXmlQuestionSets();
        XmlQuestionSetBean[] qsetBeans = new XmlQuestionSetBean[qsets.length];
        for (int i = 0; i < qsets.length; i++) {
            qsetBeans[i] = new XmlQuestionSetBean(qsets[i]);
        }
        return qsetBeans;
    }

    /**
     * @return Map of questionSetName --> {@link XmlQuestionSetBean}
     */
    public Map<String, XmlQuestionSetBean> getXmlQuestionSetsMap() {
        XmlQuestionSetBean[] qSets = getXmlQuestionSets();
        Map<String, XmlQuestionSetBean> qSetsMap = new LinkedHashMap<String, XmlQuestionSetBean>();
        for (int i = 0; i < qSets.length; i++) {
            qSetsMap.put(qSets[i].getName(), qSets[i]);
        }
        return qSetsMap;
    }

    public XmlRecordClassSetBean[] getXmlRecordClassSets() {
        XmlRecordClassSet[] rcs = model.getXmlRecordClassSets();
        XmlRecordClassSetBean[] rcBeans = new XmlRecordClassSetBean[rcs.length];
        for (int i = 0; i < rcs.length; i++) {
            rcBeans[i] = new XmlRecordClassSetBean(rcs[i]);
        }
        return rcBeans;
    }

    public UserFactoryBean getUserFactory() throws WdkUserException {
        return new UserFactoryBean(model.getUserFactory());
    }

    /**
     * @param questionFullName
     * @return
     * @see org.gusdb.wdk.model.WdkModel#getQuestionDisplayName(java.lang.String)
     */
    public String getQuestionDisplayName(String questionFullName) {
        return model.getQuestionDisplayName(questionFullName);
    }

    public String getProjectId() {
        return model.getProjectId();
    }

    public String getName() {
        return model.getProjectId();
    }

    /**
     * @param paramName
     * @return
     * @see org.gusdb.wdk.model.WdkModel#queryParamDisplayName(java.lang.String)
     */
    public String queryParamDisplayName(String paramName) {
        return model.queryParamDisplayName(paramName);
    }

    /**
     * @return
     * @throws NoSuchAlgorithmException
     * @throws WdkModelException
     * @throws IOException
     * @see org.gusdb.wdk.model.WdkModel#getSecretKey()
     */
    public String getSecretKey() throws NoSuchAlgorithmException,
            WdkModelException, IOException {
        return model.getSecretKey();
    }

    public UserBean getSystemUser() throws NoSuchAlgorithmException,
            WdkUserException, WdkModelException, SQLException {
        return new UserBean(model.getSystemUser());
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.WdkModel#getReleaseDate()
     */
    public String getReleaseDate() {
        return model.getReleaseDate();
    }

}
