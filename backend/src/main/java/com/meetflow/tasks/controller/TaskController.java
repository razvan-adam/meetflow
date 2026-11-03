package com.meetflow.tasks.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.tasks.dto.PendingTaskResponse;
import com.meetflow.tasks.dto.TaskChangeResponse;
import com.meetflow.tasks.dto.TaskDeadlineRequest;
import com.meetflow.tasks.dto.TaskDescriptionRequest;
import com.meetflow.tasks.dto.TaskEmailsResponse;
import com.meetflow.tasks.dto.TaskOwnerRequest;
import com.meetflow.tasks.dto.TaskRequest;
import com.meetflow.tasks.dto.TaskResponse;
import com.meetflow.tasks.dto.TaskStatusRequest;
import com.meetflow.tasks.service.TaskService;
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
class TaskController {

  private final TaskService taskService;

  TaskController(TaskService taskService) {
    this.taskService = taskService;
  }

  @GetMapping("/api/users/me/tasks")
  List<PendingTaskResponse> listPending(@AuthenticationPrincipal CurrentUser user) {
    return taskService.listPending(user.id());
  }

  @GetMapping("/api/meetings/{meetingId}/tasks")
  List<TaskResponse> list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID meetingId) {
    return taskService.listForMeeting(user.id(), meetingId);
  }

  @GetMapping("/api/meetings/{meetingId}/carried-over-tasks")
  List<TaskResponse> listCarriedOver(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID meetingId) {
    return taskService.listCarriedOver(user.id(), meetingId);
  }

  @GetMapping("/api/meetings/{meetingId}/task-emails")
  TaskEmailsResponse taskEmails(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID meetingId) {
    return taskService.taskEmails(user.id(), meetingId);
  }

  @GetMapping("/api/tasks/{taskId}")
  TaskResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID taskId) {
    return taskService.get(user.id(), taskId);
  }

  @GetMapping("/api/tasks/{taskId}/changes")
  List<TaskChangeResponse> history(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID taskId) {
    return taskService.history(user.id(), taskId);
  }

  @PutMapping("/api/tasks/{taskId}/status")
  TaskResponse changeStatus(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID taskId,
      @Valid @RequestBody TaskStatusRequest body) {
    return taskService.changeStatus(user.id(), taskId, body.status());
  }

  @PutMapping("/api/tasks/{taskId}/owner")
  TaskResponse changeOwner(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID taskId,
      @Valid @RequestBody TaskOwnerRequest body) {
    return taskService.changeOwner(user.id(), taskId, body.ownerId());
  }

  @PutMapping("/api/tasks/{taskId}/deadline")
  TaskResponse changeDeadline(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID taskId,
      @Valid @RequestBody TaskDeadlineRequest body) {
    return taskService.changeDeadline(user.id(), taskId, body.deadline());
  }

  @PutMapping("/api/tasks/{taskId}/description")
  TaskResponse reword(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID taskId,
      @Valid @RequestBody TaskDescriptionRequest body) {
    return taskService.reword(user.id(), taskId, body.description());
  }

  @DeleteMapping("/api/tasks/{taskId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID taskId) {
    taskService.delete(user.id(), taskId);
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
