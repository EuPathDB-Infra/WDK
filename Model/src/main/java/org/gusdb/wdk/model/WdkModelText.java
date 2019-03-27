package org.gusdb.wdk.model;

/**
 * This class is a super class for any classes that represents a tag in the WDK
 * model file, and that tag can have CDATA text in it.
 *
 * @author Jerric
 *
 */
public class WdkModelText extends WdkModelBase {

  protected String name;
  private String text;

  public WdkModelText() {
    // needed for XML parsing
  }

  public WdkModelText(String name, String text) {
    this.name = name;
    this.text = text;
  }

  /**
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * @param name
   *          the name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * @return the text
   */
  public String getText() {
    return this.text;
  }

  /**
   * @param text
   *          the text to set
   */
  public void setText(String text) {
    this.text = text;
  }

  /**
   * A temporary fix on model parsing. It sets the value into text, and should be deprecated/removed after
   * the model is unified to use setText() only.
   *
   * @param value
   *
   */
  public void setValue(String value) {
    this.text = value;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.gusdb.wdk.model.WdkModelBase#excludeResources(java.lang.String)
   */
  @Override
  public void excludeResources(String projectId) {
    // no resource held by it, do nothing
  }

  /*
   * (non-Javadoc)
   *
   * @see org.gusdb.wdk.model.WdkModelBase#resolveReferences(org.gusdb.wdk.model
   * .WdkModel)
   */
  @Override
  public void resolveReferences(WdkModel wodkModel) throws WdkModelException {
    // have nothing to do
  }
}
