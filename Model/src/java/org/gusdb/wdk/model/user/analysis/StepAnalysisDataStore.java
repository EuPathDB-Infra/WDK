package org.gusdb.wdk.model.user.analysis;

import static org.gusdb.fgputil.FormatUtil.NL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;

public abstract class StepAnalysisDataStore {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = Logger.getLogger(StepAnalysisDataStore.class);
  
  protected static class AnalysisInfo {
    
    int analysisId;
    int stepId;
    String displayName;
    boolean isNew;
    boolean hasParams;
    String invalidStepReason;
    String contextHash;
    String serializedContext;
    
    public AnalysisInfo(int analysisId, int stepId, String displayName, boolean isNew,
        boolean hasParams, String invalidStepReason, String contextHash, String serializedContext) {
      this.analysisId = analysisId;
      this.stepId = stepId;
      this.displayName = displayName;
      this.isNew = isNew;
      this.hasParams = hasParams;
      this.invalidStepReason = invalidStepReason;
      this.contextHash = contextHash;
      this.serializedContext = serializedContext;
    }
    
    @Override
    public String toString() {
      return new StringBuilder("AnalysisInfo {").append(NL)
          .append("analysisId: ").append(analysisId).append(NL)
          .append("stepId: ").append(stepId).append(NL)
          .append("displayName: ").append(displayName).append(NL)
          .append("isNew: ").append(isNew).append(NL)
          .append("hasParams: ").append(hasParams).append(NL)
          .append("invalidStepReason: ").append(invalidStepReason).append(NL)
          .append("contextHash: ").append(contextHash).append(NL)
          .append("serializedContext: ").append(serializedContext).append(NL)
          .append("}").append(NL).toString();
    }
  }

  protected static class AnalysisInfoPlusStatus {
    AnalysisInfo analysisInfo;
    ExecutionStatus status;
    public AnalysisInfoPlusStatus(AnalysisInfo info) {
      analysisInfo = info;
    }
  }
  
  // abstract methods to manage analysis information
  public abstract void createAnalysisTableAndSequence() throws WdkModelException;
  public abstract int getNextId() throws WdkModelException;
  public abstract void insertAnalysis(int analysisId, int stepId, String displayName, boolean isNew, boolean hasParams, String invalidStepReason, String contextHash, String serializedContext) throws WdkModelException;
  public abstract void deleteAnalysis(int analysisId) throws WdkModelException;
  public abstract void renameAnalysis(int analysisId, String displayName) throws WdkModelException;
  public abstract void setNewFlag(int analysisId, boolean isNew) throws WdkModelException;
  public abstract void setHasParams(int analysisId, boolean hasParams) throws WdkModelException;
  public abstract void updateContext(int analysisId, String contextHash, String serializedContext) throws WdkModelException;
  protected abstract List<Integer> getAnalysisIdsByStepId(int stepId) throws WdkModelException;
  protected abstract List<Integer> getAllAnalysisIds() throws WdkModelException;
  // contract is: analysisInfo will be null if ID does not exist; status will be null if execution does not exist
  protected abstract Map<Integer, AnalysisInfoPlusStatus> getAnalysisInfoForIds(List<Integer> analysisIds) throws WdkModelException;
  
  // abstract methods to manage result/status information
  public abstract void createExecutionTable() throws WdkModelException;
  public abstract void deleteExecutionTable(boolean purge) throws WdkModelException;
  public abstract boolean insertExecution(String contextHash, ExecutionStatus status, Date startDate) throws WdkModelException;  
  public abstract void updateExecution(String contextHash, ExecutionStatus status, Date updateDate, String charData, byte[] binData) throws WdkModelException;
  public abstract void resetStartDate(String contextHash, Date startDate) throws WdkModelException;
  public abstract void deleteExecution(String contextHash) throws WdkModelException;
  public abstract void deleteAllExecutions() throws WdkModelException;
  protected abstract ExecutionStatus getRawExecutionStatus(String contextHash) throws WdkModelException;
  public abstract AnalysisResult getRawAnalysisResult(String contextHash) throws WdkModelException;
  public abstract String getAnalysisLog(String contextHash) throws WdkModelException;
  public abstract void setAnalysisLog(String contextHash, String str) throws WdkModelException;

  private final WdkModel _wdkModel;
  
  // constructor requires WdkModel
  protected StepAnalysisDataStore(WdkModel wdkModel) {
    _wdkModel = wdkModel;
  }

  protected WdkModel getWdkModel() {
    return _wdkModel;
  }
  
  // helper methods that contain only business logic
  public void resetExecution(String contextHash, ExecutionStatus status) throws WdkModelException {
    Date newStartDate = new Date();
    resetStartDate(contextHash, newStartDate);
    updateExecution(contextHash, status, newStartDate, null, null);
  }
  
  public StepAnalysisContext getAnalysisById(int analysisId, StepAnalysisFileStore fileStore) throws WdkModelException {
    Map<Integer, AnalysisInfoPlusStatus> rawValues = getAnalysisInfoForIds(Arrays.asList(new Integer[]{ analysisId }));
    if (rawValues.get(analysisId).analysisInfo == null) throw new WdkModelException("Did not find exactly" +
        " one record for analysis ID " + analysisId + "; found " + rawValues.size());
    return getContexts(rawValues, fileStore).iterator().next();
  }
  
  public Map<Integer,StepAnalysisContext> getAnalysesByStepId(int stepId,
      StepAnalysisFileStore fileStore) throws WdkModelException {
    List<StepAnalysisContext> values = getContexts(
        getAnalysisInfoForIds(getAnalysisIdsByStepId(stepId)), fileStore);
    Map<Integer,StepAnalysisContext> contextMap = new LinkedHashMap<>();
    for (StepAnalysisContext context : values) {
      contextMap.put(context.getAnalysisId(), context);
    }
    return contextMap;
  }
  
  public List<StepAnalysisContext> getAllAnalyses(StepAnalysisFileStore fileStore)
      throws WdkModelException {
    return getContexts(getAnalysisInfoForIds(getAllAnalysisIds()), fileStore);
  }
  
  private List<StepAnalysisContext> getContexts(Map<Integer, AnalysisInfoPlusStatus> dataMap, 
      StepAnalysisFileStore fileStore) throws WdkModelException {
    List<StepAnalysisContext> contextList = new ArrayList<StepAnalysisContext>();
    for (Entry<Integer, AnalysisInfoPlusStatus> entry : dataMap.entrySet()) {
      if (entry.getValue().analysisInfo == null) {
        throw new WdkModelException("Unable to find record for analysis ID: " + entry.getKey());
      }
      contextList.add(convertToContext(entry.getValue(), fileStore));
    }
    return contextList;
  }
  
  private StepAnalysisContext convertToContext(AnalysisInfoPlusStatus data,
      StepAnalysisFileStore fileStore) throws WdkModelException {
    
    AnalysisInfo info = data.analysisInfo;
    StepAnalysisContext context = StepAnalysisContext.createFromStoredData(
        _wdkModel, info.analysisId, info.isNew, info.hasParams, info.invalidStepReason,
        info.displayName, info.serializedContext);
    
    // if analysis was created from a step its analyzer did not approve, then status is always INVALID
    if (!context.getIsValidStep()) {
      context.setStatus(ExecutionStatus.INVALID);
      return context;
    }
    
    // if analysis is new (has never been run), don't care about cache; just set as CREATED and return
    if (info.isNew) {
      context.setStatus(ExecutionStatus.CREATED);
      return context;
    }

    // check status of our data caches
    boolean dataCacheIntact = (data.status != null);
    boolean fileCacheIntact = fileStore.storageDirExists(info.contextHash);

    // if both are intact, then return status of execution as is
    if (dataCacheIntact && fileCacheIntact) {
      context.setStatus(data.status);
      return context;
    }
    
    // if got here then the analysis has been run but one or both caches are bad
    context.setStatus(ExecutionStatus.OUT_OF_DATE);
    return context;
  }
}
