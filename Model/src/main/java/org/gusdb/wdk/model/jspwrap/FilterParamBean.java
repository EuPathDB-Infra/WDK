package org.gusdb.wdk.model.jspwrap;

import java.util.List;
import java.util.Map;

import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.query.param.FilterParam;
import org.gusdb.wdk.model.query.param.FilterParamHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FilterParamBean extends EnumParamBean {

  private final FilterParam filterParam;

  public FilterParamBean(FilterParam param) {
    super(param);
    filterParam = param;
  }

  /**
   * @param user
   * @return
   * @throws WdkModelException
   * @throws WdkUserException
   * @see org.gusdb.wdk.model.query.param.FlatVocabParam#getMetadataSpec(org.gusdb.wdk.model.user.User)
   */
  public Map<String, Map<String, String>> getMetadataSpec(UserBean user) throws WdkModelException,
      WdkUserException {
    return filterParam.getMetadataSpec(user.getUser(), _contextValues);
  }

  /**
   * @param user
   * @param contextValues
   * @return
   * @throws WdkModelException
   * @throws WdkUserException
   * @see org.gusdb.wdk.model.query.param.FlatVocabParam#getMetadata(org.gusdb.wdk.model.user.User,
   *      java.util.Map)
   */
  public Map<String, Map<String, String>> getMetadata(UserBean user, Map<String, String> contextValues)
      throws WdkModelException, WdkUserException {
    return filterParam.getMetadata(user.getUser(), contextValues);
  }

  public Map<String, List<String>> getMetadata(String property) throws WdkModelException, WdkUserException {
    return filterParam.getMetaData(_userBean.getUser(), _contextValues, property, getVocabInstance());
  }

  /**
   * @param _userBean
   * @param _contextValues
   * @return
   * @throws WdkModelException
   * @throws WdkUserException
   * @see org.gusdb.wdk.model.query.param.AbstractEnumParam#getJSONValues(org.gusdb.wdk.model.user.User,
   *      java.util.Map)
   */
  @Override
  public JSONObject getJsonValues() throws WdkModelException, WdkUserException {
    return filterParam.getJsonValues(_userBean.getUser(), _contextValues);
  }

  @Override
  public void setStableValue(String stableValue) throws WdkModelException {
    // System.err.println("Stable value = " + stableValue + ", and default value = " + getDefault());

    if (stableValue == null)
      stableValue = getDefault();
    this._stableValue = stableValue;

    // also set the current values
    if (stableValue != null) {
      try {
        // System.err.println("**********" + getName() + "=" + stableValue );
        JSONObject jsValue = new JSONObject(stableValue);
        JSONArray jsTerms = jsValue.getJSONArray(FilterParamHandler.TERMS_KEY);
        String[] terms = new String[jsTerms.length()];
        for (int i = 0; i < terms.length; i++) {
          terms[i] = jsTerms.getString(i);
        }
        setCurrentValues(terms);
      }
      catch (JSONException ex) {
        throw new WdkModelException(ex);
      }
    }
  }

  @Override
  public String getDefault() throws WdkModelException {
    return filterParam.getDefault(_userBean.getUser(), _contextValues);
  }

  public String getDefaultColumns() {
    return filterParam.getDefaultColumns();
  }

  public boolean getTrimMetadataTerms() {
    return filterParam.getTrimMetadataTerms();
  }

  public String getFilterDataTypeDisplayName() {
    return filterParam.getFilterDataTypeDisplayName();
  }
}
