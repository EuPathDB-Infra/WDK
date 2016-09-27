package org.gusdb.wdk.model.fix;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.db.SqlUtils;
import org.gusdb.fgputil.db.platform.DBPlatform;
import org.gusdb.fgputil.db.pool.DatabaseInstance;
import org.gusdb.wdk.model.Utilities;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.config.ModelConfigUserDB;
import org.json.JSONException;
import org.json.JSONObject;

public class OrganismUpdater {

  private static final String PARAM_ORGANISM[] = { "organism", "BlastDatabaseOrganism", "motif_organism",
      "text_search_organism, organismSinglePick" };
  private static final int lenParamOrg = PARAM_ORGANISM.length;
  private static final Logger logger = Logger.getLogger(OrganismUpdater.class);

  public static void main(String[] args) {

    // the format of the mapping file is:
    // old_name=new_name
    // one for each line
    if (args.length != 2) {
      System.err.println("Usage: organismUpdater <project_id> <map_file>\nPlease enter one project at a time.");
      System.exit(1);
    }

    try {
      OrganismUpdater updater = new OrganismUpdater(args[0], args[1]);
      updater.update();
      System.exit(0);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private final String projectId;
  private final WdkModel wdkModel;
  private final String userSchema;
  private final Map<String, String> mappings;
  private final UpdatedStepLogger stepLogger;

  public OrganismUpdater(String projectId, String mapFile) throws WdkModelException, IOException,
      SQLException {
    this.projectId = projectId;
    String gusHome = System.getProperty(Utilities.SYSTEM_PROPERTY_GUS_HOME);
    wdkModel = WdkModel.construct(projectId, gusHome);
    ModelConfigUserDB userDB = wdkModel.getModelConfig().getUserDB();
    userSchema = userDB.getUserSchema();
    mappings = loadMapFile(mapFile);
    stepLogger = new UpdatedStepLogger(wdkModel);
    logger.debug("\n" + mappings + "\n\n\n");
  }

  private Map<String, String> loadMapFile(String fileName) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(new File(fileName)));
    Map<String, String> mappings = new HashMap<String, String>();
    String line;
    while ((line = reader.readLine()) != null) {
      line = line.trim();
      if (line.length() == 0)
        continue;
      String[] parts = line.split("=", 2);
      mappings.put(parts[0].trim(), parts[1].trim());
    }
    reader.close();
    return mappings;
  }

  public void update() throws SQLException, JSONException {
    Set<String> clobKeys = new HashSet<String>();
    updateStepParams(clobKeys);
  }

  private void updateStepParams(Set<String> clobKeys) throws SQLException, JSONException {
    logger.info("Checking step params...");

    DatabaseInstance database = wdkModel.getUserDb();
    DBPlatform platform = database.getPlatform();
    DataSource dataSource = database.getDataSource();
    PreparedStatement psSelect = null, psUpdate = null;
    ResultSet resultSet = null;
    String select = "SELECT s.step_id, s.display_params            " + " FROM " + userSchema + "users u, " +
        userSchema + "steps s" + " WHERE u.is_guest = 0 AND u.user_id = s.user_id " +
        "   AND s.project_id = ?";
    String update = "UPDATE " + userSchema + "steps " + " SET display_params = ? WHERE step_id = ?";
    try {
      psSelect = SqlUtils.getPreparedStatement(dataSource, select);
      psUpdate = SqlUtils.getPreparedStatement(dataSource, update);
      psSelect.setString(1, projectId);
      resultSet = psSelect.executeQuery();
      int count = 0;
      int stepCount = 0;
      while (resultSet.next()) {
        stepCount++;
        if (stepCount % 1000 == 0) {
          logger.debug(stepCount + " steps read");
        }

        int stepId = resultSet.getInt("step_id");
        String content = platform.getClobData(resultSet, "display_params");
        if (content == null || content.trim().length() == 0)
          continue;
        if (content.replaceAll("\\s", "").equals("{}"))
          continue;

        JSONObject jsParams = new JSONObject(content);
        if (jsParams.has("params")) 
          jsParams = jsParams.getJSONObject("params");

        if (jsParams!=null && changeParams(jsParams, clobKeys)) {
          content = jsParams.toString();
          platform.setClobData(psUpdate, 1, content, false);
          psUpdate.setInt(2, stepId);
          psUpdate.addBatch();
          stepLogger.logStep(stepId);
          count++;
          if (count % 100 == 0)
            psUpdate.executeBatch();
        }
      }
      if (count % 100 != 0)
        psUpdate.executeBatch();
      stepLogger.finish();
      logger.info("THE END:   " + count + " steps modified\n\n");
    }
    catch (SQLException ex) {
      logger.error(ex);
      throw ex;
    }
    finally {
      SqlUtils.closeResultSetAndStatement(resultSet, psSelect);
      SqlUtils.closeStatement(psUpdate);
    }
  }

  private boolean changeParams(JSONObject jsParams, Set<String> clobKeys) throws JSONException {
    boolean updated = false;
    String[] names = JSONObject.getNames(jsParams);
    if ( names == null) return updated;

    for (String name : names) {
      for (int i = 0; i < lenParamOrg; i++) {
        if (name.equals(PARAM_ORGANISM[i])) {
          String organisms = jsParams.getString(name);
          StringBuilder buffer = new StringBuilder();
          for (String organism : organisms.split("\\s*,\\s*")) {
            // logger.debug("Organism found: --" + organism + "\n\n");
            if (mappings.containsKey(organism)) {
              logger.debug("FOUND param organism uncompressed with value that needs update...");
              organism = mappings.get(organism);
              updated = true;
            }
            if (buffer.length() > 0)
              buffer.append(',');
            buffer.append(organism);
          }
          jsParams.put(name, buffer.toString());
        }
      }
    }
    return updated;
  }
}
