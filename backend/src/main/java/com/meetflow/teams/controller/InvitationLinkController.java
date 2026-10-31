package com.meetflow.teams.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.teams.dto.InvitationLinkResponse;
import com.meetflow.teams.dto.TeamResponse;
import com.meetflow.teams.service.InvitationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The link in the invitation email. Looking it up needs no login (it is public in {@code
 * SecurityConfig}); joining does.
 */
@RestController
@RequestMapping("/api/invitation-links/{token}")
class InvitationLinkController {

  private final InvitationService invitationService;

  InvitationLinkController(InvitationService invitationService) {
    this.invitationService = invitationService;
  }

  @GetMapping
  InvitationLinkResponse lookUp(@PathVariable String token) {
    return invitationService.lookUp(token);
  }

  @PostMapping("/accept")
  TeamResponse accept(@AuthenticationPrincipal CurrentUser user, @PathVariable String token) {
    return invitationService.accept(user.id(), user.getUsername(), token);
  }
}
