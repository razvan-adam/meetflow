package com.meetflow.teams;

import com.meetflow.teams.dto.TeamResponse;
import com.meetflow.teams.service.TeamAccess;
import com.meetflow.teams.service.TeamService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

  /**
   * For a change that gives someone work in the team, as a task's owner, a series' organizer or a
   * meeting's participant: holds the team's membership until the caller's transaction ends, so the
   * membership checked after it can't change before the change commits. A member's removal waits
   * for it, or it waits for the removal and then sees them gone. Take it before locking a series or
   * a meeting, the order in which a removal takes them, or the two can deadlock.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void holdMembership(UUID teamId) {
    access.holdMembership(teamId);
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

  /** Those of the users who are members of the team, in the same order. */
  @Transactional(readOnly = true)
  public List<UUID> membersAmong(Collection<UUID> userIds, UUID teamId) {
    var members = access.memberIds(teamId);
    return userIds.stream().filter(members::contains).toList();
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
