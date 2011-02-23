/**
 * 
 */
package org.gusdb.wdk.model.query;

import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Map;

import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.query.param.Param;
import org.gusdb.wdk.model.user.User;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Jerric Gao
 * 
 */
public class ProcessQuery extends Query {

    private String processName;
    private String webServiceUrl;
    private boolean local = false;

    public ProcessQuery() {
        super();
    }

    private ProcessQuery(ProcessQuery query) {
        super(query);
        this.processName = query.processName;
        this.webServiceUrl = query.webServiceUrl;
        this.local = query.local;
    }

    /**
     * @return the processClass
     */
    public String getProcessName() {
        return this.processName;
    }

    /**
     * @param processClass
     *            the processClass to set
     */
    public void setProcessName(String processName) {
        this.processName = processName;
    }

    /**
     * @return the webServiceUrl
     */
    public String getWebServiceUrl() {
        return this.webServiceUrl;
    }

    public void setWebServiceUrl(String webServiceUrl) {
        this.webServiceUrl = webServiceUrl;
    }

    /**
     * @return the local
     */
    public boolean isLocal() {
        return this.local;
    }

    /**
     * @param local
     *            the local to set
     */
    public void setLocal(boolean local) {
        this.local = local;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.Query#resolveReferences(org.gusdb.wdk.model
     * .WdkModel)
     */
    @Override
    public void resolveQueryReferences(WdkModel wdkModel) throws WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException,
            WdkUserException {
        if (webServiceUrl == null)
            webServiceUrl = wdkModel.getModelConfig().getWebServiceUrl();
        
        // set defaults for noTranslation to true
        for (Param param : paramMap.values()) {
            if (!param.isNoTranslationSet()) param.setNoTranslation(true);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.query.Query#makeInstance()
     */
    @Override
    public QueryInstance makeInstance(User user, Map<String, String> values,
            boolean validate, int assignedWeight) throws WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException,
            WdkUserException {
        return new ProcessQueryInstance(user, this, values, validate,
                assignedWeight);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.Query#appendJSONContent(org.json.JSONObject)
     */
    @Override
    protected void appendJSONContent(JSONObject jsQuery, boolean extra)
            throws JSONException {
        if (extra) {
            jsQuery.put("process", this.processName);
            if (!local) jsQuery.put("url", this.webServiceUrl);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.query.Query#clone()
     */
    @Override
    public Query clone() {
        return new ProcessQuery(this);
    }

    /**
     * Process Query is always cached.
     */
    @Override
    public boolean isCached() {
        return true;
    }

    /* (non-Javadoc)
     * @see org.gusdb.wdk.model.query.Query#addParam(org.gusdb.wdk.model.query.param.Param)
     */
    @Override
    public void addParam(Param param) {
        super.addParam(param);
        if (!param.isNoTranslationSet()) param.setNoTranslation(true);
    }
    
    
}
