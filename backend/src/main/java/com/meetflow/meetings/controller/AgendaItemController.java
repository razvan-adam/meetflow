package com.meetflow.meetings.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.meetings.dto.AgendaItemRequest;
import com.meetflow.meetings.dto.AgendaItemResponse;
import com.meetflow.meetings.dto.ReorderAgendaRequest;
import com.meetflow.meetings.service.AgendaService;
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
class AgendaItemController {

  private final AgendaService agendaService;

  AgendaItemController(AgendaService agendaService) {
    this.agendaService = agendaService;
  }

  @GetMapping("/api/meetings/{meetingId}/agenda-items")
  List<AgendaItemResponse> list(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID meetingId) {
    return agendaService.list(user.id(), meetingId);
  }

  @PostMapping("/api/meetings/{meetingId}/agenda-items")
  @ResponseStatus(HttpStatus.CREATED)
  AgendaItemResponse add(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID meetingId,
      @Valid @RequestBody AgendaItemRequest body) {
    return agendaService.add(user.id(), meetingId, body.title());
  }

  @PutMapping("/api/meetings/{meetingId}/agenda-items/order")
  List<AgendaItemResponse> reorder(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID meetingId,
      @Valid @RequestBody ReorderAgendaRequest body) {
    return agendaService.reorder(user.id(), meetingId, body.itemIds());
  }

  @PutMapping("/api/agenda-items/{itemId}")
  AgendaItemResponse rename(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID itemId,
      @Valid @RequestBody AgendaItemRequest body) {
    return agendaService.rename(user.id(), itemId, body.title());
  }

  @DeleteMapping("/api/agenda-items/{itemId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remove(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID itemId) {
    agendaService.remove(user.id(), itemId);
  }
}
