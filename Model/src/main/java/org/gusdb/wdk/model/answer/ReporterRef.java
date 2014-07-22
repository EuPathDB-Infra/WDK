/**
 * 
 */
package org.gusdb.wdk.model.answer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelBase;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkModelText;

/**
 * A reference to the download Reporter class. the full class name of the
 * reporter is set into implementation, and an instance of the reporter will be
 * created to generate download results.
 * 
 * @author xingao
 * 
 */
public class ReporterRef extends WdkModelBase {

  private static final Logger logger = Logger.getLogger(ReporterRef.class);

  private String name;
  private String displayName;
  private String implementation;
  private boolean inReportMaker = true;
  private List<WdkModelText> propertyList = new ArrayList<>();
  private Map<String, String> properties = new LinkedHashMap<>();

  private WdkModel wdkModel;

  @Override
  public WdkModel getWdkModel() {
    return wdkModel;
  }

  /**
   * @return the implementation
   */
  public String getImplementation() {
    return implementation;
  }

  /**
   * @param implementation
   *          the implementation to set
   */
  public void setImplementation(String implementation) {
    this.implementation = implementation;
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
   * @return the displayName
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * @param displayName
   *          the displayName to set
   */
  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  /**
   * @return the inReportMaker
   */
  public boolean isInReportMaker() {
    return inReportMaker;
  }

  /**
   * @param inReportMaker
   *          the inReportMaker to set
   */
  public void setInReportMaker(boolean inReportMaker) {
    this.inReportMaker = inReportMaker;
  }

  public void addProperty(WdkModelText property) {
    this.propertyList.add(property);
  }

  public Map<String, String> getProperties() {
    return new LinkedHashMap<String, String>(this.properties);
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.gusdb.wdk.model.WdkModelBase#excludeResources(java.lang.String)
   */
  @Override
  public void excludeResources(String projectId) throws WdkModelException {
    // exclude properties
    for (WdkModelText property : propertyList) {
      if (property.include(projectId)) {
        property.excludeResources(projectId);
        String propName = property.getName();
        String propValue = property.getText();
        if (properties.containsKey(propName))
          throw new WdkModelException("The property " + propName
              + " is duplicated in reporter " + name);
        properties.put(propName, propValue);
        logger.trace("reporter property: [" + propName + "]='" + propValue
            + "'");
      }
    }
    propertyList = null;
  }

  public void setResources(WdkModel wdkModel) {
    this.wdkModel = wdkModel;
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
}
