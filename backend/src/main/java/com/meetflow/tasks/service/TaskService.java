package com.meetflow.tasks.service;

import com.meetflow.identity.UserDirectory;
import com.meetflow.meetings.TaskMeetings;
import com.meetflow.meetings.TaskOrigin;
import com.meetflow.tasks.domain.Task;
import com.meetflow.tasks.domain.TaskChange;
import com.meetflow.tasks.domain.TaskStatus;
import com.meetflow.tasks.dto.TaskChangeResponse;
import com.meetflow.tasks.dto.TaskResponse;
import com.meetflow.tasks.exception.NotAllowedToChangeTaskException;
import com.meetflow.tasks.exception.OwnerNotMemberException;
import com.meetflow.tasks.exception.OwnerNotParticipantException;
import com.meetflow.tasks.exception.TaskNotFoundException;
import com.meetflow.tasks.repository.TaskChangeRepository;
import com.meetflow.tasks.repository.TaskRepository;
import com.meetflow.teams.TeamMembers;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tasks: creating them during a meeting (MF-17) and keeping them up to date (MF-18). Every member
 * of the team can read a task. Its owner and the series organizer change its status, and the
 * organizer its owner and deadline, during a meeting or between meetings. The organizer can also
 * reword and delete it while any meeting of its series runs, and create it while its own runs.
 */
@Service
public class TaskService {

  private final TaskRepository tasks;
  private final TaskChangeRepository changes;
  private final TaskMeetings meetings;
  private final TeamMembers teamMembers;
  private final UserDirectory users;

  TaskService(
      TaskRepository tasks,
      TaskChangeRepository changes,
      TaskMeetings meetings,
      TeamMembers teamMembers,
      UserDirectory users) {
    this.tasks = tasks;
    this.changes = changes;
    this.meetings = meetings;
    this.teamMembers = teamMembers;
    this.users = users;
  }

  /** The tasks created in the meeting, oldest first. */
  @Transactional(readOnly = true)
  public List<TaskResponse> listForMeeting(UUID userId, UUID meetingId) {
    meetings.requireVisibleMeeting(userId, meetingId);
    var found = tasks.findByMeetingIdAndDeletedAtIsNullOrderByCreatedAt(meetingId);
    var names = users.namesOf(found.stream().map(Task::getOwnerId).toList());
    return found.stream().map(task -> toResponse(task, names)).toList();
  }

  @Transactional(readOnly = true)
  public TaskResponse get(UUID userId, UUID taskId) {
    return toResponse(findVisible(userId, taskId));
  }

  /** The task's history, latest first. */
  @Transactional(readOnly = true)
  public List<TaskChangeResponse> history(UUID userId, UUID taskId) {
    findVisible(userId, taskId);
    var found = changes.findByTaskIdOrderByChangedAtDesc(taskId);
    var names =
        users.namesOf(
            found.stream()
                .flatMap(
                    change ->
                        Stream.of(
                            change.getChangedBy(), change.getOldOwnerId(), change.getNewOwnerId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    return found.stream().map(change -> toResponse(change, names)).toList();
  }

  @Transactional
  public TaskResponse createFromAgendaItem(
      UUID userId, UUID itemId, String description, UUID ownerId, LocalDate deadline) {
    return create(userId, meetings.ofAgendaItem(userId, itemId), description, ownerId, deadline);
  }

  @Transactional
  public TaskResponse createFromDecision(
      UUID userId, UUID decisionId, String description, UUID ownerId, LocalDate deadline) {
    return create(userId, meetings.ofDecision(userId, decisionId), description, ownerId, deadline);
  }

  /**
   * The owner or the organizer. Any status can follow any other, so a done task can be reopened and
   * a slip of the hand corrected. Choosing the current status changes nothing.
   */
  @Transactional
  public TaskResponse changeStatus(UUID userId, UUID taskId, TaskStatus status) {
    var task = findVisible(userId, taskId);
    if (!task.getOwnerId().equals(userId)) {
      requireOrganizer(userId, task);
    }
    var old = task.getStatus();
    if (old != status) {
      task.changeStatus(status);
      changes.save(TaskChange.ofStatus(task, userId, runningMeeting(task), old, status));
    }
    return toResponse(task);
  }

  /** The organizer only. Unlike a new task's owner, the new one needn't be at the meeting. */
  @Transactional
  public TaskResponse changeOwner(UUID userId, UUID taskId, UUID ownerId) {
    var task = findVisible(userId, taskId);
    requireOrganizer(userId, task);
    if (!teamMembers.areMembers(List.of(ownerId), task.getTeamId())) {
      throw new OwnerNotMemberException();
    }
    var old = task.getOwnerId();
    if (!old.equals(ownerId)) {
      task.changeOwner(ownerId);
      changes.save(TaskChange.ofOwner(task, userId, runningMeeting(task), old, ownerId));
    }
    return toResponse(task);
  }

  /** The organizer only. */
  @Transactional
  public TaskResponse changeDeadline(UUID userId, UUID taskId, LocalDate deadline) {
    var task = findVisible(userId, taskId);
    requireOrganizer(userId, task);
    var old = task.getDeadline();
    if (!old.equals(deadline)) {
      task.changeDeadline(deadline);
      changes.save(TaskChange.ofDeadline(task, userId, runningMeeting(task), old, deadline));
    }
    return toResponse(task);
  }

  /**
   * The organizer only, while any meeting of the task's series runs. During the task's own meeting
   * this is a correction before anyone is sent the task, so it stays out of the history; during a
   * later one people have seen the old wording, so the change is recorded.
   */
  @Transactional
  public TaskResponse reword(UUID userId, UUID taskId, String description) {
    var place = findPlace(userId, taskId);
    var runningMeetingId = meetings.lockRunningMeetingOf(userId, place.seriesId());
    var task = tasks.findByIdAndDeletedAtIsNull(taskId).orElseThrow(TaskNotFoundException::new);
    var old = task.getDescription();
    var reworded = description.strip();
    if (!old.equals(reworded)) {
      task.reword(reworded);
      if (!runningMeetingId.equals(task.getMeetingId())) {
        changes.save(TaskChange.ofDescription(task, userId, runningMeetingId, old, reworded));
      }
    }
    return toResponse(task);
  }

  /** The organizer only, while any meeting of the task's series runs. */
  @Transactional
  public void delete(UUID userId, UUID taskId) {
    meetings.lockRunningMeetingOf(userId, findPlace(userId, taskId).seriesId());
    tasks.findByIdAndDeletedAtIsNull(taskId).orElseThrow(TaskNotFoundException::new).delete(userId);
  }

  private TaskResponse create(
      UUID userId, TaskOrigin origin, String description, UUID ownerId, LocalDate deadline) {
    if (!origin.participantIds().contains(ownerId)) {
      throw new OwnerNotParticipantException();
    }
    var task = tasks.save(new Task(origin, description.strip(), ownerId, deadline, userId));
    changes.save(TaskChange.creationOf(task, userId));
    return toResponse(task);
  }

  /** The task, if the user is a member of its team; otherwise 404, as if it didn't exist. */
  private Task findVisible(UUID userId, UUID taskId) {
    return tasks
        .findByIdAndDeletedAtIsNull(taskId)
        .filter(found -> teamMembers.isMember(userId, found.getTeamId()))
        .orElseThrow(TaskNotFoundException::new);
  }

  /**
   * Where the task belongs, read before taking a lock, without loading the task itself: it is
   * loaded once the meeting is locked, so one deleted meanwhile answers 404. Outsiders get the same
   * 404 as for an unknown task.
   */
  private TaskRepository.TaskPlace findPlace(UUID userId, UUID taskId) {
    return tasks
        .findPlaceByIdAndDeletedAtIsNull(taskId)
        .filter(found -> teamMembers.isMember(userId, found.teamId()))
        .orElseThrow(TaskNotFoundException::new);
  }

  private void requireOrganizer(UUID userId, Task task) {
    if (!meetings.organizes(userId, task.getSeriesId())) {
      throw new NotAllowedToChangeTaskException();
    }
  }

  /** The meeting a change is made in: the series' running one, which may be a later meeting. */
  private @Nullable UUID runningMeeting(Task task) {
    return meetings.runningMeetingOf(task.getSeriesId()).orElse(null);
  }

  private TaskResponse toResponse(Task task) {
    return toResponse(task, users.namesOf(List.of(task.getOwnerId())));
  }

  private static TaskResponse toResponse(Task task, Map<UUID, String> names) {
    return new TaskResponse(
        task.getId(),
        task.getTeamId(),
        task.getSeriesId(),
        task.getMeetingId(),
        task.getAgendaItemId(),
        task.getDecisionId(),
        task.getDescription(),
        task.getOwnerId(),
        names.getOrDefault(task.getOwnerId(), ""),
        task.getDeadline(),
        task.getStatus());
  }

  private static TaskChangeResponse toResponse(TaskChange change, Map<UUID, String> names) {
    return new TaskChangeResponse(
        change.getChangedAt(),
        names.getOrDefault(change.getChangedBy(), ""),
        change.getMeetingId() != null,
        change.getOldStatus(),
        change.getNewStatus(),
        nameOf(change.getOldOwnerId(), names),
        nameOf(change.getNewOwnerId(), names),
        change.getOldDeadline(),
        change.getNewDeadline(),
        change.getOldDescription(),
        change.getNewDescription());
  }

  private static @Nullable String nameOf(@Nullable UUID userId, Map<UUID, String> names) {
    return userId == null ? null : names.getOrDefault(userId, "");
  }
}
