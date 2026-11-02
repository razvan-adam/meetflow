package com.meetflow.meetings.service;

import com.meetflow.meetings.domain.Decision;
import com.meetflow.meetings.dto.DecisionResponse;
import com.meetflow.meetings.exception.DecisionNotFoundException;
import com.meetflow.meetings.repository.DecisionRepository;
import com.meetflow.teams.TeamMembers;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisions recorded during a meeting (MF-16). The series organizer adds, edits and removes them
 * while the meeting runs, and every member of the team can read them. Each one is about an agenda
 * item of the meeting.
 */
@Service
public class DecisionService {

  private final DecisionRepository decisions;
  private final AgendaService agenda;
  private final MeetingService meetings;
  private final TeamMembers teamMembers;

  DecisionService(
      DecisionRepository decisions,
      AgendaService agenda,
      MeetingService meetings,
      TeamMembers teamMembers) {
    this.decisions = decisions;
    this.agenda = agenda;
    this.meetings = meetings;
    this.teamMembers = teamMembers;
  }

  /** The meeting's decisions, oldest first. */
  @Transactional(readOnly = true)
  public List<DecisionResponse> list(UUID userId, UUID meetingId) {
    meetings.findVisible(userId, meetingId);
    return decisions.findByMeetingIdOrderByCreatedAt(meetingId).stream()
        .map(DecisionService::toResponse)
        .toList();
  }

  /** Decisions are recorded during the meeting: 409 before it starts and after it closes. */
  @Transactional
  public DecisionResponse add(UUID userId, UUID itemId, String text) {
    var item = agenda.findInRunningMeeting(userId, itemId);
    var decision =
        new Decision(item.getTeamId(), item.getMeetingId(), item.getId(), text.strip(), userId);
    return toResponse(decisions.save(decision));
  }

  @Transactional
  public DecisionResponse edit(UUID userId, UUID decisionId, String text) {
    var decision = findInRunningMeeting(userId, decisionId);
    decision.reword(text.strip());
    return toResponse(decision);
  }

  @Transactional
  public void remove(UUID userId, UUID decisionId) {
    decisions.delete(findInRunningMeeting(userId, decisionId));
  }

  /**
   * The decision, loaded only once its meeting is locked, so one removed meanwhile answers 404.
   * Outsiders get the same 404 as for an unknown decision.
   */
  private Decision findInRunningMeeting(UUID userId, UUID decisionId) {
    var place =
        decisions
            .findPlaceById(decisionId)
            .filter(found -> teamMembers.isMember(userId, found.teamId()))
            .orElseThrow(DecisionNotFoundException::new);
    meetings.lockRunningMeeting(userId, place.meetingId());
    return decisions.findById(decisionId).orElseThrow(DecisionNotFoundException::new);
  }

  private static DecisionResponse toResponse(Decision decision) {
    return new DecisionResponse(decision.getId(), decision.getAgendaItemId(), decision.getText());
  }
}
