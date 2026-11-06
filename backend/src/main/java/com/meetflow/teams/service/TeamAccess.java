package com.meetflow.teams.service;

import com.meetflow.teams.domain.Role;
import com.meetflow.teams.domain.TeamMembership;
import com.meetflow.teams.exception.NotTeamAdminException;
import com.meetflow.teams.exception.TeamNotFoundException;
import com.meetflow.teams.repository.TeamMembershipRepository;
import com.meetflow.teams.repository.TeamRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Team-level access control: who is a member of a team, and who is one of its admins. */
@Service
public class TeamAccess {

  private final TeamMembershipRepository memberships;
  private final TeamRepository teams;

  TeamAccess(TeamMembershipRepository memberships, TeamRepository teams) {
    this.memberships = memberships;
    this.teams = teams;
  }

  /**
   * Holds the team's membership until the transaction ends: no member can be removed or change role
   * meanwhile, while other holders don't wait for each other.
   *
   * @throws TeamNotFoundException if there is no such team
   */
  public void holdMembership(UUID teamId) {
    if (teams.findForShareById(teamId).isEmpty()) {
      throw new TeamNotFoundException();
    }
  }

  public Optional<TeamMembership> membership(UUID userId, UUID teamId) {
    return memberships.findById(new TeamMembership.Id(teamId, userId));
  }

  /** The IDs of the team's members. */
  public Set<UUID> memberIds(UUID teamId) {
    return memberships.findByIdTeamId(teamId).stream()
        .map(membership -> membership.getId().userId())
        .collect(Collectors.toSet());
  }

  /**
   * Checks that the user is a member of the team.
   *
   * @throws TeamNotFoundException if not (404, so the team isn't revealed)
   */
  public TeamMembership requireMember(UUID userId, UUID teamId) {
    return membership(userId, teamId).orElseThrow(TeamNotFoundException::new);
  }

  /**
   * Checks that the user is one of the team's admins.
   *
   * @throws TeamNotFoundException if the user isn't a member (404, so the team isn't revealed)
   * @throws NotTeamAdminException if the user is a member but not an admin
   */
  public void requireAdmin(UUID userId, UUID teamId) {
    if (requireMember(userId, teamId).getRole() != Role.ADMIN) {
      throw new NotTeamAdminException();
    }
  }
}
