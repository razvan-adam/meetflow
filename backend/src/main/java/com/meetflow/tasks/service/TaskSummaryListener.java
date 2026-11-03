package com.meetflow.tasks.service;

import com.meetflow.identity.UserDirectory;
import com.meetflow.meetings.MeetingClosedEvent;
import com.meetflow.meetings.MeetingRecords;
import com.meetflow.notifications.TaskSummaryEmail;
import com.meetflow.notifications.TaskSummaryMailer;
import com.meetflow.tasks.domain.Task;
import com.meetflow.tasks.domain.TaskStatus;
import com.meetflow.tasks.repository.TaskRepository;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails each owner their tasks from a closed meeting (MF-20), once it commits. A failed email is
 * retried through the event publication table, as with the minutes (see {@code MinutesListener}).
 *
 * <p>It reads the tasks as they stand now, not as they stood at closing. Tasks can change before a
 * retry, and this email is there to be acted on: nobody should be told about a task that has since
 * been done, removed or given to someone else.
 */
@Component
class TaskSummaryListener {

  private final TaskRepository tasks;
  private final MeetingRecords meetings;
  private final UserDirectory users;
  private final TaskSummaryMailer mailer;

  TaskSummaryListener(
      TaskRepository tasks,
      MeetingRecords meetings,
      UserDirectory users,
      TaskSummaryMailer mailer) {
    this.tasks = tasks;
    this.meetings = meetings;
    this.users = users;
    this.mailer = mailer;
  }

  @Async
  @TransactionalEventListener
  void email(MeetingClosedEvent event) {
    var byOwner =
        tasks.findRecordedInMeeting(event.meetingId()).stream()
            .filter(task -> task.getDeletedAt() == null && task.getStatus() != TaskStatus.DONE)
            .collect(
                Collectors.groupingBy(Task::getOwnerId, LinkedHashMap::new, Collectors.toList()));
    if (byOwner.isEmpty()) {
      return;
    }
    var meeting = meetings.ofClosed(event.meetingId());
    var emails = users.emailsOf(byOwner.keySet());
    var owners = new ArrayList<TaskSummaryEmail.Owner>();
    byOwner.forEach(
        (ownerId, owned) -> {
          var email = emails.get(ownerId);
          // A deleted account has no email left to send to.
          if (email != null) {
            owners.add(toOwner(email, owned));
          }
        });
    mailer.send(
        new TaskSummaryEmail(
            meeting.id(),
            meeting.seriesName(),
            meeting.startedAt().atZone(ZoneId.of(event.timeZone())).toLocalDate(),
            owners));
  }

  private static TaskSummaryEmail.Owner toOwner(String email, List<Task> owned) {
    return new TaskSummaryEmail.Owner(
        email,
        owned.stream()
            .map(
                task ->
                    new TaskSummaryEmail.Task(
                        task.getId(), task.getDescription(), task.getDeadline()))
            .toList());
  }
}
