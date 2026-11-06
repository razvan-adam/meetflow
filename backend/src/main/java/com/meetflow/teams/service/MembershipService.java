package com.meetflow.teams.service;

import com.meetflow.identity.UserDirectory;
import com.meetflow.teams.MemberRemovalEvent;
import com.meetflow.teams.domain.Role;
import com.meetflow.teams.domain.TeamMembership;
import com.meetflow.teams.dto.MemberResponse;
import com.meetflow.teams.dto.RemovalRequest;
import com.meetflow.teams.exception.InvalidHandoverException;
import com.meetflow.teams.exception.LastAdminException;
import com.meetflow.teams.exception.TeamNotFoundException;
import com.meetflow.teams.repository.TeamMembershipRepository;
import com.meetflow.teams.repository.TeamRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managing a team's members (MF-11): who is an admin, and removing members once their work is
 * handed over. Team admins only, and a team always keeps at least one admin.
 */
@Service
public class MembershipService {

  private final TeamRepository teams;
  private final TeamMembershipRepository memberships;
  private final TeamAccess access;
  private final UserDirectory users;
  private final ApplicationEventPublisher events;

  MembershipService(
      TeamRepository teams,
      TeamMembershipRepository memberships,
      TeamAccess access,
      UserDirectory users,
      ApplicationEventPublisher events) {
    this.teams = teams;
    this.memberships = memberships;
    this.access = access;
    this.users = users;
    this.events = events;
  }

  /**
   * Makes a member an admin or takes their admin rights, their own included. 409 if that would
   * leave the team without an admin.
   */
  @Transactional
  public MemberResponse changeRole(UUID adminId, UUID teamId, UUID memberId, Role role) {
    var member = lockAndFind(adminId, teamId, memberId);
    if (member.getRole() != role) {
      if (role != Role.ADMIN) {
        requireAnotherAdmin(teamId);
      }
      member.changeRole(role);
    }
    var name = users.namesOf(List.of(memberId)).getOrDefault(memberId, "");
    return new MemberResponse(memberId, name, member.getRole());
  }

  /**
   * Removes the member, themselves included, once each open task they own is reassigned or removed
   * and each series they organize has a new organizer. Everything happens in one transaction: if
   * the handover doesn't match what the member has now (409) or names someone who can't take it
   * (400), nothing changes. The member loses access at once, because every request checks the
   * membership.
   */
  @Transactional
  public void remove(UUID adminId, UUID teamId, UUID memberId, RemovalRequest handover) {
    var member = lockAndFind(adminId, teamId, memberId);
    if (member.getRole() == Role.ADMIN) {
      requireAnotherAdmin(teamId);
    }
    events.publishEvent(toEvent(teamId, memberId, adminId, handover));
    memberships.delete(member);
  }

  /**
   * Locks the team until the transaction ends, then checks the admin and the member, so neither can
   * change between the check and the change.
   */
  private TeamMembership lockAndFind(UUID adminId, UUID teamId, UUID memberId) {
    if (teams.findForUpdateById(teamId).isEmpty()) {
      throw new TeamNotFoundException();
    }
    access.requireAdmin(adminId, teamId);
    return access.requireMember(memberId, teamId);
  }

  private void requireAnotherAdmin(UUID teamId) {
    if (memberships.countByIdTeamIdAndRole(teamId, Role.ADMIN) < 2) {
      throw new LastAdminException();
    }
  }

  private static MemberRemovalEvent toEvent(
      UUID teamId, UUID memberId, UUID adminId, RemovalRequest handover) {
    Map<UUID, UUID> taskOwners = new HashMap<>();
    Set<UUID> tasksToRemove = new HashSet<>();
    for (var task : handover.tasks()) {
      if (taskOwners.containsKey(task.taskId()) || tasksToRemove.contains(task.taskId())) {
        throw new InvalidHandoverException("Each task can only be handed over once.");
      }
      var newOwner = task.newOwnerId();
      if (newOwner == null) {
        tasksToRemove.add(task.taskId());
      } else if (newOwner.equals(memberId)) {
        throw new InvalidHandoverException("A task can't go to the member being removed.");
      } else {
        taskOwners.put(task.taskId(), newOwner);
      }
    }
    Map<UUID, UUID> seriesOrganizers = new HashMap<>();
    for (var series : handover.series()) {
      if (series.organizerId().equals(memberId)) {
        throw new InvalidHandoverException("A series can't go to the member being removed.");
      }
      if (seriesOrganizers.put(series.seriesId(), series.organizerId()) != null) {
        throw new InvalidHandoverException("Each series can only be handed over once.");
      }
    }
    return new MemberRemovalEvent(
        teamId,
        memberId,
        adminId,
        Map.copyOf(taskOwners),
        Set.copyOf(tasksToRemove),
        Map.copyOf(seriesOrganizers));
  }
}
