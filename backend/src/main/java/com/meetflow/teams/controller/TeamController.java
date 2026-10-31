package com.meetflow.teams.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.teams.dto.CreateTeamRequest;
import com.meetflow.teams.dto.MemberResponse;
import com.meetflow.teams.dto.TeamResponse;
import com.meetflow.teams.service.TeamService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
class TeamController {

  private final TeamService teamService;

  TeamController(TeamService teamService) {
    this.teamService = teamService;
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

  @GetMapping("/{teamId}")
  TeamResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID teamId) {
    return teamService.get(user.id(), teamId);
  }
}
