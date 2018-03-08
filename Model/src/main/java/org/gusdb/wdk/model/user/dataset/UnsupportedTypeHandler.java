package org.gusdb.wdk.model.user.dataset;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.gusdb.wdk.model.WdkModelException;

public class UnsupportedTypeHandler extends UserDatasetTypeHandler {
  public final static String NAME = "unsupported";
  public final static String VERSION = "1.0";

  @Override
  public UserDatasetCompatibility getCompatibility(UserDataset userDataset, DataSource appDbDataSource)
   throws WdkModelException {
    return new UserDatasetCompatibility(false, "This type of user dataset is no longer supported.");
  }

  @Override
  public UserDatasetType getUserDatasetType() {
    return UserDatasetTypeFactory.getUserDatasetType(NAME, VERSION);
  }

  @Override
  public String[] getInstallInAppDbCommand(UserDataset userDataset, Map<String, Path> fileNameToTempFileMap,
   String project) {
    String[] cmd = {};
    return cmd;
  }

  @Override
  public Set<String> getInstallInAppDbFileNames(UserDataset userDataset) {
    return new HashSet<String>();
  }

  @Override
  public String[] getUninstallInAppDbCommand(Long userDatasetId, String projectName) {
    String[] cmd = {};
    return cmd;
  }

  @Override
  public String[] getRelevantQuestionNames() {
	String[] q = {};
    return q;
  }

}
