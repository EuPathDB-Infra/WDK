package org.gusdb.wdk.service.service.user;

import static org.gusdb.fgputil.functional.Functions.mapToList;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.log4j.Logger;
import org.gusdb.fgputil.FormatUtil;
import org.gusdb.fgputil.IoUtil;
import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.user.User;
import org.gusdb.wdk.model.user.UserFactory;
import org.gusdb.wdk.model.user.dataset.UserDataset;
import org.gusdb.wdk.model.user.dataset.UserDatasetFile;
import org.gusdb.wdk.model.user.dataset.UserDatasetInfo;
import org.gusdb.wdk.model.user.dataset.UserDatasetSession;
import org.gusdb.wdk.model.user.dataset.UserDatasetStore;
import org.gusdb.wdk.service.UserBundle;
import org.gusdb.wdk.service.annotation.PATCH;
import org.gusdb.wdk.service.formatter.UserDatasetFormatter;
import org.gusdb.wdk.service.request.exception.RequestMisformatException;
import org.gusdb.wdk.service.request.user.UserDatasetShareRequest;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *  TODO: validate
 *    - requested user exists
 *    - sharing:
 *      - target user exists
 *      - shared dataset exists
 *    - external datasets
 *      - original dataset exists, and is still shared  
 *
 */
public class UserDatasetService extends UserService {

  private static Logger LOG = Logger.getLogger(UserDatasetService.class);

  public UserDatasetService(@PathParam(USER_ID_PATH_PARAM) String uid) {
    super(uid);
  }

  @GET
  @Path("user-datasets")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getAllUserDatasets(@QueryParam("expandDetails") Boolean expandDatasets) throws WdkModelException {
    expandDatasets = getFlag(expandDatasets, false);
    User user = getUser(Access.PRIVATE);
    long userId = user.getUserId();
    UserFactory userFactory = getWdkModel().getUserFactory();
    UserDatasetStore dsStore = getUserDatasetStore();
    String responseJson = null;
    try (UserDatasetSession dsSession = dsStore.getSession(dsStore.getUsersRootDir())) {
      Set<Long> installedUserDatasets = getWdkModel().getUserDatasetFactory().getInstalledUserDatasets(userId);
      List<UserDatasetInfo> userDatasets = getDatasetInfo(dsSession.getUserDatasets(userId).values(),
          installedUserDatasets, dsStore, dsSession, userFactory, getWdkModel(), user);
      List<UserDatasetInfo> sharedDatasets = getDatasetInfo(dsSession.getExternalUserDatasets(userId).values(),
          installedUserDatasets, dsStore, dsSession, userFactory, getWdkModel(), user);
      responseJson = UserDatasetFormatter.getUserDatasetsJson(dsSession, userDatasets,
          sharedDatasets, expandDatasets).toString();
    }        
    return Response.ok(responseJson).build();
  }

  private List<UserDatasetInfo> getDatasetInfo(final Collection<UserDataset> datasets,
      final Set<Long> installedUserDatasets, final UserDatasetStore dsStore, final UserDatasetSession dsSession,
      final UserFactory userFactory, final WdkModel wdkModel, User user) throws WdkModelException {
    List<UserDatasetInfo> list = mapToList(datasets, dataset -> new UserDatasetInfo(dataset,
        installedUserDatasets.contains(dataset.getUserDatasetId()), dsStore, dsSession, userFactory, wdkModel));
    getWdkModel().getUserDatasetFactory().addTypeSpecificData(wdkModel, list, user);
    return list;
  }

  @GET
  @Path("user-datasets/{datasetId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getUserDataset(@PathParam("datasetId") String datasetIdStr) throws WdkModelException {
	User user = getUser(Access.PRIVATE); 
	long userId = user.getUserId();
	long datasetId = parseLongId(datasetIdStr, new NotFoundException("No dataset found with ID " + datasetIdStr));
    UserDatasetStore dsStore = getUserDatasetStore();
    String responseJson = null;
    try (UserDatasetSession dsSession = dsStore.getSession(dsStore.getUsersRootDir())) {
      UserDataset userDataset =
          dsSession.getUserDatasetExists(userId, datasetId) ?
          dsSession.getUserDataset(userId, datasetId) :
          dsSession.getExternalUserDatasets(userId).get(datasetId);
      if (userDataset == null) {
        throw new NotFoundException("user-dataset/" + datasetIdStr);
      }
      Set<Long> installedUserDatasets = getWdkModel().getUserDatasetFactory().getInstalledUserDatasets(userId);
      UserDatasetInfo dsInfo = new UserDatasetInfo(userDataset, installedUserDatasets.contains(datasetId),
        dsStore, dsSession, getWdkModel().getUserFactory(), getWdkModel());
      dsInfo.loadDetailedTypeSpecificData(user);
      responseJson = UserDatasetFormatter.getUserDatasetJson(dsSession, dsInfo,
          userDataset.getOwnerId().equals(userId), true).toString();
    }
    return Response.ok(responseJson).build();
  }
  
  /**
   * 
   * @param datasetIdStr
   * @param datafileName
   * @return
   * @throws WdkModelException
   * @throws WdkUserException
   */
  @GET
  @Path("user-datasets/{datasetId}/user-datafiles/{datafileName}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public Response getBinaryDatafile(@PathParam("datasetId") String datasetIdStr, @PathParam("datafileName") String datafileName) throws WdkModelException, WdkUserException {
	long userId = getUser(Access.PUBLIC).getUserId();
    long datasetId = parseLongId(datasetIdStr, new NotFoundException("No dataset found with ID " + datasetIdStr));
    UserDatasetStore dsStore = getUserDatasetStore();
    java.nio.file.Path temporaryDirPath = null;
    try (UserDatasetSession dsSession = dsStore.getSession(dsStore.getUsersRootDir())) {
    	  temporaryDirPath = IoUtil.createOpenPermsTempDir(Paths.get(getWdkModel().getModelConfig().getWdkTempDir()), "irods_");
      UserDataset userDataset =
              dsSession.getUserDatasetExists(userId, datasetId) ?
              dsSession.getUserDataset(userId, datasetId) :
              dsSession.getExternalUserDatasets(userId).get(datasetId);
      if (userDataset == null) {
        throw new NotFoundException("No user dataset is found with ID " + datasetId);
      }
      UserDatasetFile userDatasetFile = userDataset.getFile(dsSession, datafileName);
      if(userDatasetFile == null) throw new WdkModelException("There is no data file corresponding to the filename " + datafileName);
      InputStream inputStream = userDatasetFile.getFileContents(dsSession, temporaryDirPath);
    	  StreamingOutput output = new StreamingOutput() {
        @Override
        public void write(OutputStream out) throws IOException, WebApplicationException {  
          int length;
          byte[] buffer = new byte[1024];
          while((length = inputStream.read(buffer)) != -1) {
            out.write(buffer, 0, length);
          }
          out.flush();  
          inputStream.close();
        }
     };
     return Response.ok(output).build();
    }
    catch(IOException ioe) {
    	  throw new WdkModelException(ioe);
    }
    finally {
    	  if(temporaryDirPath != null) {
    	    java.nio.file.Path temporaryFilePath = temporaryDirPath.resolve(datafileName);
    	    try {
    		  Files.delete(temporaryFilePath);
    		  Files.delete(temporaryDirPath);
    	    }
    	    catch(IOException ioe) {
    	    	  throw new WdkModelException(ioe);
    	    }
    	  }
    }
  }

  @PUT
  @Path("user-datasets/{datasetId}/meta")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response updateMetaInfo(@PathParam("datasetId") String datasetIdStr, String body) throws WdkModelException {
	long userId = getUser(Access.PRIVATE).getUserId();
    long datasetId = parseLongId(datasetIdStr, new NotFoundException("No dataset found with ID " + datasetIdStr));
    UserDatasetStore dsStore = getUserDatasetStore();
    try (UserDatasetSession dsSession = dsStore.getSession(dsStore.getUsersRootDir())) {
      if (!dsSession.getUserDatasetExists(userId, datasetId)) throw new NotFoundException("user-dataset/" + datasetIdStr);
      JSONObject metaJson = new JSONObject(body);
      dsSession.updateMetaFromJson(userId, datasetId, metaJson);
      return Response.noContent().build();
    }
    catch (JSONException e) {
      throw new BadRequestException(e);
    }
  }

  /*
   * This service allows a WDK user to share/unshare owned datasets with
   * other WDK users.  The JSON object accepted by the service should have the following form:
   *    {
   *	  "add": {
   *	    "dataset_id1": [ "user1", "user2" ]
   *	    "dataset_id2": [ "user1" ]
   *	  },
   *	  "delete" {
   *	    "dataset_id3": [ "user1", "user3" ]
   *	  }
   *	}
   */	
  @PATCH
  @Path("user-datasets/sharing")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response manageShares(String body) throws WdkModelException {
	long userId = getUser(Access.PRIVATE).getUserId();
    JSONObject jsonObj = new JSONObject(body);
    UserDatasetStore dsStore = getUserDatasetStore();
    try (UserDatasetSession dsSession = dsStore.getSession(dsStore.getUsersRootDir())) {
      UserDatasetShareRequest request = UserDatasetShareRequest.createFromJson(jsonObj);
      Map<String, Map<Long, Set<Long>>> userDatasetShareMap = request.getUserDatasetShareMap();
      Set<Long> installedDatasetIds = getWdkModel().getUserDatasetFactory().getInstalledUserDatasets(userId);
      for (String key : userDatasetShareMap.keySet()) {
        // Find datasets to share  
        if ("add".equals(key)) {
          Set<Long> targetDatasetIds = userDatasetShareMap.get(key).keySet();
          // Ignore any provided dataset ids not owned by this user.
          targetDatasetIds.retainAll(installedDatasetIds);
          for (Long targetDatasetId : targetDatasetIds) {
            Set<Long> targetUserIds = identifyTargetUsers(userDatasetShareMap.get(key).get(targetDatasetId));  
            // Since each dataset may be shared with different users, we share datasets one by one
            dsSession.shareUserDataset(userId, targetDatasetId, targetUserIds);
          }
        }
        // Fine datasets to unshare
        if ("delete".equals(key)) {
          Set<Long> targetDatasetIds = userDatasetShareMap.get(key).keySet();
          // Ignore any provided dataset ids not owned by this user.
          targetDatasetIds.retainAll(installedDatasetIds);
          for (Long targetDatasetId : targetDatasetIds) {
            Set<Long> targetUserIds = identifyTargetUsers(userDatasetShareMap.get(key).get(targetDatasetId));  
            // Since each dataset may unshared with different users, we unshare datasets one by one.
            dsSession.unshareUserDataset(userId, targetDatasetId, targetUserIds);
          }
        }
      }
      return Response.noContent().build();
    }
    catch(JSONException | RequestMisformatException e) {
      throw new BadRequestException(e);
    }
  }

  /**
   * Convenience method to whittle out any non-valid target users
   * @param providedUserIds - set of user ids provided to the service
   * @return - subset of those provided user ids that belong to valid users.
   * @throws WdkModelException
   */
  protected Set<Long> identifyTargetUsers(Set<Long> providedUserIds) throws WdkModelException {
    Set<Long> targetUserIds = new HashSet<>();
    for (Long providedUserId : providedUserIds) {
      if (validateTargetUserId(providedUserId)) {
        targetUserIds.add(providedUserId);
      }
    }
    return targetUserIds;
  }

  @DELETE
  @Path("user-datasets/{datasetId}")
  public Response deleteById(@PathParam("datasetId") String datasetIdStr) throws WdkModelException {
	long userId = getUser(Access.PRIVATE).getUserId();
    long datasetId = parseLongId(datasetIdStr, new NotFoundException("No dataset found with ID " + datasetIdStr));
    UserDatasetStore dsStore = getUserDatasetStore();
    try (UserDatasetSession dsSession = dsStore.getSession(dsStore.getUsersRootDir())) {
      dsSession.deleteUserDataset(userId, datasetId);
    }  
    return Response.noContent().build();
  }

  private UserDatasetStore getUserDatasetStore() throws WdkModelException {
    UserDatasetStore userDatasetStore = getWdkModel().getUserDatasetStore();
    if (userDatasetStore == null) throw new WdkModelException("There is no userDatasetStore installed in the WDK Model.");
    return userDatasetStore;
  }

  /* not used yet.
  private UserDataset getUserDatasetObj(String datasetIdStr) throws WdkModelException {
    try {
      Integer datasetId = new Integer(datasetIdStr);
      UserBundle userBundle = getUserBundle(Access.PUBLIC); // TODO: temporary, for debugging
      return getUserDatasetStore().getUserDataset(userBundle.getTargetUser().getUserId(), datasetId);
    }
    catch (NumberFormatException e) {
      throw new BadRequestException(e);
    }   
  }
  */

  private long parseLongId(String idStr, RuntimeException exception) {
    if (FormatUtil.isInteger(idStr)) {
      return Long.parseLong(idStr);
    }
    throw exception;
  }

  /**
   * In addition to returning the target user's id, this identifies whether the session
   * user must be the target user.  Regardless of session user access, the target user
   * cannot be a guest.
   * @return
   * @throws WdkModelException
   */
  private User getUser(Access access) throws WdkModelException {
	if(access == Access.PRIVATE) return getPrivateRegisteredUser();
	User user = getUserBundle(access).getTargetUser();
	if(user.isGuest()) throw new NotFoundException("The user " + user.getUserId() + " has no datasets.");
	return user;
  }

  /**
   * Determines whether the target user is valid.  Any invalid user is noted in the logs.  Seems extreme to trash the whole operation
   * over one wayward user id.
   * @param targetUserId - id of target user to check for validity
   * @return - true is target user is valid and false otherwise.
   * @throws WdkModelException
   */
  private boolean validateTargetUserId(Long targetUserId) throws WdkModelException {
    UserBundle targetUserBundle = UserBundle.createFromTargetId(targetUserId.toString(), getSessionUser(), getWdkModel().getUserFactory(), isSessionUserAdmin());
    if (!targetUserBundle.isValidUserId()) {
      //throw new NotFoundException(WdkService.formatNotFound(UserService.USER_RESOURCE + targetUserBundle.getTargetUserIdString()));
      LOG.warn("This user dataset share service request contains the following invalid user: " + targetUserId);
      return false;	
    }
    return true;
  }
}
