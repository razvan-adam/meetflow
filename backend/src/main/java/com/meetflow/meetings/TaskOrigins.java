package com.meetflow.meetings;

import com.meetflow.meetings.service.AgendaService;
import com.meetflow.meetings.service.DecisionService;
import com.meetflow.meetings.service.MeetingService;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the tasks module asks about meetings. Tasks are created by the series organizer while a
 * meeting runs, so the same rules as for decisions apply: 404 for outsiders, 403 for other members
 * and 409 before the meeting starts and after it closes.
 *
 * <p>Finding an origin locks the meeting's row until the caller's transaction ends, so the caller
 * must have one: otherwise a task could slip in after closing.
 */
@Service
public class TaskOrigins {

  private final AgendaService agenda;
  private final DecisionService decisions;
  private final MeetingService meetings;

  TaskOrigins(AgendaService agenda, DecisionService decisions, MeetingService meetings) {
    this.agenda = agenda;
    this.decisions = decisions;
    this.meetings = meetings;
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

  /** Throws a 404 error response if the user isn't a member of the meeting's team. */
  @Transactional(readOnly = true)
  public void requireVisibleMeeting(UUID userId, UUID meetingId) {
    meetings.findVisible(userId, meetingId);
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
