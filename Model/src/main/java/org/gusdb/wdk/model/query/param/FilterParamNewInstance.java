package org.gusdb.wdk.model.query.param;

import java.util.Map;

import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.user.User;

/**
 * This class encapsulates a vocabulary and default value for an
 * AbstractEnumParam based on a set of context values.  It is generated by
 * subclasses of AbstractEnumParam but is used by EnumParamBean to hold
 * dependent param info for the life of a request (since it can be expensive
 * to generate).
 * 
 * History: When it was created, we were maintaining context state in
 * AbstractEnumParam itself, which violated the idea that Model components were
 * stateless.  Since a lot of information must be retrieved and stored in
 * EnumParamBean, this class was created to hold it all for a single context.
 * EnumParamBean will create a new cache if its context values change.
 * 
 * @author rdoherty
 */
public class FilterParamNewInstance implements DependentParamInstance {

  // param this cache was created by
  private FilterParamNew _param;

  // context values used to create this cache
  private Map<String, String> _dependedParamValues;

  // default value based on vocabulary and select mode (or maybe "hard-coded" (in XML) default)
  private String _defaultValue;

  public FilterParamNewInstance(Map<String, String> dependedParamValues, FilterParamNew param) {
    _dependedParamValues = dependedParamValues;
    _param = param;
  }

  @Override
  public String getValidStableValue(User user, String stableValue, Map<String, String> contextParamValues)
      throws WdkModelException, WdkUserException {
    // TODO phase 2 - use metadata and ontologyMap to correct stableValue
    Map<String, OntologyItem> ontologyMap = _param.getOntology(user, contextParamValues);
    if(stableValue == null) {
      return _param.getDefault();
    }
    return stableValue;
  }
	
}
