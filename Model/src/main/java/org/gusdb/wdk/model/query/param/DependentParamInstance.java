package org.gusdb.wdk.model.query.param;

import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.query.param.values.ValidStableValuesFactory.ValidStableValues;
import org.gusdb.wdk.model.user.User;

public interface DependentParamInstance {

  String getValidStableValue(User user, ValidStableValues contextParamValues)
      throws WdkModelException, WdkUserException;

}
