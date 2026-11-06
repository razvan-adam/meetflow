package com.meetflow.meetings;

import com.meetflow.meetings.service.AgendaService;
import com.meetflow.meetings.service.DecisionService;
import com.meetflow.meetings.service.MeetingSeriesService;
import com.meetflow.meetings.service.MeetingService;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the tasks module asks about meetings. Tasks are created by the series organizer while the
 * task's meeting runs, and reworded and deleted while any meeting of its series runs, so the same
 * rules as for decisions apply: 404 for outsiders, 403 for other members and 409 when no such
 * meeting is running.
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

  /**
   * Where a task created from the item goes. Holds the team's membership as well as the meeting, so
   * the caller can check that the new owner is a member.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public TaskOrigin ofAgendaItem(UUID userId, UUID itemId) {
    var item = agenda.findInRunningMeetingToAssign(userId, itemId);
    return origin(userId, item.getMeetingId(), item.getId(), null);
  }

  /** Like {@link #ofAgendaItem}, for a task created from the decision. */
  @Transactional(propagation = Propagation.MANDATORY)
  public TaskOrigin ofDecision(UUID userId, UUID decisionId) {
    var decision = decisions.findInRunningMeetingToAssign(userId, decisionId);
    return origin(userId, decision.getMeetingId(), decision.getAgendaItemId(), decision.getId());
  }

  /** Locks the series' running meeting, whichever it is, and returns its ID. */
  @Transactional(propagation = Propagation.MANDATORY)
  public UUID lockRunningMeetingOf(UUID userId, UUID seriesId) {
    return meetings.lockRunningMeetingOf(userId, seriesId);
  }

  /**
   * The series' running meeting, if any, locked like {@link #lockRunningMeetingOf} but without
   * checking who asks: for changes made on the team's behalf. A meeting that closes meanwhile is
   * waited for, and then isn't running.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<UUID> lockRunningMeetingIfAny(UUID seriesId) {
    return meetings.lockRunningMeetingIfAny(seriesId);
  }

  /** Locks the series, so no meeting of it can be scheduled until the transaction ends. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockSeries(UUID seriesId) {
    series.lock(seriesId);
  }

  /** Throws a 404 error response if the user isn't a member of the meeting's team. */
  @Transactional(readOnly = true)
  public void requireVisibleMeeting(UUID userId, UUID meetingId) {
    meetings.findVisible(userId, meetingId);
  }

  /**
   * When the meeting closed, null if it hasn't; a 404 error response if the user isn't in its team.
   */
  @Transactional(readOnly = true)
  public @Nullable Instant closedAt(UUID userId, UUID meetingId) {
    return meetings.findVisible(userId, meetingId).getClosedAt();
  }

  /** Those of the meetings that are closed. */
  @Transactional(readOnly = true)
  public Set<UUID> closedAmong(Collection<UUID> meetingIds) {
    return meetings.closedAmong(meetingIds);
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

  /** The series' meeting that hasn't started yet, if any. */
  @Transactional(readOnly = true)
  public Optional<UUID> scheduledMeetingOf(UUID seriesId) {
    return meetings.scheduledMeetingOf(seriesId);
  }

  /** The series' meeting that isn't closed, if any: at most one, scheduled or running. */
  @Transactional(readOnly = true)
  public Optional<UUID> openMeetingOf(UUID seriesId) {
    return meetings.openMeetingOf(seriesId);
  }

  /** The names of the series, by ID. */
  @Transactional(readOnly = true)
  public Map<UUID, String> seriesNamesOf(Collection<UUID> seriesIds) {
    return series.namesOf(seriesIds);
  }

  /** When the meetings are scheduled, by ID. */
  @Transactional(readOnly = true)
  public Map<UUID, Instant> scheduledTimesOf(Collection<UUID> meetingIds) {
    return meetings.scheduledTimesOf(meetingIds);
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
