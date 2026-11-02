package com.meetflow.tasks.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.tasks.dto.TaskRequest;
import com.meetflow.tasks.dto.TaskResponse;
import com.meetflow.tasks.service.TaskService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TaskController {

  private final TaskService taskService;

  TaskController(TaskService taskService) {
    this.taskService = taskService;
  }

  @GetMapping("/api/meetings/{meetingId}/tasks")
  List<TaskResponse> list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID meetingId) {
    return taskService.listForMeeting(user.id(), meetingId);
  }

  @PostMapping("/api/agenda-items/{itemId}/tasks")
  @ResponseStatus(HttpStatus.CREATED)
  TaskResponse createFromAgendaItem(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID itemId,
      @Valid @RequestBody TaskRequest body) {
    return taskService.createFromAgendaItem(
        user.id(), itemId, body.description(), body.ownerId(), body.deadline());
  }

  @PostMapping("/api/decisions/{decisionId}/tasks")
  @ResponseStatus(HttpStatus.CREATED)
  TaskResponse createFromDecision(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID decisionId,
      @Valid @RequestBody TaskRequest body) {
    return taskService.createFromDecision(
        user.id(), decisionId, body.description(), body.ownerId(), body.deadline());
  }
}
