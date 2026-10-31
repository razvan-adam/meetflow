package com.meetflow.teams.service;

import static java.util.Objects.requireNonNull;

import com.meetflow.teams.domain.Role;
import com.meetflow.teams.domain.Team;
import com.meetflow.teams.domain.TeamMembership;
import com.meetflow.teams.dto.TeamResponse;
import com.meetflow.teams.exception.TeamNotFoundException;
import com.meetflow.teams.repository.TeamMembershipRepository;
import com.meetflow.teams.repository.TeamRepository;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creating teams, and the teams a user belongs to. A user only ever sees their own teams. */
@Service
public class TeamService {

  private final TeamRepository teams;
  private final TeamMembershipRepository memberships;

  TeamService(TeamRepository teams, TeamMembershipRepository memberships) {
    this.teams = teams;
    this.memberships = memberships;
  }

  /** Creates the team with its creator as the first admin. */
  @Transactional
  public TeamResponse create(UUID userId, String name) {
    var team = teams.save(new Team(name.strip(), userId));
    memberships.save(new TeamMembership(team.getId(), userId, Role.ADMIN));
    return new TeamResponse(team.getId(), team.getName(), Role.ADMIN);
  }

  /** The user's teams, sorted by name. */
  @Transactional(readOnly = true)
  public List<TeamResponse> listFor(UUID userId) {
    var roles =
        memberships.findByIdUserId(userId).stream()
            .collect(
                Collectors.toMap(
                    membership -> membership.getId().teamId(), TeamMembership::getRole));
    var byName = Collator.getInstance(Locale.ENGLISH);
    return teams.findAllById(roles.keySet()).stream()
        .map(
            team ->
                new TeamResponse(
                    team.getId(), team.getName(), requireNonNull(roles.get(team.getId()))))
        .sorted(Comparator.comparing(TeamResponse::name, byName))
        .toList();
  }

  /**
   * One of the user's teams.
   *
   * @throws TeamNotFoundException if it doesn't exist or the user isn't a member
   */
  @Transactional(readOnly = true)
  public TeamResponse get(UUID userId, UUID teamId) {
    var membership =
        memberships
            .findById(new TeamMembership.Id(teamId, userId))
            .orElseThrow(TeamNotFoundException::new);
    var team = teams.findById(teamId).orElseThrow(TeamNotFoundException::new);
    return new TeamResponse(team.getId(), team.getName(), membership.getRole());
  }
}
