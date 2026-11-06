package com.meetflow.tasks.service;

import com.meetflow.meetings.TaskMeetings;
import com.meetflow.tasks.domain.Task;
import com.meetflow.tasks.domain.TaskChange;
import com.meetflow.tasks.exception.OwnerNotMemberException;
import com.meetflow.tasks.exception.UnresolvedTasksException;
import com.meetflow.tasks.repository.TaskChangeRepository;
import com.meetflow.tasks.repository.TaskRepository;
import com.meetflow.teams.MemberRemovalEvent;
import com.meetflow.teams.TeamMembers;
import java.util.Comparator;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Hands over a removed member's open tasks (MF-11), inside the transaction that removes them, so no
 * task is left with an owner outside the team. Each change is recorded in the task's history as
 * made by the admin, in the series' running meeting if there is one. That meeting is locked, as
 * when its organizer changes a task, so the change can't land in it after it has closed.
 */
@Component
class TaskHandover {

  private final TaskRepository tasks;
  private final TaskChangeRepository changes;
  private final TaskMeetings meetings;
  private final TeamMembers teamMembers;

  TaskHandover(
      TaskRepository tasks,
      TaskChangeRepository changes,
      TaskMeetings meetings,
      TeamMembers teamMembers) {
    this.tasks = tasks;
    this.changes = changes;
    this.meetings = meetings;
    this.teamMembers = teamMembers;
  }

  @EventListener
  void on(MemberRemovalEvent event) {
    // Series in a fixed order, so two handovers can't lock them in opposite orders.
    var open =
        tasks.findOpenInTeam(event.teamId()).stream()
            .filter(task -> task.getOwnerId().equals(event.memberId()))
            .sorted(Comparator.comparing(Task::getSeriesId).thenComparing(Task::getId))
            .toList();
    var handedOver = new HashSet<>(event.taskOwners().keySet());
    handedOver.addAll(event.tasksToRemove());
    if (!handedOver.equals(open.stream().map(Task::getId).collect(Collectors.toSet()))) {
      throw new UnresolvedTasksException();
    }
    var newOwners = event.taskOwners().values().stream().distinct().toList();
    if (!teamMembers.areMembers(newOwners, event.teamId())) {
      throw new OwnerNotMemberException();
    }
    for (var task : open) {
      // Waits for a meeting being scheduled or started, which reads the series' open tasks.
      meetings.lockSeries(task.getSeriesId());
      var runningMeetingId = meetings.lockRunningMeetingIfAny(task.getSeriesId()).orElse(null);
      UUID newOwner = event.taskOwners().get(task.getId());
      if (newOwner != null) {
        task.changeOwner(newOwner);
        changes.save(
            TaskChange.ofOwner(
                task, event.removedBy(), runningMeetingId, event.memberId(), newOwner));
      } else {
        remove(task, event.removedBy(), runningMeetingId);
      }
    }
  }

  /**
   * During a running meeting it is removed in that meeting, as an organizer would. Otherwise it
   * also leaves the agenda of the series' next meeting, as a task never carries over once removed.
   */
  private void remove(Task task, UUID removedBy, @Nullable UUID running) {
    task.delete(removedBy, running);
    changes.save(TaskChange.ofDeletion(task, removedBy, running));
    if (running == null) {
      meetings
          .scheduledMeetingOf(task.getSeriesId())
          .ifPresent(meetingId -> tasks.removeFromReview(meetingId, task.getId()));
    }
  }
}
