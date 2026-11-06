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
   * The team's minutes as the meeting history lists them, newest meeting first. Only these few
   * fields are read from each snapshot, not the notes and tasks.
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
          ORDER BY "meetingDate" DESC, generated_at DESC""")
  List<HistoryRow> findHistoryByTeamId(UUID teamId);

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
