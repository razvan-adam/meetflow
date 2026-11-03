package com.meetflow.tasks;

import com.meetflow.tasks.domain.Task;
import com.meetflow.tasks.domain.TaskChange;
import com.meetflow.tasks.domain.TaskStatus;
import com.meetflow.tasks.repository.TaskChangeRepository;
import com.meetflow.tasks.repository.TaskRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the minutes module reads about tasks. Callers check who may see them. */
@Service
public class TaskRecords {

  /**
   * A task as it stood when a meeting closed.
   *
   * @param statusBefore its status before the meeting's own changes
   * @param removed whether it was removed during that meeting
   */
  public record ClosingTask(
      UUID id,
      String description,
      UUID ownerId,
      LocalDate deadline,
      String statusBefore,
      String statusAtClose,
      boolean done,
      boolean removed) {}

  /** A task as it stands now, which can differ from a closed meeting's record of it. */
  public record CurrentTask(String status, boolean removed) {}

  private final TaskRepository tasks;
  private final TaskChangeRepository changes;

  TaskRecords(TaskRepository tasks, TaskChangeRepository changes) {
    this.tasks = tasks;
    this.changes = changes;
  }

  /**
   * The tasks carried over to a meeting that has just closed, soonest deadline first, with those
   * removed during it. Must run after the status at closing is recorded on them.
   */
  @Transactional(readOnly = true)
  public List<ClosingTask> carriedOverToClosing(UUID meetingId) {
    var atClose =
        tasks.findStatusesAtClose(meetingId).stream()
            .collect(
                Collectors.toMap(
                    TaskRepository.StatusAtClose::getTaskId,
                    TaskRepository.StatusAtClose::getStatus));
    var before = new HashMap<UUID, TaskStatus>();
    for (TaskChange change :
        changes.findByMeetingIdAndOldStatusIsNotNullOrderByChangedAt(meetingId)) {
      before.putIfAbsent(change.getTaskId(), change.getOldStatus());
    }
    return tasks.findCarriedOverTo(meetingId, false).stream()
        .map(
            task -> {
              var status =
                  TaskStatus.valueOf(atClose.getOrDefault(task.getId(), task.getStatus().name()));
              return closingTask(task, before.getOrDefault(task.getId(), status), status);
            })
        .toList();
  }

  /** The tasks created in a meeting that has just closed, oldest first. */
  @Transactional(readOnly = true)
  public List<ClosingTask> createdInClosing(UUID meetingId) {
    return tasks.findRecordedInMeeting(meetingId).stream()
        .map(task -> closingTask(task, task.getStatus(), task.getStatus()))
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<UUID, CurrentTask> currentlyOf(Collection<UUID> taskIds) {
    return tasks.findAllById(taskIds).stream()
        .collect(
            Collectors.toMap(
                Task::getId,
                task -> new CurrentTask(task.getStatus().name(), task.getDeletedAt() != null)));
  }

  private static ClosingTask closingTask(Task task, TaskStatus before, TaskStatus atClose) {
    return new ClosingTask(
        task.getId(),
        task.getDescription(),
        task.getOwnerId(),
        task.getDeadline(),
        before.name(),
        atClose.name(),
        atClose == TaskStatus.DONE,
        task.getDeletedAt() != null);
  }
}
