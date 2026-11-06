package com.meetflow.teams.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.teams.dto.CreateTeamRequest;
import com.meetflow.teams.dto.MemberResponse;
import com.meetflow.teams.dto.RemovalRequest;
import com.meetflow.teams.dto.RoleRequest;
import com.meetflow.teams.dto.TeamResponse;
import com.meetflow.teams.service.MembershipService;
import com.meetflow.teams.service.TeamService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
class TeamController {

  private final TeamService teamService;
  private final MembershipService memberships;

  TeamController(TeamService teamService, MembershipService memberships) {
    this.teamService = teamService;
    this.memberships = memberships;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  TeamResponse create(
      @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateTeamRequest body) {
    return teamService.create(user.id(), body.name());
  }

  @GetMapping
  List<TeamResponse> list(@AuthenticationPrincipal CurrentUser user) {
    return teamService.listFor(user.id());
  }

  @GetMapping("/{teamId}/members")
  List<MemberResponse> listMembers(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID teamId) {
    return teamService.listMembers(user.id(), teamId);
  }

  /** Team admins make a member an admin, or take their admin rights (MF-11). */
  @PutMapping("/{teamId}/members/{memberId}/role")
  MemberResponse changeRole(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID teamId,
      @PathVariable UUID memberId,
      @Valid @RequestBody RoleRequest body) {
    return memberships.changeRole(user.id(), teamId, memberId, body.role());
  }

  /**
   * Team admins remove a member, with the handover of their open tasks and series (MF-11). A POST,
   * not a DELETE, because it carries a body, which proxies don't reliably pass on a DELETE.
   */
  @PostMapping("/{teamId}/members/{memberId}/removal")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remove(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID teamId,
      @PathVariable UUID memberId,
      @Valid @RequestBody RemovalRequest body) {
    memberships.remove(user.id(), teamId, memberId, body);
  }

  @GetMapping("/{teamId}")
  TeamResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID teamId) {
    return teamService.get(user.id(), teamId);
  }
}
