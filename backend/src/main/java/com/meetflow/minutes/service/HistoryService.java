package com.meetflow.minutes.service;

import com.meetflow.meetings.MeetingRecords;
import com.meetflow.minutes.dto.HistorySeries;
import com.meetflow.minutes.repository.MinutesRepository;
import com.meetflow.teams.TeamMembers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The meeting history (MF-23): a team's closed meetings, grouped by series, and a keyword search
 * across series names and decision text. Each meeting opens its minutes.
 */
@Service
public class HistoryService {

  /** Anything that isn't a letter or a digit separates words, as the frontend highlights them. */
  private static final Pattern NOT_A_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

  /** Enough for any real search, and it keeps the database query small. */
  private static final int MAX_WORDS = 10;

  private final MinutesRepository minutes;
  private final MeetingRecords meetings;
  private final TeamMembers teamMembers;
  private final JsonMapper json;

  HistoryService(
      MinutesRepository minutes,
      MeetingRecords meetings,
      TeamMembers teamMembers,
      JsonMapper json) {
    this.minutes = minutes;
    this.meetings = meetings;
    this.teamMembers = teamMembers;
    this.json = json;
  }

  /**
   * The team's closed meetings by series: the series with the newest meeting first, and its
   * meetings newest first. With search words, only meetings whose series name or one of whose
   * decisions contains them all. 404 if the user isn't a member of the team.
   */
  @Transactional(readOnly = true)
  public List<HistorySeries> of(UUID userId, UUID teamId, String search) {
    teamMembers.requireMember(userId, teamId);
    var words = wordsIn(search);
    var matching = words.isEmpty() ? null : meetings.closedMatching(teamId, words);
    var bySeries = new LinkedHashMap<UUID, SeriesBuilder>();
    for (var row : minutes.findHistoryByTeamId(teamId)) {
      if (matching != null && !matching.contains(row.getMeetingId())) {
        continue;
      }
      bySeries
          .computeIfAbsent(row.getSeriesId(), id -> new SeriesBuilder(id, row.getSeriesName()))
          .meetings
          .add(
              new HistorySeries.Meeting(
                  row.getMeetingId(),
                  row.getMeetingDate(),
                  Arrays.asList(json.readValue(row.getDecisions(), String[].class)),
                  row.getTaskCount()));
    }
    return bySeries.values().stream()
        .map(series -> new HistorySeries(series.id, series.name, List.copyOf(series.meetings)))
        .toList();
  }

  private static List<String> wordsIn(String search) {
    return NOT_A_WORD
        .splitAsStream(search.toLowerCase(Locale.ROOT))
        .filter(word -> !word.isEmpty())
        .distinct()
        .limit(MAX_WORDS)
        .toList();
  }

  private record SeriesBuilder(UUID id, String name, List<HistorySeries.Meeting> meetings) {
    SeriesBuilder(UUID id, String name) {
      this(id, name, new ArrayList<>());
    }
  }
}
