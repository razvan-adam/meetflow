package com.meetflow.notifications;

import com.meetflow.notifications.config.EmailProperties;
import com.meetflow.notifications.domain.EmailDelivery;
import com.meetflow.notifications.repository.EmailDeliveryRepository;
import com.meetflow.notifications.service.EmailMessage;
import com.meetflow.notifications.service.MeetingEmails;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Sends the task email (E3): one per owner per meeting, and no reminders after it. */
@Service
public class TaskSummaryMailer {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("EEE d MMM uuuu", Locale.ENGLISH);

  private final MeetingEmails emails;
  private final EmailDeliveryRepository deliveries;
  private final EmailProperties properties;

  TaskSummaryMailer(
      MeetingEmails emails, EmailDeliveryRepository deliveries, EmailProperties properties) {
    this.emails = emails;
    this.deliveries = deliveries;
    this.properties = properties;
  }

  /** How many owners have been sent their tasks from the meeting so far. */
  public long sentFor(UUID meetingId) {
    return deliveries.countByKindAndReferenceId(EmailDelivery.Kind.TASK_SUMMARY, meetingId);
  }

  /**
   * Tries every owner who hasn't had it yet.
   *
   * @throws IllegalStateException if it couldn't be sent to at least one of them; see {@link
   *     MeetingEmails#sendOnce}
   */
  public void send(TaskSummaryEmail summary) {
    var subject =
        "Your tasks from %s, %s"
            .formatted(summary.seriesName(), DATE.format(summary.meetingDate()));
    emails.sendOnce(
        EmailDelivery.Kind.TASK_SUMMARY,
        summary.meetingId(),
        summary.owners().stream()
            .map(owner -> new EmailMessage(owner.email(), subject, text(owner)))
            .toList());
  }

  private String text(TaskSummaryEmail.Owner owner) {
    var tasks = owner.tasks().stream().map(this::taskLine).collect(Collectors.joining("\n"));
    return """
        These are your tasks from the meeting:

        %s

        They'll come back at the next meeting until they're done. Update them any time from My \
        tasks:
        %s/

        This is the only email about these tasks. MeetFlow doesn't send deadline reminders.
        """
        .formatted(tasks, properties.appUrl());
  }

  private String taskLine(TaskSummaryEmail.Task task) {
    return "- %s · due %s\n  %s/tasks/%s"
        .formatted(
            task.description(), DATE.format(task.deadline()), properties.appUrl(), task.id());
  }
}
