package org.gusdb.wdk.model.user.dataset;


import java.nio.file.Path;
import java.util.Map;

import org.gusdb.wdk.model.WdkModelException;


/**
 * Provides access to collections of user datasets.  Only provides information about
 * one user at a time.
 * @author steve
 *
 */
public interface UserDatasetStore {

  /**
   * Called at start up by the WDK.  The configuration comes from
   * properties in model XML.
   * @param configuration
   */
  void initialize(Map<String, String> configuration, Map<UserDatasetType, UserDatasetTypeHandler> typeHandlers) throws WdkModelException;
  
  UserDatasetSession getSession(Path usersRootDir);
  
  Path getUsersRootDir();
  
  /**
   * Return the type handler registered for the specified type.
   * @param type
   * @return null if not found.
   */
  UserDatasetTypeHandler getTypeHandler(UserDatasetType type);
  
  String getId();

}