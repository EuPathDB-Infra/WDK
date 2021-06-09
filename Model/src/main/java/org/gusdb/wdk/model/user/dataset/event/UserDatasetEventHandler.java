package org.gusdb.wdk.model.user.dataset.event;

import java.nio.file.Path;
import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.user.dataset.UserDatasetTypeHandler;
import org.gusdb.wdk.model.user.dataset.event.model.EventRow;
import org.gusdb.wdk.model.user.dataset.event.model.UserDatasetEvent;
import org.gusdb.wdk.model.user.dataset.event.model.UserDatasetEventStatus;
import org.gusdb.wdk.model.user.dataset.event.model.UserDatasetUninstallEvent;
import org.gusdb.wdk.model.user.dataset.event.repo.InstalledUserDatasetRepo;
import org.gusdb.wdk.model.user.dataset.event.repo.UserDatasetEventRepo;
import org.gusdb.wdk.model.user.dataset.event.repo.UserDatasetOwnerRepo;
import org.gusdb.wdk.model.user.dataset.event.repo.UserDatasetShareRepo;

/**
 * Handle events that impact which user datasets a user can use in this website.
 * <p>
 * We use the word "installed" to mean that a user dataset is available for use
 * on this website for this user.  It never means anything else.
 * <p>
 * Three database tables control if a user sees a dataset as installed.
 * <ol>
 *   <li>
 *     The InstalledUserDataset table holds the IDs of all datasets that are
 *     installed for use on this site. It includes the name of the UD, to show
 *     to the user in parameters in WDK Searches.
 *   </li>
 *   <li>
 *     The UserDatasetOwner table tells us who owns the UD that is in the
 *     InstalledUserDataset table. It has a foreign key to the
 *     InstalledUserDataset table.
 *   </li>
 *   <li>
 *     the UserDatasetSharedWith table tells us who has share access to an
 *     installed UD.  has a foreign key to the InstalledUserDataset table.
 *   </li>
 * </ol>
 * <p>
 * An install event causes the UD to be inserted into the install table and the
 * owner table.
 * <p>
 * A share event causes the a row to be inserted into the shared table, (and
 * unshare is vice versa)
 * <p>
 * A delete event causes rows from share, owner and install table to be
 * removed.
 * <p>
 * To see which UDs a user has installed, we query the union of the Owner and
 * Shared table.
 *
 * @author Steve
 */
// TODO: it seems we should add the owner as a column to the
//       InstalledUserDatasets table, and lose the Owner table, since they are 1-1
// TODO: if the user changes the name of their UD, this will not be reflected in
//       installed UDs, since there is no event to convey that.
public abstract class UserDatasetEventHandler
{
  private static final Logger LOG = Logger.getLogger(UserDatasetEventHandler.class);


  private final DataSource dataSource;

  private final UserDatasetEventRepo     eventRepo;
  private final InstalledUserDatasetRepo installRepo;
  private final UserDatasetShareRepo     shareRepo;
  private final UserDatasetOwnerRepo     ownerRepo;

  private final Path   tmpDir;
  private final String projectId;


  public UserDatasetEventHandler(
    final DataSource ds,
    final Path tmpDir,
    final String dsSchema,
    final String projectId
  ) {
    this.dataSource = ds;
    this.tmpDir     = tmpDir;
    this.projectId  = projectId;

    this.eventRepo   = new UserDatasetEventRepo(dsSchema, ds);
    this.installRepo = new InstalledUserDatasetRepo(dsSchema, ds);
    this.shareRepo   = new UserDatasetShareRepo(dsSchema, ds);
    this.ownerRepo   = new UserDatasetOwnerRepo(dsSchema, ds);
  }

  /**
   * Checks if the given event should be handled.
   * <p>
   * See specific implementations for criteria handleable events must meet.
   */
  public abstract boolean shouldHandleEvent(EventRow row);

  /**
   * Attempts to acquire a process lock on the event row.  When locked, other
   * simultaneous executions of this tool will ignore the event.
   * <p>
   * If another process has claimed the event this method will do nothing and
   * return false.
   * <p>
   * When claimed, the event will be recorded or updated in the DB with an
   * 'in progress' status.
   *
   * @param row Row to attempt to lock.
   *
   * @return {@code true} if the row could be locked and now "belongs to" this
   * process.  {@code false} if another process has already claimed this event
   * row.
   */
  public abstract boolean acquireEventLock(EventRow row);

  /**
   * Marks an event as failed in the DB.  All future events for this UD should
   * be ignored.
   *
   * @param row Row representing the event to mark as failed.
   */
  public abstract void markEventAsFailed(EventRow row);

  /**
   * Method to handle an event that is either not relevant to this WDK project
   * or is related to an unsupported type.  The event is not installed but it is
   * noted in the dataset as handled so that the event is not repeatedly and
   * unnecessarily processed.
   */
  public void handleNoOpEvent(EventRow row) {
    closeEventHandling(row);
  }

  public void handleUninstallEvent(
    UserDatasetUninstallEvent event,
    UserDatasetTypeHandler typeHandler
  ) throws WdkModelException {
    LOG.info("Uninstalling user dataset " + event.getUserDatasetId());

    revokeAllAccess(event.getUserDatasetId());
    typeHandler.uninstallInAppDb(event.getUserDatasetId(), getTmpDir(), getProjectId());

    installRepo.deleteUserDataset(event.getUserDatasetId());

    closeEventHandling(event);
  }

  protected UserDatasetShareRepo getShareRepo() {
    return shareRepo;
  }

  protected UserDatasetOwnerRepo getOwnerRepo() {
    return ownerRepo;
  }

  protected InstalledUserDatasetRepo getInstallRepo() {
    return installRepo;
  }

  protected UserDatasetEventRepo getEventRepo() {
    return eventRepo;
  }

  protected Path getTmpDir() {
    return tmpDir;
  }

  protected String getProjectId() {
    return projectId;
  }

  protected DataSource getDataSource() {
    return dataSource;
  }

  protected void revokeAllAccess(long userDatasetId) {
    LOG.info("Revoking all access to user dataset " + userDatasetId);
    ownerRepo.deleteAllOwners(userDatasetId);
    shareRepo.deleteAllShares(userDatasetId);
  }

  protected void closeEventHandling(UserDatasetEvent event) {
    closeEventHandling(new EventRow(
        event.getEventId(),
        event.getUserDatasetId(),
        event.getUserDatasetType()
      )
    );
  }

  protected abstract void closeEventHandling(EventRow row);
}
