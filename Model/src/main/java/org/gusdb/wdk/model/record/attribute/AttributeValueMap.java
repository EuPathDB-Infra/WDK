package org.gusdb.wdk.model.record.attribute;

import java.util.Map;

import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;

/**
 * An interface for storing and accessing {@link AttributeValue}s.
 * 
 * @author jerric
 * 
 */
public interface AttributeValueMap extends Map<String, AttributeValue>{

  public void addAttributeValue(AttributeValue value);

  public AttributeValue getAttributeValue(String key) throws WdkModelException, WdkUserException;

}
