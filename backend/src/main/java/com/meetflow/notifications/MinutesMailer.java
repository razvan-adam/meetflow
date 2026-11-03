package com.meetflow.notifications;

import com.meetflow.notifications.config.EmailProperties;
import com.meetflow.notifications.domain.EmailDelivery;
import com.meetflow.notifications.repository.EmailDeliveryRepository;
import com.meetflow.notifications.service.EmailMessage;
import com.meetflow.notifications.service.MeetingEmails;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Sends the minutes email (E2) to a meeting's participants. */
@Service
public class MinutesMailer {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("EEE d MMM uuuu", Locale.ENGLISH);

  private final MeetingEmails emails;
  private final EmailDeliveryRepository deliveries;
  private final EmailProperties properties;

  MinutesMailer(
      MeetingEmails emails, EmailDeliveryRepository deliveries, EmailProperties properties) {
    this.emails = emails;
    this.deliveries = deliveries;
    this.properties = properties;
  }

  /**
   * Tries every recipient who hasn't had it yet.
   *
   * @throws IllegalStateException if it couldn't be sent to at least one of them; see {@link
   *     MeetingEmails#sendOnce}
   */
  public void send(MinutesEmail minutes) {
    var subject =
        "Minutes: %s, %s".formatted(minutes.seriesName(), DATE.format(minutes.meetingDate()));
    var text = text(minutes);
    emails.sendOnce(
        EmailDelivery.Kind.MINUTES,
        minutes.meetingId(),
        minutes.recipients().stream()
            .map(recipient -> new EmailMessage(recipient, subject, text))
            .toList());
  }

  /** How many of these recipients have been sent the meeting's minutes so far. */
  public long sentTo(UUID meetingId, Collection<String> recipients) {
    return recipients.isEmpty()
        ? 0
        : deliveries.countByKindAndReferenceIdAndRecipientEmailIn(
            EmailDelivery.Kind.MINUTES, meetingId, recipients);
  }

  private String text(MinutesEmail minutes) {
    var sections = new ArrayList<String>();
    sections.add(
        "Minutes of %s, %s.".formatted(minutes.seriesName(), DATE.format(minutes.meetingDate())));
    addSection(sections, "Decisions", minutes.decisions());
    addSection(sections, "New tasks", minutes.newTasks().stream().map(this::newTaskLine).toList());
    addSection(
        sections,
        "Open commitments",
        minutes.commitments().stream().map(this::commitmentLine).toList());
    addSection(sections, "Removed tasks", minutes.removed());
    addSection(sections, "Carried over (not covered)", minutes.carriedOver());
    sections.add(
        "Open the full minutes:\n%s/meetings/%s/minutes"
            .formatted(properties.appUrl(), minutes.meetingId()));
    return String.join("\n\n", sections) + "\n";
  }

  private static void addSection(List<String> sections, String title, List<String> lines) {
    if (!lines.isEmpty()) {
      sections.add(title + "\n- " + String.join("\n- ", lines));
    }
  }

  private String newTaskLine(MinutesEmail.Task task) {
    return "%s · %s · due %s"
        .formatted(task.description(), task.ownerName(), date(task.deadline()));
  }

  private String commitmentLine(MinutesEmail.Task task) {
    return "%s%s · %s · due %s · %s"
        .formatted(
            task.overdue() ? "Overdue: " : "",
            task.description(),
            task.ownerName(),
            date(task.deadline()),
            task.status());
  }

  private static String date(LocalDate date) {
    return DATE.format(date);
  }
}
