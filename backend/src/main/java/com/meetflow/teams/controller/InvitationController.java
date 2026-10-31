package com.meetflow.teams.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.teams.dto.InvitationResponse;
import com.meetflow.teams.dto.InviteRequest;
import com.meetflow.teams.service.InvitationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Team admins invite colleagues, see pending invitations and cancel them. */
@RestController
class InvitationController {

  private final InvitationService invitationService;

  InvitationController(InvitationService invitationService) {
    this.invitationService = invitationService;
  }

  @PostMapping("/api/teams/{teamId}/invitations")
  @ResponseStatus(HttpStatus.CREATED)
  InvitationResponse invite(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID teamId,
      @Valid @RequestBody InviteRequest body) {
    return invitationService.invite(user.id(), teamId, body.email());
  }

  @GetMapping("/api/teams/{teamId}/invitations")
  List<InvitationResponse> listPending(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID teamId) {
    return invitationService.listPending(user.id(), teamId);
  }

  @DeleteMapping("/api/invitations/{invitationId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID invitationId) {
    invitationService.cancel(user.id(), invitationId);
  }
}
