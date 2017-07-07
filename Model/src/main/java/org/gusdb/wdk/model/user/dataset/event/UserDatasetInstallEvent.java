package org.gusdb.wdk.model.user.dataset.event;

import java.util.Collections;
import java.util.Set;

import org.gusdb.wdk.model.user.dataset.UserDatasetDependency;
import org.gusdb.wdk.model.user.dataset.UserDatasetType;

public class UserDatasetInstallEvent extends UserDatasetEvent {

  private Set<UserDatasetDependency> dependencies;
  private Long ownerUserId;

  public UserDatasetInstallEvent(Long eventId, Set<String> projectsFilter, Long userDatasetId,
      UserDatasetType userDatasetType, Long ownerUserId, Set<UserDatasetDependency> dependencies) {
    super(eventId, projectsFilter, userDatasetId, userDatasetType);
    this.dependencies = dependencies;
    this.ownerUserId = ownerUserId;
  }

  public Set<UserDatasetDependency> getDependencies() { return Collections.unmodifiableSet(dependencies); }
  public Long getOwnerUserId() { return ownerUserId; }
}
