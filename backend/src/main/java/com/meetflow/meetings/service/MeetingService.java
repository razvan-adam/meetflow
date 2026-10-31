package com.meetflow.meetings.service;

import com.meetflow.identity.UserDirectory;
import com.meetflow.meetings.domain.Meeting;
import com.meetflow.meetings.domain.MeetingSeries;
import com.meetflow.meetings.domain.MeetingStatus;
import com.meetflow.meetings.dto.MeetingResponse;
import com.meetflow.meetings.dto.ParticipantResponse;
import com.meetflow.meetings.exception.MeetingChangedException;
import com.meetflow.meetings.exception.MeetingClosedException;
import com.meetflow.meetings.exception.MeetingNotFoundException;
import com.meetflow.meetings.exception.NotOrganizerException;
import com.meetflow.meetings.exception.OpenMeetingExistsException;
import com.meetflow.meetings.exception.ParticipantNotMemberException;
import com.meetflow.meetings.repository.MeetingRepository;
import com.meetflow.teams.TeamMembers;
import java.text.Collator;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduling meetings (MF-13). The series organizer schedules the next meeting and chooses its
 * participants from the team, and can change both until the meeting is closed. Every member of the
 * team can see the series' meetings.
 */
@Service
public class MeetingService {

  private final MeetingRepository meetings;
  private final MeetingSeriesService series;
  private final TeamMembers teamMembers;
  private final UserDirectory users;

  MeetingService(
      MeetingRepository meetings,
      MeetingSeriesService series,
      TeamMembers teamMembers,
      UserDirectory users) {
    this.meetings = meetings;
    this.series = series;
    this.teamMembers = teamMembers;
    this.users = users;
  }

  /** Schedules the series' next meeting. Organizer only, and only once the last one is closed. */
  @Transactional
  public MeetingResponse schedule(
      UUID userId, UUID seriesId, Instant scheduledAt, Collection<UUID> participantIds) {
    var found = series.findVisible(userId, seriesId);
    requireOrganizer(userId, found);
    if (meetings.existsBySeriesIdAndStatusNot(seriesId, MeetingStatus.CLOSED)) {
      throw new OpenMeetingExistsException();
    }
    var meeting =
        new Meeting(found.getTeamId(), seriesId, scheduledAt, participants(found, participantIds));
    try {
      meetings.saveAndFlush(meeting);
    } catch (DataIntegrityViolationException e) {
      // The organizer scheduled another meeting of the series at the same moment.
      throw new OpenMeetingExistsException();
    }
    return toResponse(meeting, names(List.of(meeting)));
  }

  /** The series' meetings, latest first. */
  @Transactional(readOnly = true)
  public List<MeetingResponse> listForSeries(UUID userId, UUID seriesId) {
    series.findVisible(userId, seriesId);
    var found = meetings.findBySeriesIdOrderByScheduledAtDesc(seriesId);
    var names = names(found);
    return found.stream().map(meeting -> toResponse(meeting, names)).toList();
  }

  @Transactional(readOnly = true)
  public MeetingResponse get(UUID userId, UUID meetingId) {
    var meeting = findVisible(userId, meetingId);
    return toResponse(meeting, names(List.of(meeting)));
  }

  /**
   * Changes the date and time and the participants. Organizer only, until the meeting is closed,
   * and only on the latest version of the meeting.
   */
  @Transactional
  public MeetingResponse update(
      UUID userId,
      UUID meetingId,
      Instant scheduledAt,
      Collection<UUID> participantIds,
      long version) {
    var meeting = findVisible(userId, meetingId);
    var found = series.findVisible(userId, meeting.getSeriesId());
    requireOrganizer(userId, found);
    if (meeting.getStatus() == MeetingStatus.CLOSED) {
      throw new MeetingClosedException();
    }
    if (meeting.getVersion() != version) {
      throw new MeetingChangedException();
    }
    meeting.reschedule(scheduledAt, participants(found, participantIds));
    // Flushed now so the response carries the new version.
    meetings.flush();
    return toResponse(meeting, names(List.of(meeting)));
  }

  /** The meeting, if the user is a member of its team; otherwise 404, as if it didn't exist. */
  private Meeting findVisible(UUID userId, UUID meetingId) {
    return meetings
        .findById(meetingId)
        .filter(found -> teamMembers.isMember(userId, found.getTeamId()))
        .orElseThrow(MeetingNotFoundException::new);
  }

  private static void requireOrganizer(UUID userId, MeetingSeries found) {
    if (!found.getOrganizerId().equals(userId)) {
      throw new NotOrganizerException();
    }
  }

  /** The chosen participants plus the organizer, who always attends; all must be members. */
  private Set<UUID> participants(MeetingSeries found, Collection<UUID> participantIds) {
    var participants = new HashSet<>(participantIds);
    participants.add(found.getOrganizerId());
    if (!teamMembers.areMembers(participants, found.getTeamId())) {
      throw new ParticipantNotMemberException();
    }
    return participants;
  }

  private Map<UUID, String> names(List<Meeting> list) {
    return users.namesOf(
        list.stream().flatMap(meeting -> meeting.getParticipantIds().stream()).toList());
  }

  private static MeetingResponse toResponse(Meeting meeting, Map<UUID, String> names) {
    var byName = Collator.getInstance(Locale.ENGLISH);
    var participants =
        meeting.getParticipantIds().stream()
            .map(id -> new ParticipantResponse(id, names.getOrDefault(id, "")))
            .sorted(Comparator.comparing(ParticipantResponse::name, byName))
            .toList();
    return new MeetingResponse(
        meeting.getId(),
        meeting.getTeamId(),
        meeting.getSeriesId(),
        meeting.getScheduledAt(),
        meeting.getStatus(),
        participants,
        meeting.getVersion());
  }
}
