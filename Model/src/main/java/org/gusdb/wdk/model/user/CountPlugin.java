package org.gusdb.wdk.model.user;

import org.gusdb.fgputil.validation.ValidObjectFactory.Runnable;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;

public interface CountPlugin {
  
  void setModel(WdkModel wdkModel);

  int count(Runnable<Step> step) throws WdkModelException, WdkUserException;
}
