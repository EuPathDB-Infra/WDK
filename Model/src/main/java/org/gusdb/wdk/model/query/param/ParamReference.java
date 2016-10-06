package org.gusdb.wdk.model.query.param;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.Group;
import org.gusdb.wdk.model.Reference;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkModelText;
import org.gusdb.wdk.model.query.Query;

/**
 * An object representation of a <paramRef> tag, It is used in query and
 * question tag to reference a param, and provide customization to the param.
 * 
 * @author Jerric
 * @created Feb 16, 2006
 */
public class ParamReference extends Reference {

  private static final Logger LOG = Logger.getLogger(ParamReference.class);

  public static Param resolveReference(WdkModel wdkModel,
      ParamReference paramRef, Query contextQuery) throws WdkModelException {

    String twoPartName = paramRef.getTwoPartName();
    Param param;
    try {
      param = (Param) wdkModel.resolveReference(twoPartName);
    }
    catch (WdkModelException e) {
      throw new WdkModelException("Unable to resolve param reference '" + twoPartName +
          "' referred to by context query '" + contextQuery.getFullName() + "'.", e);
    }
    // clone the param to have different default values
    param = param.clone();
    param.setContextQuery(contextQuery);

    // if the param has customized default value
    String defaultValue = paramRef.getDefault();
    if (defaultValue != null)
      param.setDefault(defaultValue);

    // if the param has customized allowEmpty
    Boolean allowEmpty = paramRef.isAllowEmpty();
    if (allowEmpty != null) {
      param.setAllowEmpty(allowEmpty);

      // if the param has customized allowEmpty
      String emptyValue = paramRef.getEmptyValue();
      if (emptyValue != null)
        param.setEmptyValue(emptyValue);
    }
    Boolean noTranslation = paramRef.getNoTranslation();
    if (noTranslation != null)
      param.setNoTranslation(noTranslation);

    // if the visible is set
    Boolean visible = paramRef.getVisible();
    if (visible != null)
      param.setVisible(visible);

    // set help if exists
    String help = paramRef.getHelp();
    if (help != null)
      param.setHelp(help);

		// set visibleHelp if exists
    String visibleHelp = paramRef.getVisibleHelp();
    if (visibleHelp != null)
      param.setVisibleHelp(visibleHelp);

    // set prompt if any
    String prompt = paramRef.getPrompt();
    if (prompt != null)
      param.setPrompt(prompt);

    Boolean number = paramRef.getNumber();

    Boolean quote = paramRef.getQuote();
    Boolean multiPick = paramRef.isMultiPick();
    String displayType = paramRef.getDisplayType();
    SelectMode selectMode = paramRef.getSelectModeEnum();
    String queryRef = paramRef.getQueryRef();
    Boolean suppressNode = paramRef.getSuppressNode();
    Integer minSelectedCount = paramRef.getMinSelectedCount();
    Integer maxSelectedCount = paramRef.getMaxSelectedCount();
    Boolean countOnlyLeaves = paramRef.getCountOnlyLeaves();
    Long interval = paramRef.getInterval();
    
    if (param instanceof AbstractEnumParam) {
      AbstractEnumParam enumParam = (AbstractEnumParam) param;
      // check those invalid properties
      if (number != null)
        throw new WdkModelException("The 'number' property is not "
            + "allowed in param '" + twoPartName + "'");

      if (interval != null)
        throw new WdkModelException("The 'interval' property is not "
            + "allowed in param '" + twoPartName + "'");

      // if the param has customized multi pick
      if (multiPick != null) {
        if (LOG.isDebugEnabled()) {
          if (!enumParam.getMultiPick() != multiPick) {
            LOG.debug("ParamRef to '" + enumParam.getFullName() +
                "' in context query '" + contextQuery.getFullName() +
                "' is overriding multi-pick: " + enumParam.getMultiPick() +
                " -> " + multiPick + ", displayType: " + enumParam.getDisplayType() +
                " -> " + (displayType == null ? "<inherited>" : displayType));
          }
        }
        enumParam.setMultiPick(multiPick);
      }

      // if the queryRef is set for FlatVocabParam
      if (queryRef != null) {
        if (param instanceof FlatVocabParam) {
          ((FlatVocabParam) param).setQueryRef(queryRef);
        } else
          throw new WdkModelException("The paramRef to '" + twoPartName
              + "' is not a flatVocabParam. The "
              + "'queryRef' property can only be applied to "
              + "paramRefs of flatVocabParams.");
      }

      // if quote is set, it overrides the value of the param
      if (quote != null)
        enumParam.setQuote(quote);

      // if displayType is set, overrides the value in param
      if (displayType != null)
        enumParam.setDisplayType(displayType);

      if (selectMode != null)
        enumParam.setSelectMode(selectMode);

      if (suppressNode != null)
        enumParam.setSuppressNode(suppressNode);
      
      if (minSelectedCount != null)
        enumParam.setMinSelectedCount(minSelectedCount);
      
      if (maxSelectedCount != null)
        enumParam.setMaxSelectedCount(maxSelectedCount);
      
      if (countOnlyLeaves != null)
        enumParam.setCountOnlyLeaves(countOnlyLeaves);

    } else { // or other param types
      if (multiPick != null || quote != null || displayType != null
          || selectMode != null || queryRef != null)
        throw new WdkModelException("The paramRef to '" + twoPartName
            + "' is not a flatVocabParam nor enumParam. The "
            + "'multiPick', 'displayType', 'quote',"
            + " 'selectMode', 'queryRef' properties can only be "
            + "applied to paramRefs of flatVocabParams or " + "enumParams.");
      if (param instanceof StringParam) {
        // if quote is set, it overrides the value of the param
        if (number != null)
          ((StringParam) param).setNumber(number);
      } else if (number != null) {
        throw new WdkModelException("The paramRef to '" + twoPartName
            + "' is not a stringParam. The 'number' property can "
            + "only be applied to paramRefs of stringParams.");
      }
      
      if (param instanceof TimestampParam) {
        if (interval != null)
          ((TimestampParam)param).setInterval(interval);
      } else if (interval != null) {
        throw new WdkModelException("The paramRef to '" + twoPartName
            + "' is not a timestampParam. The 'interval' property can "
            + "only be applied to paramRefs of timestampParam.");
      }
    }

    // resolve the group reference
    String groupRef = paramRef.getGroupRef();
    if (groupRef != null) {
      Group group = (Group) wdkModel.resolveReference(groupRef);
      param.setGroup(group);
    } else if (!param.isVisible()) {
      param.setGroup(Group.Hidden());
    }
    param.setResources(wdkModel);
    return param;
  }

  // set of overridable values
  private String defaultValue;
  private Boolean allowEmpty;
  private Boolean multiPick;
  private String queryRef;
  private Boolean quote;
  private Boolean number;
  private String emptyValue;
  private String displayType;
  private Boolean visible;
  private SelectMode selectMode;
  private Boolean noTranslation;
  private Boolean suppressNode;
  private Integer minSelectedCount;
  private Integer maxSelectedCount;
  private Boolean countOnlyLeaves;
  private String prompt;

  private List<WdkModelText> helps = new ArrayList<WdkModelText>();
  private String help;

	private List<WdkModelText> visibleHelps = new ArrayList<WdkModelText>();
  private String visibleHelp;

  // this property only applies to timestamp param.
  private Long interval;

  public ParamReference() {}

  /**
   * @param twoPartName
   */
  public ParamReference(String twoPartName) throws WdkModelException {
    super(twoPartName);
  }

  /**
   * @return Returns the defaultValue.
   */
  public String getDefault() {
    return this.defaultValue;
  }

  /**
   * @param defaultValue
   *          The defaultValue to set.
   */
  public void setDefault(String defaultValue) {
    this.defaultValue = defaultValue;
  }

  /**
   * @return the allowEmpty
   */
  public Boolean isAllowEmpty() {
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
   * @return the multiPick
   */
  public Boolean isMultiPick() {
    return this.multiPick;
  }

  /**
   * @param multiPick
   *          the multiPick to set
   */
  public void setMultiPick(boolean multiPick) {
    this.multiPick = multiPick;
  }

  /**
   * @return the queryRef
   */
  public String getQueryRef() {
    return this.queryRef;
  }

  /**
   * @param queryRef
   *          the queryRef to set
   */
  public void setQueryRef(String queryRef) {
    this.queryRef = queryRef;
  }

  /**
   * @return the quote
   */
  public Boolean getQuote() {
    return quote;
  }

  /**
   * @param quote
   *          the quote to set
   */
  public void setQuote(Boolean quote) {
    this.quote = quote;
  }

  public String getEmptyValue() {
    return emptyValue;
  }

  public void setEmptyValue(String emptyValue) {
    this.emptyValue = emptyValue;
  }

  /**
   * @return the displayType
   */
  public String getDisplayType() {
    return displayType;
  }

  /**
   * @param displayType
   *          the displayType to set
   */
  public void setDisplayType(String displayType) {
    this.displayType = displayType;
  }

  /**
   * @return the visible
   */
  public Boolean getVisible() {
    return visible;
  }

  /**
   * @param visible
   *          the visible to set
   */
  public void setVisible(Boolean visible) {
    this.visible = visible;
  }

  /**
   * @return the selectMode
   */
  public String getSelectMode() {
    return selectMode.name();
  }

  public SelectMode getSelectModeEnum() {
    return selectMode;
  }

  /**
   * @param selectMode
   *          the selectMode to set
   */
  public void setSelectMode(String selectMode) {
    this.selectMode = SelectMode.valueOf(selectMode.toUpperCase());
  }

  /**
   * @return the noTranslation
   */
  public Boolean getNoTranslation() {
    return noTranslation;
  }

  /**
   * @param noTranslation
   *          the noTranslation to set
   */
  public void setNoTranslation(Boolean noTranslation) {
    this.noTranslation = noTranslation;
  }

  /**
   * @return the number
   */
  public Boolean getNumber() {
    return number;
  }

  /**
   * @param number
   *          the number to set
   */
  public void setNumber(Boolean number) {
    this.number = number;
  }

  /**
   * @return the suppressNode
   */
  public Boolean getSuppressNode() {
    return suppressNode;
  }

  /**
   * @param suppressNode
   *          the suppressNode to set
   */
  public void setSuppressNode(Boolean suppressNode) {
    this.suppressNode = suppressNode;
  }

  public Boolean getMultiPick() {
    return multiPick;
  }

  public void setMultiPick(Boolean multiPick) {
    this.multiPick = multiPick;
  }

  public Integer getMinSelectedCount() {
    return minSelectedCount;
  }

  public void setMinSelectedCount(Integer minSelectedCount) {
    this.minSelectedCount = minSelectedCount;
  }

  public Integer getMaxSelectedCount() {
    return maxSelectedCount;
  }

  public void setMaxSelectedCount(Integer maxSelectedCount) {
    this.maxSelectedCount = maxSelectedCount;
  }

  public Boolean getCountOnlyLeaves() {
    return countOnlyLeaves;
  }

  public void setCountOnlyLeaves(Boolean countOnlyLeaves) {
    this.countOnlyLeaves = countOnlyLeaves;
  }

  public void addHelp(WdkModelText help) {
    this.helps.add(help);
  }

  public String getHelp() {
    return help;
  }

 public void addVisibleHelp(WdkModelText visibleHelp) {
    this.visibleHelps.add(visibleHelp);
  }

  public String getVisibleHelp() {
    return visibleHelp;
  }

  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  public String getPrompt() {
    return prompt;
  }
  
  /**
   * @return the interval
   */
  public Long getInterval() {
    return interval;
  }

  /**
   * @param interval the interval to set
   */
  public void setInterval(Long interval) {
    this.interval = interval;
  }

  @Override
  public void excludeResources(String projectId) throws WdkModelException {
    super.excludeResources(projectId);

    for (WdkModelText help : helps) {
      if (help.include(projectId)) {
        if (this.help != null)
          throw new WdkModelException("More than one <help> are "
              + "defined in the paramRef '" + this.getTwoPartName());

        help.excludeResources(projectId);
        this.help = help.getText();
      }
    }
    helps = null;

 for (WdkModelText visibleHelp : visibleHelps) {
      if (visibleHelp.include(projectId)) {
        if (this.visibleHelp != null)
          throw new WdkModelException("More than one <visibleHelp> are "
              + "defined in the paramRef '" + this.getTwoPartName());

        visibleHelp.excludeResources(projectId);
        this.visibleHelp = visibleHelp.getText();
      }
    }
    visibleHelps = null;
	 }
}
