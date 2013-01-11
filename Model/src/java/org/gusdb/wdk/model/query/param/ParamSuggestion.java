/**
 * 
 */
package org.gusdb.wdk.model.query.param;

import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelBase;
import org.gusdb.wdk.model.WdkModelException;

/**
 * An object representation of the suggestion tag. The default values can be
 * defined in this tag.
 * 
 * @author Jerric
 * 
 */
public class ParamSuggestion extends WdkModelBase {

  /**
   * The sample provides a text display of possible values a param can take.
   * However, since the information can be put into default value, and param's
   * description, the sample is no longer needed.
   */
  @Deprecated
  private String sample;
  private String defaultValue;
  /**
   * The allowEmpty & emptyValue is mainly used by stringParam. default, user
   * has to provide some input to a string param, but if this flag is true, then
   * the input box of a string param can be left empty, and the empty value will
   * be used as user's input.
   */
  private boolean allowEmpty = false;
  private String emptyValue = "";

  /**
   * only used by abstractEnumParam
   */
  private String selectMode;

  /**
   * Only used by datasetParam, determines what input type to be selected as
   * default.
   */
  private String defaultType;

  /**
   * the default constructor is used by the digester
   */
  public ParamSuggestion() {}

  /**
   * the copy constructor is used by the clone methods
   */
  public ParamSuggestion(ParamSuggestion suggestion) {
    this.sample = suggestion.sample;
    this.defaultValue = suggestion.defaultValue;
    this.allowEmpty = suggestion.allowEmpty;
    this.emptyValue = suggestion.emptyValue;
    this.selectMode = suggestion.selectMode;
    this.defaultType = suggestion.defaultType;
  }

  /**
   * @return the allowEmpty
   */
  public boolean isAllowEmpty() {
    return this.allowEmpty;
  }

  /**
   * @param allowEmpty
   *          the allowEmpty to set
   */
  public void setAllowEmpty(boolean allowEmpty) {
    this.allowEmpty = allowEmpty;
  }

  /**
   * @return the defaultValue
   */
  public String getDefault() {
    return this.defaultValue;
  }

  /**
   * @param defaultValue
   *          the defaultValue to set
   */
  public void setDefault(String defaultValue) {
    this.defaultValue = (defaultValue.trim().length() == 0) ? null
        : defaultValue;
  }

  /**
   * @return the sample
   */
  @Deprecated
  public String getSample() {
    return this.sample;
  }

  /**
   * @param sample
   *          the sample to set
   */
  @Deprecated
  public void setSample(String sample) {
    this.sample = sample;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.WdkModelBase#excludeResources(java.lang.String)
   */
  @Override
  public void excludeResources(String projectId) {
    // do nothing
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.WdkModelBase#resolveReferences(org.gusdb.wdk.model
   * .WdkModel)
   */
  @Override
  public void resolveReferences(WdkModel wodkModel) throws WdkModelException {
    // do nothing
  }

  /**
   * @return the emptyValue
   */
  public String getEmptyValue() {
    return emptyValue;
  }

  /**
   * @param emptyValue
   *          the emptyValue to set
   */
  public void setEmptyValue(String emptyValue) {
    this.emptyValue = emptyValue;
  }

  /**
   * @return the selectMode
   */
  public String getSelectMode() {
    return selectMode;
  }

  /**
   * @param selectMode
   *          the selectMode to set
   */
  public void setSelectMode(String selectMode) {
    this.selectMode = selectMode;
  }

  public String getDefaultType() {
    return defaultType;
  }

  public void setDefaultType(String defaultType) {
    this.defaultType = defaultType;
  }
}
