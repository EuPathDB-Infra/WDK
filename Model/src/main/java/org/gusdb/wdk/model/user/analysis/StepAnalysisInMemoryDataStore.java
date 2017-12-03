package org.gusdb.wdk.model.user.analysis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.IoUtil;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;

public class StepAnalysisInMemoryDataStore extends StepAnalysisDataStore {

  private static final Logger LOG = Logger.getLogger(StepAnalysisInMemoryDataStore.class);
  
  /**
   * Eventual table will have:
   *   analysisId(PK), stepId, displayName, isNew, contextHash, context CLOB
   */
  // will map stepId -> List<analysisId>
  private static Map<Long, List<Long>> STEP_ANALYSIS_MAP = new HashMap<>();
  // will map analysisId -> AnalysisInfo
  private static Map<Long, AnalysisInfo> ANALYSIS_INFO_MAP = new HashMap<>();
  // will map analysisId -> String (emulated properties CLOB)
  private static Map<Long, String> ANALYSIS_PROPERTIES_MAP = new HashMap<>();

  /**
   * Eventual table will have:
   *   contextHash(PK), status, log CLOB, data CLOB, data BLOB
   */
  // will map contextHash -> AnalysisResult
  private static Map<String, AnalysisResult> RESULT_INFO_MAP = new LinkedHashMap<>();

  private static AtomicInteger ID_SEQUENCE = new AtomicInteger(0);
  
  public StepAnalysisInMemoryDataStore(WdkModel wdkModel) {
    super(wdkModel);
  }

  @Override
  public long getNextId() throws WdkModelException {
    return ID_SEQUENCE.incrementAndGet();
  }

  @Override
  public void insertAnalysis(long analysisId, long stepId, String displayName, StepAnalysisState state,
      boolean hasParams, String invalidStepReason, String contextHash, String serializedContext)
          throws WdkModelException {
    synchronized(ANALYSIS_INFO_MAP) {
      if (!STEP_ANALYSIS_MAP.containsKey(stepId)) {
        STEP_ANALYSIS_MAP.put(stepId, new ArrayList<Long>());
      }
      STEP_ANALYSIS_MAP.get(stepId).add(analysisId);
      AnalysisInfo info = new AnalysisInfo(analysisId, stepId, displayName,
          state, hasParams, invalidStepReason, contextHash, serializedContext);
      ANALYSIS_INFO_MAP.put(analysisId, info);
      LOG.info("Inserted analysis with ID " + analysisId + " on step " + stepId +
          "; now " + STEP_ANALYSIS_MAP.get(stepId).size() + " analyses for this step.");
    }
  }
  
  @Override
  public void deleteAnalysis(long analysisId) throws WdkModelException {
    synchronized(ANALYSIS_INFO_MAP) {
      if (!ANALYSIS_INFO_MAP.containsKey(analysisId)) {
        LOG.info("Unable to find value for analysis ID " + analysisId);
        throw new WdkModelException("Analysis ID to be deleted [ " + analysisId + " ] does not exist.");
      }
      long stepId = ANALYSIS_INFO_MAP.get(analysisId).stepId;
      ANALYSIS_INFO_MAP.remove(analysisId);
      ANALYSIS_PROPERTIES_MAP.remove(analysisId);
      
      // remove reference to this analysis in step map
      List<Long> idsForStep = STEP_ANALYSIS_MAP.get(stepId);
      idsForStep.remove(analysisId);
      
      // remove record for step if no analyses remain
      if (idsForStep.isEmpty()) {
        STEP_ANALYSIS_MAP.remove(stepId);
      }
      
      LOG.info("End of call to delete analysis with ID " + analysisId);
      LOG.info(FormatUtil.prettyPrint(STEP_ANALYSIS_MAP));
      LOG.info(FormatUtil.prettyPrint(ANALYSIS_INFO_MAP));
    }
  }

  @Override
  public void renameAnalysis(long analysisId, String displayName) throws WdkModelException {
    synchronized(ANALYSIS_INFO_MAP) {
      if (ANALYSIS_INFO_MAP.containsKey(analysisId)) {
        ANALYSIS_INFO_MAP.get(analysisId).displayName = displayName;
        return;
      }
      throw new WdkModelException("No analysis exists with id: " + analysisId);
    }
  }

  @Override
  public void setState(long analysisId, StepAnalysisState state) throws WdkModelException {
    synchronized(ANALYSIS_INFO_MAP) {
      if (ANALYSIS_INFO_MAP.containsKey(analysisId)) {
        ANALYSIS_INFO_MAP.get(analysisId).state = state;
        return;
      }
      throw new WdkModelException("No analysis exists with id: " + analysisId);
    }
  }

  @Override
  public void setHasParams(long analysisId, boolean hasParams) throws WdkModelException {
    synchronized(ANALYSIS_INFO_MAP) {
      if (ANALYSIS_INFO_MAP.containsKey(analysisId)) {
        ANALYSIS_INFO_MAP.get(analysisId).hasParams = hasParams;
        return;
      }
      throw new WdkModelException("No analysis exists with id: " + analysisId);
    }
  }

  @Override
  public void setInvalidStepReason(long analysisId, String invalidStepReason) throws WdkModelException {
    synchronized(ANALYSIS_INFO_MAP) {
      if (ANALYSIS_INFO_MAP.containsKey(analysisId)) {
        ANALYSIS_INFO_MAP.get(analysisId).invalidStepReason = invalidStepReason;
        return;
      }
      throw new WdkModelException("No analysis exists with id: " + analysisId);
    }
  }

  @Override
  public void updateContext(long analysisId, String contextHash, String serializedContext) throws WdkModelException {
    synchronized(ANALYSIS_INFO_MAP) {
      if (ANALYSIS_INFO_MAP.containsKey(analysisId)) {
        ANALYSIS_INFO_MAP.get(analysisId).contextHash = contextHash;
        ANALYSIS_INFO_MAP.get(analysisId).serializedContext = serializedContext;
        return;
      }
      throw new WdkModelException("No analysis exists with id: " + analysisId);
    }
  }

  @Override
  protected List<Long> getAnalysisIdsByHash(String contextHash) throws WdkModelException {
    List<Long> idList = new ArrayList<>();
    if (contextHash == null) return idList;
    synchronized(ANALYSIS_INFO_MAP) {
      // inefficient search but ok since past original testing phase
      for (AnalysisInfo analysis : ANALYSIS_INFO_MAP.values()) {
        if (contextHash.equals(analysis.contextHash)) {
          idList.add(analysis.analysisId);
        }
      }
      return idList;
    }
  }

  @Override
  protected List<Long> getAnalysisIdsByStepId(long stepId) throws WdkModelException {
    synchronized(ANALYSIS_INFO_MAP) {
      if (STEP_ANALYSIS_MAP.containsKey(stepId)) {
        return STEP_ANALYSIS_MAP.get(stepId);
      }
      return new ArrayList<Long>();
    }
  }

  @Override
  protected List<Long> getAllAnalysisIds() throws WdkModelException {
    synchronized(ANALYSIS_INFO_MAP) {
      return new ArrayList<Long>(ANALYSIS_INFO_MAP.keySet());
    }
  }

  @Override
  protected Map<Long, AnalysisInfoPlusStatus> getAnalysisInfoForIds(List<Long> analysisIds)
      throws WdkModelException {
    synchronized(ANALYSIS_INFO_MAP) {
      synchronized(RESULT_INFO_MAP) {
        Map<Long, AnalysisInfoPlusStatus> map = new LinkedHashMap<>();
        for (Long analysisId : analysisIds) {
          AnalysisInfoPlusStatus aips = new AnalysisInfoPlusStatus(ANALYSIS_INFO_MAP.get(analysisId));
          if (aips.analysisInfo != null && RESULT_INFO_MAP.containsKey(aips.analysisInfo.contextHash)) {
            aips.status = RESULT_INFO_MAP.get(aips.analysisInfo.contextHash).getStatus();
          }
          map.put(analysisId, aips);
        }
        return map;
      }
    }
  }

  @Override
  public boolean insertExecution(String contextHash, ExecutionStatus initialStatus, Date startDate)
      throws WdkModelException {
    synchronized(RESULT_INFO_MAP) {
      if (RESULT_INFO_MAP.containsKey(contextHash)) {
        return false;
      }
      RESULT_INFO_MAP.put(contextHash, new AnalysisResult(initialStatus, startDate, startDate, null, null, null));
      return true;
    }
  }

  @Override
  public void updateExecution(String contextHash, ExecutionStatus status, Date updateDate, String charData, byte[] binData) throws WdkModelException {
    synchronized(RESULT_INFO_MAP) {
      if (RESULT_INFO_MAP.containsKey(contextHash)) {
        AnalysisResult info = RESULT_INFO_MAP.get(contextHash);
        info.setStatus(status);
        info.setUpdateDate(updateDate);
        info.setStoredString(charData);
        info.setStoredBytes(binData);
        LOG.info("Updated result record for hash[" + contextHash + "], status=" + status + ", charData =\n" + charData);
        return;
      }
      throw new WdkModelException("Step Analysis Execution for hash [" + contextHash + "] does not exist.");
    }
  }

  @Override
  public void resetStartDate(String contextHash, Date startDate) throws WdkModelException {
    synchronized(RESULT_INFO_MAP) {
      if (RESULT_INFO_MAP.containsKey(contextHash)) {
        AnalysisResult info = RESULT_INFO_MAP.get(contextHash);
        info.setStartDate(startDate);
        return;
      }
      throw new WdkModelException("Step Analysis Execution for hash [" + contextHash + "] does not exist.");
    }
  }

  @Override
  public void deleteExecution(String hash) throws WdkModelException {
    synchronized(RESULT_INFO_MAP) {
      RESULT_INFO_MAP.remove(hash);
    }
  }

  @Override
  public void deleteAllExecutions() throws WdkModelException {
    synchronized(RESULT_INFO_MAP) {
      RESULT_INFO_MAP.clear();
    }
  }

  @Override
  protected ExecutionStatus getRawExecutionStatus(String contextHash) throws WdkModelException {
    AnalysisResult result = getRawAnalysisResult(contextHash);
    return (result == null ? null : result.getStatus());
  }

  @Override
  public AnalysisResult getRawAnalysisResult(String contextHash) throws WdkModelException {
    synchronized(RESULT_INFO_MAP) {
      if (RESULT_INFO_MAP.containsKey(contextHash)) {
        return RESULT_INFO_MAP.get(contextHash);
      }
      return null;
    }
  }

  @Override
  public List<ExecutionInfo> getAllRunningExecutions() {
    synchronized(RESULT_INFO_MAP) {
      List<ExecutionInfo> results = new ArrayList<>();
      for (Entry<String, AnalysisResult> entry : RESULT_INFO_MAP.entrySet()) {
        AnalysisResult result = entry.getValue();
        if (result.getStatus().equals(ExecutionStatus.PENDING) ||
            result.getStatus().equals(ExecutionStatus.RUNNING)) {
          results.add(new ExecutionInfo(entry.getKey(), result.getStatus(),
              result.getStartDate(), result.getUpdateDate()));
        }
      }
      return results;
    }
  }

  @Override
  public String getAnalysisLog(String contextHash) throws WdkModelException {
    synchronized(RESULT_INFO_MAP) {
      if (RESULT_INFO_MAP.containsKey(contextHash)) {
        AnalysisResult result = RESULT_INFO_MAP.get(contextHash);
        return result.getStatusLog();
      }
      throw new WdkModelException("No analysis execution with context hash value: " + contextHash);
    }
  }

  @Override
  public void setAnalysisLog(String contextHash, String str) throws WdkModelException {
    synchronized(RESULT_INFO_MAP) {
      if (RESULT_INFO_MAP.containsKey(contextHash)) {
        RESULT_INFO_MAP.get(contextHash).setStatusLog(str);
        return;
      }
      throw new WdkModelException("No analysis execution with context hash value: " + contextHash);
    }
  }

  @Override
  public void createAnalysisTableAndSequence() throws WdkModelException {
    // no need to create; they are always here
  }

  @Override
  public void createExecutionTable() throws WdkModelException {
    // no need to create; it is always here
  }

  @Override
  public void deleteExecutionTable(boolean purge) throws WdkModelException {
    // just clear the execution table for in memory data store
    deleteAllExecutions();
  }

  @Override
  public InputStream getProperties(long analysisId) throws WdkModelException {
    synchronized(ANALYSIS_INFO_MAP) {
      if (!ANALYSIS_INFO_MAP.containsKey(analysisId)) {
        return null;
      }
      String props = ANALYSIS_PROPERTIES_MAP.get(analysisId);
      if (props == null) props = "";
      return new ByteArrayInputStream(props.getBytes());
    }
  }

  @Override
  public boolean setProperties(long analysisId, InputStream propertiesStream) throws WdkModelException {
    synchronized(ANALYSIS_INFO_MAP) {
      if (!ANALYSIS_INFO_MAP.containsKey(analysisId)) {
        return false;
      }
      try {
        ANALYSIS_PROPERTIES_MAP.put(analysisId, IoUtil.readAllChars(new InputStreamReader(propertiesStream)));
        return true;
      }
      catch (IOException e) {
        throw new WdkModelException("Unable to read whole properties stream", e);
      }
    }
  }
}
