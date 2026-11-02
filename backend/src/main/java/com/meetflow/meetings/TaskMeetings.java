package com.meetflow.meetings;

import com.meetflow.meetings.service.AgendaService;
import com.meetflow.meetings.service.DecisionService;
import com.meetflow.meetings.service.MeetingSeriesService;
import com.meetflow.meetings.service.MeetingService;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the tasks module asks about meetings. Tasks are created, reworded and deleted by the series
 * organizer while the task's meeting runs, so the same rules as for decisions apply: 404 for
 * outsiders, 403 for other members and 409 before the meeting starts and after it closes.
 *
 * <p>The methods for those changes lock the meeting's row until the caller's transaction ends, so
 * the caller must have one: otherwise a change could slip in after closing.
 */
@Service
public class TaskMeetings {

  private final AgendaService agenda;
  private final DecisionService decisions;
  private final MeetingService meetings;
  private final MeetingSeriesService series;

  TaskMeetings(
      AgendaService agenda,
      DecisionService decisions,
      MeetingService meetings,
      MeetingSeriesService series) {
    this.agenda = agenda;
    this.decisions = decisions;
    this.meetings = meetings;
    this.series = series;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public TaskOrigin ofAgendaItem(UUID userId, UUID itemId) {
    var item = agenda.findInRunningMeeting(userId, itemId);
    return origin(userId, item.getMeetingId(), item.getId(), null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public TaskOrigin ofDecision(UUID userId, UUID decisionId) {
    var decision = decisions.findInRunningMeeting(userId, decisionId);
    return origin(userId, decision.getMeetingId(), decision.getAgendaItemId(), decision.getId());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockRunningMeeting(UUID userId, UUID meetingId) {
    meetings.lockRunningMeeting(userId, meetingId);
  }

  /** Throws a 404 error response if the user isn't a member of the meeting's team. */
  @Transactional(readOnly = true)
  public void requireVisibleMeeting(UUID userId, UUID meetingId) {
    meetings.findVisible(userId, meetingId);
  }

  /** Whether the user organizes the series right now, which can change after the task's meeting. */
  @Transactional(readOnly = true)
  public boolean organizes(UUID userId, UUID seriesId) {
    return series.findVisible(userId, seriesId).getOrganizerId().equals(userId);
  }

  /** The series' meeting that is running right now, if any. */
  @Transactional(readOnly = true)
  public Optional<UUID> runningMeetingOf(UUID seriesId) {
    return meetings.runningMeetingOf(seriesId);
  }

  private TaskOrigin origin(
      UUID userId, UUID meetingId, UUID agendaItemId, @Nullable UUID decisionId) {
    var meeting = meetings.findVisible(userId, meetingId);
    return new TaskOrigin(
        meeting.getTeamId(),
        meeting.getSeriesId(),
        meeting.getId(),
        agendaItemId,
        decisionId,
        meeting.getParticipantIds());
  }
}
