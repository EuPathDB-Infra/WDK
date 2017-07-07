package org.gusdb.wdk.model.user.dataset.event;

import java.util.Collections;
import java.util.Set;

import org.gusdb.wdk.model.user.dataset.UserDatasetType;

public class UserDatasetEvent {
  private Set<String> projectsFilter; // null if no filter
  private Long userDatasetId;
  private UserDatasetType userDatasetType;
  private Long eventId;
  
  public UserDatasetEvent(Long eventId, Set<String> projectsFilter, Long userDatasetId, UserDatasetType userDatasetType) {
    this.eventId = eventId;
    this.projectsFilter = projectsFilter;
    this.userDatasetId = userDatasetId;
    this.userDatasetType = userDatasetType;
  }
  
  public Set<String> getProjectsFilter() { return Collections.unmodifiableSet(projectsFilter); }
  public Long getUserDatasetId() { return userDatasetId; }
  public UserDatasetType getUserDatasetType() { return userDatasetType; }
  public Long getEventId() { return eventId; }
}
