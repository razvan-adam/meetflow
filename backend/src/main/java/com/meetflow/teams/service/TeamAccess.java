package com.meetflow.teams.service;

import com.meetflow.teams.domain.Role;
import com.meetflow.teams.domain.TeamMembership;
import com.meetflow.teams.exception.NotTeamAdminException;
import com.meetflow.teams.exception.TeamNotFoundException;
import com.meetflow.teams.repository.TeamMembershipRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Team-level access control: who is a member of a team, and who is one of its admins. */
@Service
public class TeamAccess {

  private final TeamMembershipRepository memberships;

  TeamAccess(TeamMembershipRepository memberships) {
    this.memberships = memberships;
  }

  public Optional<TeamMembership> membership(UUID userId, UUID teamId) {
    return memberships.findById(new TeamMembership.Id(teamId, userId));
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
