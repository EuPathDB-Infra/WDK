package org.gusdb.wdk.model.user.dataset.event;

import java.io.File;
import java.io.FileInputStream;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.BaseCLI;
import org.json.JSONArray;

/**
 * A wrapper around the UserDatasetEventArrayHandler library to allow it to be
 * called via a command line interface.
 * @author crisl-adm
 *
 */
public class UserDatasetEventListHandler extends BaseCLI {

  protected static final String ARG_PROJECT = "project";
  protected static final String ARG_EVENTS_FILE = "eventsFile";

  private static final Logger logger = Logger.getLogger(UserDatasetEventListHandler.class);

  public UserDatasetEventListHandler(String command) {
    super(command, "Handle a list of user dataset events.");
  }

  public static void main(String[] args) {
    String cmdName = System.getProperty("cmdName");
    UserDatasetEventListHandler handler = new UserDatasetEventListHandler(cmdName);
    try {
      handler.invoke(args);
      logger.info("done.");
      System.exit(0);
    }
    catch (Exception ex) {
      ex.printStackTrace();
      System.exit(1);
    }
  }
  
  @Override
  protected void execute() throws Exception {
	String projectId = (String) getOptionValue(ARG_PROJECT);
    UserDatasetEventArrayHandler handler = new UserDatasetEventArrayHandler(projectId);
    File eventFile = new File((String)getOptionValue(ARG_EVENTS_FILE));
    JSONArray eventJsonArray = null;
    try (FileInputStream fileInputStream = new FileInputStream(eventFile)) {
      eventJsonArray = new JSONArray(fileInputStream.toString());
    }
    handler.handleEventList(UserDatasetEventArrayHandler.parseEventsArray(eventJsonArray),
    handler.getModelConfig().getUserDatasetStoreConfig().getTypeHandlers());
  }

  @Override
  protected void declareOptions() {
    addSingleValueOption(ARG_PROJECT, true, null, "The project of the app db");
    addSingleValueOption(ARG_EVENTS_FILE, true, null, "File containing an ordered JSON Array of user dataset events"); 
  }

}
