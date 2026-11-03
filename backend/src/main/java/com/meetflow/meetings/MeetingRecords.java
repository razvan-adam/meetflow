package com.meetflow.meetings;

import static java.util.Objects.requireNonNull;

import com.meetflow.meetings.domain.AgendaItemStatus;
import com.meetflow.meetings.domain.Decision;
import com.meetflow.meetings.domain.Meeting;
import com.meetflow.meetings.exception.MeetingNotFoundException;
import com.meetflow.meetings.repository.AgendaItemRepository;
import com.meetflow.meetings.repository.DecisionRepository;
import com.meetflow.meetings.repository.MeetingRepository;
import com.meetflow.meetings.service.MeetingSeriesService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What the minutes module reads about a closed meeting. Callers check who may see it. */
@Service
public class MeetingRecords {

  private final MeetingRepository meetings;
  private final AgendaItemRepository agendaItems;
  private final DecisionRepository decisions;
  private final MeetingSeriesService series;

  MeetingRecords(
      MeetingRepository meetings,
      AgendaItemRepository agendaItems,
      DecisionRepository decisions,
      MeetingSeriesService series) {
    this.meetings = meetings;
    this.agendaItems = agendaItems;
    this.decisions = decisions;
    this.series = series;
  }

  /** The meeting, with its agenda in order. It must be closed. */
  @Transactional(readOnly = true)
  public MeetingRecord ofClosed(UUID meetingId) {
    Meeting meeting = meetings.findById(meetingId).orElseThrow(MeetingNotFoundException::new);
    var decisionsByItem =
        decisions.findByMeetingIdOrderByCreatedAt(meetingId).stream()
            .collect(
                Collectors.groupingBy(
                    Decision::getAgendaItemId,
                    Collectors.mapping(Decision::getText, Collectors.toList())));
    var agenda =
        agendaItems.findByMeetingIdOrderByPosition(meetingId).stream()
            .map(
                item ->
                    new MeetingRecord.Item(
                        item.getTitle(),
                        item.getNotes(),
                        item.getStatus() == AgendaItemStatus.COVERED,
                        item.isCarriedOver(),
                        decisionsByItem.getOrDefault(item.getId(), List.of())))
            .toList();
    var seriesName = series.namesOf(List.of(meeting.getSeriesId())).get(meeting.getSeriesId());
    return new MeetingRecord(
        meeting.getId(),
        meeting.getTeamId(),
        meeting.getSeriesId(),
        requireNonNull(seriesName),
        meeting.getScheduledAt(),
        requireNonNull(meeting.getStartedAt(), "the meeting never started"),
        requireNonNull(meeting.getClosedAt(), "the meeting isn't closed"),
        requireNonNull(meeting.getClosedBy(), "the meeting isn't closed"),
        meeting.getParticipantIds(),
        agenda);
  }
}
