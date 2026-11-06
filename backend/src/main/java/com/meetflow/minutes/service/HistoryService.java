package com.meetflow.minutes.service;

import com.meetflow.meetings.MeetingRecords;
import com.meetflow.minutes.dto.HistoryResponse;
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
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The meeting history (MF-23): a team's closed meetings, grouped by series, and a keyword search
 * across series names and decision text. Each meeting opens its minutes.
 */
@Service
public class HistoryService {

  /** Meetings per page: enough to fill a screen, few enough to answer quickly however old. */
  private static final int PAGE_SIZE = 20;

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
   * One page of the team's closed meetings, newest first, by series: the series in the order of
   * their newest meeting on the page. With search words, only meetings whose series name or one of
   * whose decisions contains them all. 404 if the user isn't a member of the team.
   *
   * @param after the {@code next} of the previous page, or null for the first page
   */
  @Transactional(readOnly = true)
  public HistoryResponse of(UUID userId, UUID teamId, String search, @Nullable UUID after) {
    teamMembers.requireMember(userId, teamId);
    var words = wordsIn(search);
    var onlyMeetingIds = "";
    if (!words.isEmpty()) {
      var matching = meetings.closedMatching(teamId, words);
      if (matching.isEmpty()) {
        return new HistoryResponse(List.of(), null);
      }
      onlyMeetingIds = matching.stream().map(UUID::toString).collect(Collectors.joining(","));
    }
    // One more than a page, to know whether there is another.
    var rows =
        minutes.findHistoryPage(
            teamId, after == null ? "" : after.toString(), onlyMeetingIds, PAGE_SIZE + 1);
    var page = rows.subList(0, Math.min(rows.size(), PAGE_SIZE));
    var bySeries = new LinkedHashMap<UUID, SeriesBuilder>();
    for (var row : page) {
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
    return new HistoryResponse(
        bySeries.values().stream()
            .map(series -> new HistorySeries(series.id, series.name, List.copyOf(series.meetings)))
            .toList(),
        rows.size() > PAGE_SIZE ? page.getLast().getMeetingId() : null);
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
