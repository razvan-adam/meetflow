package com.meetflow.tasks.service;

import static java.util.Objects.requireNonNull;

import com.meetflow.identity.UserDirectory;
import com.meetflow.meetings.TaskMeetings;
import com.meetflow.meetings.TaskOrigin;
import com.meetflow.notifications.TaskSummaryMailer;
import com.meetflow.tasks.domain.Task;
import com.meetflow.tasks.domain.TaskChange;
import com.meetflow.tasks.domain.TaskStatus;
import com.meetflow.tasks.dto.OpenTaskResponse;
import com.meetflow.tasks.dto.PendingTaskResponse;
import com.meetflow.tasks.dto.TaskChangeResponse;
import com.meetflow.tasks.dto.TaskEmailsResponse;
import com.meetflow.tasks.dto.TaskResponse;
import com.meetflow.tasks.exception.NotAllowedToChangeTaskException;
import com.meetflow.tasks.exception.OwnerNotMemberException;
import com.meetflow.tasks.exception.OwnerNotParticipantException;
import com.meetflow.tasks.exception.TaskNotFoundException;
import com.meetflow.tasks.repository.TaskChangeRepository;
import com.meetflow.tasks.repository.TaskRepository;
import com.meetflow.teams.TeamMembers;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
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
 * reword and delete it while any meeting of its series runs, and create it while its own runs. A
 * task deleted after its own meeting can still be read, as part of that meeting's record.
 */
@Service
public class TaskService {

  private final TaskRepository tasks;
  private final TaskChangeRepository changes;
  private final TaskMeetings meetings;
  private final TeamMembers teamMembers;
  private final UserDirectory users;
  private final TaskSummaryMailer mailer;

  TaskService(
      TaskRepository tasks,
      TaskChangeRepository changes,
      TaskMeetings meetings,
      TeamMembers teamMembers,
      UserDirectory users,
      TaskSummaryMailer mailer) {
    this.tasks = tasks;
    this.changes = changes;
    this.meetings = meetings;
    this.teamMembers = teamMembers;
    this.users = users;
    this.mailer = mailer;
  }

  /** How many owners have been emailed their tasks from the meeting so far (MF-20). */
  @Transactional(readOnly = true)
  public TaskEmailsResponse taskEmails(UUID userId, UUID meetingId) {
    meetings.requireVisibleMeeting(userId, meetingId);
    return new TaskEmailsResponse(mailer.sentFor(meetingId));
  }

  /**
   * The user's dashboard (MF-21): the tasks they own that aren't done or removed, soonest deadline
   * first, across the teams they belong to. Membership is checked here too, so a task can't show a
   * team the user has left.
   */
  @Transactional(readOnly = true)
  public List<PendingTaskResponse> listPending(UUID userId) {
    var teamNames = teamMembers.teamNamesOf(userId);
    var found =
        tasks.findPendingOwnedBy(userId).stream()
            .filter(task -> teamNames.containsKey(task.getTeamId()))
            .toList();
    var seriesNames =
        meetings.seriesNamesOf(found.stream().map(Task::getSeriesId).distinct().toList());
    var meetingIds = found.stream().map(Task::getMeetingId).distinct().toList();
    var meetingTimes = meetings.scheduledTimesOf(meetingIds);
    var closedMeetings = meetings.closedAmong(meetingIds);
    return found.stream()
        .map(
            task ->
                new PendingTaskResponse(
                    task.getId(),
                    task.getDescription(),
                    task.getDeadline(),
                    task.getStatus(),
                    task.getTeamId(),
                    requireNonNull(teamNames.get(task.getTeamId())),
                    task.getSeriesId(),
                    requireNonNull(seriesNames.get(task.getSeriesId())),
                    task.getMeetingId(),
                    requireNonNull(meetingTimes.get(task.getMeetingId())),
                    closedMeetings.contains(task.getMeetingId())))
        .toList();
  }

  /**
   * The team's tasks that aren't done or removed, soonest deadline first, for the members page
   * (MF-11). Any member of the team can see them, as they can each task.
   */
  @Transactional(readOnly = true)
  public List<OpenTaskResponse> listOpenInTeam(UUID userId, UUID teamId) {
    teamMembers.requireMember(userId, teamId);
    return tasks.findOpenInTeam(teamId).stream()
        .map(
            task ->
                new OpenTaskResponse(
                    task.getId(),
                    task.getDescription(),
                    task.getOwnerId(),
                    task.getDeadline(),
                    task.getStatus()))
        .toList();
  }

  /** The tasks created in the meeting, oldest first. */
  @Transactional(readOnly = true)
  public List<TaskResponse> listForMeeting(UUID userId, UUID meetingId) {
    var closedAt = meetings.closedAt(userId, meetingId);
    return asRecorded(tasks.findRecordedInMeeting(meetingId), closedAt);
  }

  /**
   * The tasks carried over to the meeting, soonest deadline first. Deleted ones are included if
   * they were deleted during this meeting or the meeting is closed, as part of its record; the page
   * lists them apart. Other meetings that aren't closed leave them out.
   */
  @Transactional(readOnly = true)
  public List<TaskResponse> listCarriedOver(UUID userId, UUID meetingId) {
    var closedAt = meetings.closedAt(userId, meetingId);
    return asRecorded(tasks.findCarriedOverTo(meetingId, closedAt != null), closedAt);
  }

  /**
   * A closed meeting is a record, so its lists also say how a task reworded since then read when it
   * closed: that is the old wording of its first rewording after that moment.
   */
  private List<TaskResponse> asRecorded(List<Task> found, @Nullable Instant closedAt) {
    if (closedAt == null || found.isEmpty()) {
      return toResponses(found, Map.of());
    }
    var wordings = new HashMap<UUID, String>();
    for (var change :
        changes.findRewordingsSince(found.stream().map(Task::getId).toList(), closedAt)) {
      wordings.putIfAbsent(change.getTaskId(), requireNonNull(change.getOldDescription()));
    }
    return toResponses(found, wordings);
  }

  @Transactional(readOnly = true)
  public TaskResponse get(UUID userId, UUID taskId) {
    return toResponse(findRecorded(userId, taskId));
  }

  /** The task's history, latest first. */
  @Transactional(readOnly = true)
  public List<TaskChangeResponse> history(UUID userId, UUID taskId) {
    findRecorded(userId, taskId);
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
      if (old == TaskStatus.DONE || status == TaskStatus.DONE) {
        updateNextAgenda(task, status);
      }
      task.changeStatus(status);
      changes.save(TaskChange.ofStatus(task, userId, runningMeeting(task), old, status));
    }
    return toResponse(task);
  }

  /**
   * A task marked done leaves the agenda of a meeting that hasn't started; during a meeting it
   * stays on the list, as reviewed. A reopened task joins the series' open meeting, unless that is
   * its own meeting. The lock waits for a meeting being scheduled, which reads the open tasks, so
   * the task can't miss it.
   */
  private void updateNextAgenda(Task task, TaskStatus status) {
    meetings.lockSeries(task.getSeriesId());
    if (status == TaskStatus.DONE) {
      meetings
          .scheduledMeetingOf(task.getSeriesId())
          .ifPresent(meetingId -> tasks.removeFromReview(meetingId, task.getId()));
    } else {
      meetings
          .openMeetingOf(task.getSeriesId())
          .filter(meetingId -> !meetingId.equals(task.getMeetingId()))
          .ifPresent(meetingId -> tasks.reviewTask(meetingId, task.getId(), task.getSeriesId()));
    }
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
    var runningMeetingId =
        meetings.lockRunningMeetingOf(userId, findPlace(userId, taskId).seriesId());
    var task = tasks.findByIdAndDeletedAtIsNull(taskId).orElseThrow(TaskNotFoundException::new);
    task.delete(userId, runningMeetingId);
    changes.save(TaskChange.ofDeletion(task, userId, runningMeetingId));
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

  /**
   * For reading: the task, even if deleted after its own meeting, if the user is a member of its
   * team; otherwise 404, as if it didn't exist.
   */
  private Task findRecorded(UUID userId, UUID taskId) {
    return tasks
        .findRecordedById(taskId)
        .filter(found -> teamMembers.isMember(userId, found.getTeamId()))
        .orElseThrow(TaskNotFoundException::new);
  }

  /** For changing: the task, if not deleted and the user is a member of its team; otherwise 404. */
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
    return toResponses(List.of(task), Map.of()).getFirst();
  }

  private List<TaskResponse> toResponses(List<Task> found, Map<UUID, String> wordingsAtClose) {
    var names =
        users.namesOf(
            found.stream()
                .flatMap(task -> Stream.of(task.getOwnerId(), task.getDeletedBy()))
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    var meetingTimes =
        meetings.scheduledTimesOf(
            found.stream()
                .map(Task::getDeletedInMeetingId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    return found.stream()
        .map(task -> toResponse(task, wordingsAtClose.get(task.getId()), names, meetingTimes))
        .toList();
  }

  private static TaskResponse toResponse(
      Task task,
      @Nullable String descriptionAtClose,
      Map<UUID, String> names,
      Map<UUID, Instant> meetingTimes) {
    return new TaskResponse(
        task.getId(),
        task.getTeamId(),
        task.getSeriesId(),
        task.getMeetingId(),
        task.getAgendaItemId(),
        task.getDecisionId(),
        task.getDescription(),
        descriptionAtClose,
        task.getOwnerId(),
        names.getOrDefault(task.getOwnerId(), ""),
        task.getDeadline(),
        task.getStatus(),
        deletionOf(task, names, meetingTimes));
  }

  private static TaskResponse.@Nullable Deletion deletionOf(
      Task task, Map<UUID, String> names, Map<UUID, Instant> meetingTimes) {
    var at = task.getDeletedAt();
    if (at == null) {
      return null;
    }
    var meetingId = task.getDeletedInMeetingId();
    return new TaskResponse.Deletion(
        Objects.requireNonNullElse(nameOf(task.getDeletedBy(), names), ""),
        at,
        meetingId == null ? null : meetingTimes.get(meetingId));
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
        change.getNewDescription(),
        change.isDeleted());
  }

  private static @Nullable String nameOf(@Nullable UUID userId, Map<UUID, String> names) {
    return userId == null ? null : names.getOrDefault(userId, "");
  }
}
