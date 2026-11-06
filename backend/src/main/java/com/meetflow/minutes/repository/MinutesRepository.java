package com.meetflow.minutes.repository;

import com.meetflow.minutes.domain.Minutes;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MinutesRepository extends JpaRepository<Minutes, UUID> {

  Optional<Minutes> findByMeetingId(UUID meetingId);

  /**
   * One page of the team's minutes as the meeting history lists them, newest meeting first. Only
   * these few fields are read from each snapshot, not the notes and tasks. Pages follow the order
   * of {@code minutes_history_idx}: the date the meeting was held (ISO text, which sorts like the
   * date), then when it closed.
   *
   * @param after the meeting the previous page ended with, or empty for the first page; a meeting
   *     of another team gives an empty page
   * @param onlyMeetingIds comma-separated meeting IDs to keep, or empty to keep them all
   */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT meeting_id AS "meetingId",
                 CAST(content ->> 'seriesId' AS uuid) AS "seriesId",
                 content ->> 'seriesName' AS "seriesName",
                 CAST(content ->> 'meetingDate' AS date) AS "meetingDate",
                 CAST(jsonb_path_query_array(content, '$.agenda[*].decisions[*]') AS text)
                   AS "decisions",
                 jsonb_array_length(content -> 'newTasks') AS "taskCount"
          FROM minutes
          WHERE team_id = :teamId
            AND (:after = ''
              OR (content ->> 'meetingDate', generated_at, id)
                < (SELECT content ->> 'meetingDate', generated_at, id FROM minutes
                   WHERE team_id = :teamId AND meeting_id = CAST(NULLIF(:after, '') AS uuid)))
            AND (:onlyMeetingIds = ''
              OR meeting_id = ANY (CAST(string_to_array(:onlyMeetingIds, ',') AS uuid[])))
          ORDER BY content ->> 'meetingDate' DESC, generated_at DESC, id DESC
          LIMIT :limit""")
  List<HistoryRow> findHistoryPage(UUID teamId, String after, String onlyMeetingIds, int limit);

  /** One closed meeting in the history. */
  interface HistoryRow {
    UUID getMeetingId();

    UUID getSeriesId();

    String getSeriesName();

    LocalDate getMeetingDate();

    /** A JSON array of the decisions' text, in agenda order. */
    String getDecisions();

    int getTaskCount();
  }
}
