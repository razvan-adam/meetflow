package com.meetflow.tasks.service;

import com.meetflow.identity.UserDirectory;
import com.meetflow.meetings.TaskOrigin;
import com.meetflow.meetings.TaskOrigins;
import com.meetflow.tasks.domain.Task;
import com.meetflow.tasks.domain.TaskChange;
import com.meetflow.tasks.dto.TaskResponse;
import com.meetflow.tasks.exception.OwnerNotParticipantException;
import com.meetflow.tasks.repository.TaskChangeRepository;
import com.meetflow.tasks.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating tasks during a meeting (MF-17). The series organizer turns what was agreed into tasks,
 * from an agenda item or a decision, and every member of the team can read them.
 */
@Service
public class TaskService {

  private final TaskRepository tasks;
  private final TaskChangeRepository changes;
  private final TaskOrigins origins;
  private final UserDirectory users;

  TaskService(
      TaskRepository tasks,
      TaskChangeRepository changes,
      TaskOrigins origins,
      UserDirectory users) {
    this.tasks = tasks;
    this.changes = changes;
    this.origins = origins;
    this.users = users;
  }

  /** The tasks created in the meeting, oldest first. */
  @Transactional(readOnly = true)
  public List<TaskResponse> listForMeeting(UUID userId, UUID meetingId) {
    origins.requireVisibleMeeting(userId, meetingId);
    var found = tasks.findByMeetingIdAndDeletedAtIsNullOrderByCreatedAt(meetingId);
    var names = users.namesOf(found.stream().map(Task::getOwnerId).toList());
    return found.stream().map(task -> toResponse(task, names)).toList();
  }

  @Transactional
  public TaskResponse createFromAgendaItem(
      UUID userId, UUID itemId, String description, UUID ownerId, LocalDate deadline) {
    return create(userId, origins.ofAgendaItem(userId, itemId), description, ownerId, deadline);
  }

  @Transactional
  public TaskResponse createFromDecision(
      UUID userId, UUID decisionId, String description, UUID ownerId, LocalDate deadline) {
    return create(userId, origins.ofDecision(userId, decisionId), description, ownerId, deadline);
  }

  private TaskResponse create(
      UUID userId, TaskOrigin origin, String description, UUID ownerId, LocalDate deadline) {
    if (!origin.participantIds().contains(ownerId)) {
      throw new OwnerNotParticipantException();
    }
    var task = tasks.save(new Task(origin, description.strip(), ownerId, deadline, userId));
    changes.save(TaskChange.creationOf(task, userId));
    return toResponse(task, users.namesOf(List.of(ownerId)));
  }

  private static TaskResponse toResponse(Task task, Map<UUID, String> names) {
    return new TaskResponse(
        task.getId(),
        task.getAgendaItemId(),
        task.getDecisionId(),
        task.getDescription(),
        task.getOwnerId(),
        names.getOrDefault(task.getOwnerId(), ""),
        task.getDeadline(),
        task.getStatus());
  }
}
