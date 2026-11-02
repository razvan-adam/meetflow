package com.meetflow.meetings.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.meetings.dto.DecisionRequest;
import com.meetflow.meetings.dto.DecisionResponse;
import com.meetflow.meetings.service.DecisionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DecisionController {

  private final DecisionService decisionService;

  DecisionController(DecisionService decisionService) {
    this.decisionService = decisionService;
  }

  @GetMapping("/api/meetings/{meetingId}/decisions")
  List<DecisionResponse> list(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID meetingId) {
    return decisionService.list(user.id(), meetingId);
  }

  @PostMapping("/api/agenda-items/{itemId}/decisions")
  @ResponseStatus(HttpStatus.CREATED)
  DecisionResponse add(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID itemId,
      @Valid @RequestBody DecisionRequest body) {
    return decisionService.add(user.id(), itemId, body.text());
  }

  @PutMapping("/api/decisions/{decisionId}")
  DecisionResponse edit(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID decisionId,
      @Valid @RequestBody DecisionRequest body) {
    return decisionService.edit(user.id(), decisionId, body.text());
  }

  @DeleteMapping("/api/decisions/{decisionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remove(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID decisionId) {
    decisionService.remove(user.id(), decisionId);
  }
}
