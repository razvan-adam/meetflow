package com.meetflow.minutes.service;

import com.meetflow.identity.UserDirectory;
import com.meetflow.meetings.MeetingClosedEvent;
import com.meetflow.meetings.MeetingRecord;
import com.meetflow.meetings.MeetingRecords;
import com.meetflow.minutes.domain.Minutes;
import com.meetflow.minutes.dto.MinutesContent;
import com.meetflow.minutes.dto.MinutesResponse;
import com.meetflow.minutes.exception.MinutesNotFoundException;
import com.meetflow.minutes.repository.MinutesRepository;
import com.meetflow.notifications.MinutesEmail;
import com.meetflow.notifications.MinutesMailer;
import com.meetflow.tasks.TaskRecords;
import com.meetflow.teams.TeamMembers;
import java.text.Collator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minutes (MF-22): written once when a meeting closes, read by the members of its team, and never
 * edited. There is no method that changes them.
 */
@Service
public class MinutesService {

  private final MinutesRepository minutes;
  private final MeetingRecords meetings;
  private final TaskRecords tasks;
  private final TeamMembers teamMembers;
  private final UserDirectory users;
  private final JsonMapper json;
  private final MinutesMailer mailer;

  MinutesService(
      MinutesRepository minutes,
      MeetingRecords meetings,
      TaskRecords tasks,
      TeamMembers teamMembers,
      UserDirectory users,
      JsonMapper json,
      MinutesMailer mailer) {
    this.minutes = minutes;
    this.meetings = meetings;
    this.tasks = tasks;
    this.teamMembers = teamMembers;
    this.users = users;
    this.json = json;
    this.mailer = mailer;
  }

  /** Called in the transaction that closes the meeting, so it can't close without its minutes. */
  @Transactional
  public void generate(MeetingClosedEvent event) {
    var meeting = meetings.ofClosed(event.meetingId());
    var zone = ZoneId.of(event.timeZone());
    var meetingDate = meeting.startedAt().atZone(zone).toLocalDate();
    var content =
        new MinutesContent(
            meeting.seriesId(),
            meeting.seriesName(),
            meetingDate,
            meeting.scheduledAt().atZone(zone).toLocalDate(),
            meeting.closedAt(),
            meeting.closedBy(),
            List.copyOf(meeting.participantIds()),
            toContent(tasks.carriedOverToClosing(meeting.id()), meetingDate),
            meeting.agenda().stream().map(MinutesService::toContent).toList(),
            toContent(tasks.createdInClosing(meeting.id()), meetingDate));
    minutes.save(new Minutes(meeting.teamId(), meeting.id(), json.writeValueAsString(content)));
  }

  /** The minutes, if the meeting has them and the user is a member of its team; otherwise 404. */
  @Transactional(readOnly = true)
  public MinutesResponse get(UUID userId, UUID meetingId) {
    var found =
        minutes
            .findByMeetingId(meetingId)
            .filter(stored -> teamMembers.isMember(userId, stored.getTeamId()))
            .orElseThrow(MinutesNotFoundException::new);
    var content = read(found);
    var names = namesIn(content);
    var current =
        tasks.currentlyOf(
            Stream.concat(content.commitments().stream(), content.newTasks().stream())
                .map(MinutesContent.Task::id)
                .toList());
    var recipients = users.emailsOf(content.participantIds()).values();
    var byName = Collator.getInstance(Locale.ENGLISH);
    var participants =
        content.participantIds().stream()
            .map(
                id ->
                    new MinutesResponse.Participant(
                        id, names.getOrDefault(id, ""), id.equals(content.closedBy())))
            .sorted(Comparator.comparing(MinutesResponse.Participant::name, byName))
            .toList();
    return new MinutesResponse(
        meetingId,
        found.getTeamId(),
        content.seriesId(),
        content.seriesName(),
        content.meetingDate(),
        content.scheduledDate(),
        content.closedAt(),
        names.getOrDefault(content.closedBy(), ""),
        participants,
        toResponse(content.commitments(), names, current),
        content.agenda(),
        toResponse(content.newTasks(), names, current),
        recipients.size(),
        mailer.sentTo(meetingId, recipients));
  }

  /** The summary to email to the meeting's participants. */
  @Transactional(readOnly = true)
  public MinutesEmail emailFor(UUID meetingId) {
    var content =
        read(minutes.findByMeetingId(meetingId).orElseThrow(MinutesNotFoundException::new));
    var names = namesIn(content);
    return new MinutesEmail(
        meetingId,
        content.seriesName(),
        content.meetingDate(),
        content.agenda().stream().flatMap(item -> item.decisions().stream()).toList(),
        content.newTasks().stream().map(task -> toEmail(task, names)).toList(),
        content.commitments().stream()
            .filter(task -> !task.removed() && !"DONE".equals(task.statusAtClose()))
            .map(task -> toEmail(task, names))
            .toList(),
        content.commitments().stream()
            .filter(MinutesContent.Task::removed)
            .map(MinutesContent.Task::description)
            .toList(),
        content.agenda().stream()
            .filter(item -> !item.covered())
            .map(MinutesContent.Item::title)
            .toList(),
        List.copyOf(users.emailsOf(content.participantIds()).values()));
  }

  private MinutesContent read(Minutes stored) {
    return json.readValue(stored.getContent(), MinutesContent.class);
  }

  private Map<UUID, String> namesIn(MinutesContent content) {
    return users.namesOf(
        Stream.of(
                content.participantIds().stream(),
                content.commitments().stream().map(MinutesContent.Task::ownerId),
                content.newTasks().stream().map(MinutesContent.Task::ownerId),
                Stream.of(content.closedBy()))
            .flatMap(ids -> ids)
            .distinct()
            .toList());
  }

  private static List<MinutesContent.Task> toContent(
      List<TaskRecords.ClosingTask> found, LocalDate meetingDate) {
    return found.stream()
        .map(
            task ->
                new MinutesContent.Task(
                    task.id(),
                    task.description(),
                    task.ownerId(),
                    task.deadline(),
                    task.statusBefore(),
                    task.statusAtClose(),
                    daysOverdue(task, meetingDate),
                    task.removed()))
        .toList();
  }

  private static MinutesContent.Item toContent(MeetingRecord.Item item) {
    return new MinutesContent.Item(
        item.title(), item.notes(), item.covered(), item.carriedOver(), item.decisions());
  }

  /**
   * Overdue is any task that isn't done and whose deadline has passed; a removed one is neither.
   */
  private static long daysOverdue(TaskRecords.ClosingTask task, LocalDate meetingDate) {
    return task.done() || task.removed() || !task.deadline().isBefore(meetingDate)
        ? 0
        : ChronoUnit.DAYS.between(task.deadline(), meetingDate);
  }

  private static List<MinutesResponse.Task> toResponse(
      List<MinutesContent.Task> found,
      Map<UUID, String> names,
      Map<UUID, TaskRecords.CurrentTask> current) {
    return found.stream()
        .map(
            task -> {
              var now = current.get(task.id());
              return new MinutesResponse.Task(
                  task.id(),
                  task.description(),
                  names.getOrDefault(task.ownerId(), ""),
                  task.deadline(),
                  task.statusBefore(),
                  task.statusAtClose(),
                  task.daysOverdue(),
                  task.removed(),
                  !task.removed() && now != null && now.removed(),
                  now == null ? task.statusAtClose() : now.status());
            })
        .toList();
  }

  private static MinutesEmail.Task toEmail(MinutesContent.Task task, Map<UUID, String> names) {
    return new MinutesEmail.Task(
        task.description(),
        names.getOrDefault(task.ownerId(), ""),
        task.deadline(),
        task.statusAtClose().toLowerCase(Locale.ROOT).replace('_', ' '),
        task.daysOverdue() > 0);
  }
}
