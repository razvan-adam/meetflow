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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the minutes, the meeting history and the task emails read about closed meetings. Callers
 * check who may see them.
 */
@Service
public class MeetingRecords {

  private static final Pattern SEARCH_WORD = Pattern.compile("[\\p{L}\\p{N}]+");

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

  /**
   * The team's closed meetings whose series name, or one of whose decisions, contains every word,
   * each as a whole word or the start of one, ignoring case.
   *
   * @param words letters and digits only, at least one
   */
  @Transactional(readOnly = true)
  public Set<UUID> closedMatching(UUID teamId, List<String> words) {
    if (words.isEmpty() || words.stream().anyMatch(word -> !SEARCH_WORD.matcher(word).matches())) {
      throw new IllegalArgumentException("each word is letters and digits only");
    }
    // Quoted and with :* for a prefix match; the words can't hold a quote or an operator.
    var query = words.stream().map(word -> "'" + word + "':*").collect(Collectors.joining(" & "));
    return Set.copyOf(meetings.findClosedIdsMatching(teamId, query));
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
