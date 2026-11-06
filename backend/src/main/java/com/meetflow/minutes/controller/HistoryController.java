package com.meetflow.minutes.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.minutes.dto.HistorySeries;
import com.meetflow.minutes.service.HistoryService;
import java.util.List;
import java.util.UUID;
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

  /** {@code q} holds the search words; without it, every closed meeting is listed. */
  @GetMapping("/api/teams/{teamId}/history")
  List<HistorySeries> get(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID teamId,
      @RequestParam(defaultValue = "") String q) {
    return history.of(user.id(), teamId, q);
  }
}
