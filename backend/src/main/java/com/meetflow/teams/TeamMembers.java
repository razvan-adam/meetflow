package com.meetflow.teams;

import com.meetflow.teams.service.TeamAccess;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team-level access control for other modules: users only reach the meetings and tasks of teams
 * they belong to. A user who isn't a member gets 404, so the team's records aren't revealed.
 */
@Service
public class TeamMembers {

  private final TeamAccess access;

  TeamMembers(TeamAccess access) {
    this.access = access;
  }

  @Transactional(readOnly = true)
  public boolean isMember(UUID userId, UUID teamId) {
    return access.membership(userId, teamId).isPresent();
  }

  /** Throws a 404 error response if the user isn't a member of the team. */
  @Transactional(readOnly = true)
  public void requireMember(UUID userId, UUID teamId) {
    access.requireMember(userId, teamId);
  }

  /**
   * Throws a 404 error response if the user isn't a member of the team, and 403 if they are a
   * member but not an admin.
   */
  @Transactional(readOnly = true)
  public void requireAdmin(UUID userId, UUID teamId) {
    access.requireAdmin(userId, teamId);
  }
}
