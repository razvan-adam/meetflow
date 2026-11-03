package com.meetflow.teams;

import com.meetflow.teams.dto.TeamResponse;
import com.meetflow.teams.service.TeamAccess;
import com.meetflow.teams.service.TeamService;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Team-level access control for other modules: users only reach the meetings and tasks of teams
 * they belong to. A user who isn't a member gets 404, so the team's records aren't revealed.
 */
@Service
public class TeamMembers {

  private final TeamAccess access;
  private final TeamService teams;

  TeamMembers(TeamAccess access, TeamService teams) {
    this.access = access;
    this.teams = teams;
  }

  /** The names of the teams the user belongs to, by ID. */
  @Transactional(readOnly = true)
  public Map<UUID, String> teamNamesOf(UUID userId) {
    return teams.listFor(userId).stream()
        .collect(Collectors.toMap(TeamResponse::id, TeamResponse::name));
  }

  @Transactional(readOnly = true)
  public boolean isMember(UUID userId, UUID teamId) {
    return access.membership(userId, teamId).isPresent();
  }

  /** Whether all these users are members of the team. */
  @Transactional(readOnly = true)
  public boolean areMembers(Collection<UUID> userIds, UUID teamId) {
    return access.memberIds(teamId).containsAll(userIds);
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
