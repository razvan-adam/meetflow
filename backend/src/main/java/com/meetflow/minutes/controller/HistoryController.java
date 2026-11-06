package com.meetflow.minutes.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.minutes.dto.HistoryResponse;
import com.meetflow.minutes.service.HistoryService;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HistoryController {

  private final HistoryService history;

  HistoryController(HistoryService history) {
    this.history = history;
  }

  /**
   * {@code q} holds the search words; without it, every closed meeting is listed. {@code after}
   * asks for the page after the one whose {@code next} it is.
   */
  @GetMapping("/api/teams/{teamId}/history")
  HistoryResponse get(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID teamId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(required = false) @Nullable UUID after) {
    return history.of(user.id(), teamId, q, after);
  }
}
