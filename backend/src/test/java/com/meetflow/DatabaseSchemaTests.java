package com.meetflow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rules from docs/data-model.md that the database enforces on its own, checked against the Flyway
 * migrations.
 */
@IntegrationTest
@Transactional
class DatabaseSchemaTests {

  @Autowired JdbcTemplate jdbc;

  UUID userId = UUID.randomUUID();
  UUID teamId = UUID.randomUUID();
  UUID seriesId = UUID.randomUUID();

  @BeforeEach
  void createSeries() {
    jdbc.update(
        "INSERT INTO app_user (id, email, name, password_hash, created_at) VALUES (?, ?, 'Ana', 'hash', now())",
        userId,
        userId + "@example.com");
    jdbc.update(
        "INSERT INTO team (id, name, created_by, created_at) VALUES (?, 'Ops', ?, now())",
        teamId,
        userId);
    insertSeries(seriesId, teamId);
  }

  @Test
  void seriesHasAtMostOneMeetingThatIsNotClosed() {
    insertMeeting("CLOSED");
    insertMeeting("SCHEDULED");

    assertThatThrownBy(() -> insertMeeting("SCHEDULED")).isInstanceOf(DuplicateKeyException.class);
  }

  // One rejected statement per test: PostgreSQL aborts the test's transaction
  // after the first error.
  @Test
  void closedMeetingHasItsClosingDetails() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO meeting (id, team_id, series_id, scheduled_at, status, started_at, version) VALUES (?, ?, ?, now(), 'CLOSED', now(), 0)",
                    UUID.randomUUID(),
                    teamId,
                    seriesId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void scheduledMeetingHasNoClosingDetails() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO meeting (id, team_id, series_id, scheduled_at, status, closed_at, version) VALUES (?, ?, ?, now(), 'SCHEDULED', now(), 0)",
                    UUID.randomUUID(),
                    teamId,
                    seriesId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void minutesCannotBeUpdated() {
    UUID meetingId = insertMeeting("CLOSED");
    UUID minutesId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO minutes (id, team_id, meeting_id, generated_at, content) VALUES (?, ?, ?, now(), '{}')",
        minutesId,
        teamId,
        meetingId);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE minutes SET content = '{\"edited\": true}' WHERE id = ?", minutesId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("minutes are read-only");
  }

  @Test
  void meetingCannotPointToAnotherTeamsSeries() {
    UUID otherTeamId = insertTeam();

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO meeting (id, team_id, series_id, scheduled_at, status, version) VALUES (?, ?, ?, now(), 'SCHEDULED', 0)",
                    UUID.randomUUID(),
                    otherTeamId,
                    seriesId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // The same inserts as the rejection tests below, with valid links,
  // so those tests fail for the intended reason.
  @Test
  void linksWithinOneMeetingAndSeriesAreAccepted() {
    UUID closedMeetingId = insertMeeting("CLOSED");
    UUID uncoveredItemId = insertAgendaItem(teamId, closedMeetingId);
    UUID carriedOverTaskId = insertTask(seriesId, closedMeetingId);
    UUID meetingId = insertMeeting("IN_PROGRESS");
    UUID agendaItemId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO agenda_item (id, team_id, meeting_id, position, title, status, carried_over_from_id, version) VALUES (?, ?, ?, 1, 'Budget', 'PENDING', ?, 0)",
        agendaItemId,
        teamId,
        meetingId,
        uncoveredItemId);
    UUID decisionId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO decision (id, team_id, meeting_id, agenda_item_id, text, created_by, created_at) VALUES (?, ?, ?, ?, 'Agreed', ?, now())",
        decisionId,
        teamId,
        meetingId,
        agendaItemId,
        userId);
    UUID taskId = insertTask(seriesId, meetingId);
    jdbc.update(
        "UPDATE task SET decision_id = ?, agenda_item_id = ? WHERE id = ?",
        decisionId,
        agendaItemId,
        taskId);
    jdbc.update(
        "INSERT INTO meeting_task_review (meeting_id, task_id, series_id) VALUES (?, ?, ?)",
        meetingId,
        carriedOverTaskId,
        seriesId);
    insertTaskChange(carriedOverTaskId, teamId, meetingId);
    insertTaskChange(carriedOverTaskId, teamId, null);
  }

  @Test
  void taskCannotComeFromAnotherMeetingsDecision() {
    UUID closedMeetingId = insertMeeting("CLOSED");
    UUID oldDecisionId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO decision (id, team_id, meeting_id, text, created_by, created_at) VALUES (?, ?, ?, 'Agreed', ?, now())",
        oldDecisionId,
        teamId,
        closedMeetingId,
        userId);
    UUID taskId = insertTask(seriesId, insertMeeting("IN_PROGRESS"));

    assertThatThrownBy(
            () ->
                jdbc.update("UPDATE task SET decision_id = ? WHERE id = ?", oldDecisionId, taskId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void taskCarriesOverInTheSeriesOfItsMeeting() {
    UUID meetingId = insertMeeting("IN_PROGRESS");
    UUID otherSeriesId = UUID.randomUUID();
    insertSeries(otherSeriesId, teamId);

    assertThatThrownBy(() -> insertTask(otherSeriesId, meetingId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void decisionCannotBeAboutAnotherTeamsAgendaItem() {
    UUID meetingId = insertMeeting("IN_PROGRESS");
    UUID otherTeamId = insertTeam();
    UUID otherSeriesId = UUID.randomUUID();
    insertSeries(otherSeriesId, otherTeamId);
    UUID otherAgendaItemId =
        insertAgendaItem(otherTeamId, insertMeeting(otherTeamId, otherSeriesId, "IN_PROGRESS"));

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO decision (id, team_id, meeting_id, agenda_item_id, text, created_by, created_at) VALUES (?, ?, ?, ?, 'Agreed', ?, now())",
                    UUID.randomUUID(),
                    teamId,
                    meetingId,
                    otherAgendaItemId,
                    userId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void agendaItemCannotBeCarriedOverFromAnotherTeam() {
    UUID meetingId = insertMeeting("SCHEDULED");
    UUID otherTeamId = insertTeam();
    UUID otherSeriesId = UUID.randomUUID();
    insertSeries(otherSeriesId, otherTeamId);
    UUID otherAgendaItemId =
        insertAgendaItem(otherTeamId, insertMeeting(otherTeamId, otherSeriesId, "CLOSED"));

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO agenda_item (id, team_id, meeting_id, position, title, status, carried_over_from_id, version) VALUES (?, ?, ?, 1, 'Budget', 'PENDING', ?, 0)",
                    UUID.randomUUID(),
                    teamId,
                    meetingId,
                    otherAgendaItemId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void taskIsNotReviewedInAnotherSeriesMeeting() {
    UUID taskId = insertTask(seriesId, insertMeeting("CLOSED"));
    UUID otherSeriesId = UUID.randomUUID();
    insertSeries(otherSeriesId, teamId);
    UUID otherMeetingId = insertMeeting(teamId, otherSeriesId, "SCHEDULED");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO meeting_task_review (meeting_id, task_id, series_id) VALUES (?, ?, ?)",
                    otherMeetingId,
                    taskId,
                    seriesId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void reviewSeriesMustBeTheTasksSeries() {
    UUID taskId = insertTask(seriesId, insertMeeting("CLOSED"));
    UUID otherSeriesId = UUID.randomUUID();
    insertSeries(otherSeriesId, teamId);
    UUID otherMeetingId = insertMeeting(teamId, otherSeriesId, "SCHEDULED");

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO meeting_task_review (meeting_id, task_id, series_id) VALUES (?, ?, ?)",
                    otherMeetingId,
                    taskId,
                    otherSeriesId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void taskChangeCannotPointToAnotherTeamsMeeting() {
    UUID taskId = insertTask(seriesId, insertMeeting("CLOSED"));
    UUID otherTeamId = insertTeam();
    UUID otherSeriesId = UUID.randomUUID();
    insertSeries(otherSeriesId, otherTeamId);
    UUID otherMeetingId = insertMeeting(otherTeamId, otherSeriesId, "IN_PROGRESS");

    assertThatThrownBy(() -> insertTaskChange(taskId, teamId, otherMeetingId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void taskChangeIsInTheTasksTeam() {
    UUID taskId = insertTask(seriesId, insertMeeting("CLOSED"));
    UUID otherTeamId = insertTeam();

    assertThatThrownBy(() -> insertTaskChange(taskId, otherTeamId, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void emailIsAtMost254Characters() {
    String email = "a".repeat(243) + "@example.com";

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO app_user (id, email, name, password_hash, created_at) VALUES (?, ?, 'Bo', 'hash', now())",
                    UUID.randomUUID(),
                    email))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private UUID insertTeam() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO team (id, name, created_by, created_at) VALUES (?, 'Sales', ?, now())",
        id,
        userId);
    return id;
  }

  private void insertSeries(UUID id, UUID team) {
    jdbc.update(
        "INSERT INTO meeting_series (id, team_id, name, organizer_id, created_at) VALUES (?, ?, 'Weekly sync', ?, now())",
        id,
        team,
        userId);
  }

  private UUID insertMeeting(String status) {
    return insertMeeting(teamId, seriesId, status);
  }

  private UUID insertMeeting(UUID team, UUID series, String status) {
    UUID meetingId = UUID.randomUUID();
    jdbc.update(
        """
				INSERT INTO meeting (id, team_id, series_id, scheduled_at, status, started_at, closed_at, closed_by, version)
				VALUES (?, ?, ?, now(), ?,
				        CASE WHEN ? <> 'SCHEDULED' THEN now() END,
				        CASE WHEN ? = 'CLOSED' THEN now() END,
				        CASE WHEN ? = 'CLOSED' THEN ?::uuid END,
				        0)
				""",
        meetingId,
        team,
        series,
        status,
        status,
        status,
        status,
        userId);
    return meetingId;
  }

  private UUID insertAgendaItem(UUID team, UUID meetingId) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO agenda_item (id, team_id, meeting_id, position, title, status, version) VALUES (?, ?, ?, 1, 'Budget', 'PENDING', 0)",
        id,
        team,
        meetingId);
    return id;
  }

  private UUID insertTask(UUID series, UUID meetingId) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
				INSERT INTO task (id, team_id, series_id, meeting_id, description, owner_id, deadline, status,
				                  created_by, created_at, updated_at, version)
				VALUES (?, ?, ?, ?, 'Send the report', ?, current_date, 'OPEN', ?, now(), now(), 0)
				""",
        id,
        teamId,
        series,
        meetingId,
        userId,
        userId);
    return id;
  }

  private void insertTaskChange(UUID taskId, UUID team, @Nullable UUID meetingId) {
    jdbc.update(
        "INSERT INTO task_change (id, task_id, team_id, changed_by, changed_at, meeting_id, new_status) VALUES (?, ?, ?, ?, now(), ?, 'DONE')",
        UUID.randomUUID(),
        taskId,
        team,
        userId,
        meetingId);
  }
}
