package com.meetflow.meetings;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.meetflow.IntegrationTest;
import com.meetflow.RecordingMeetingClosedListener;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * MF-15: running a meeting, through the whole app. Ana creates the team and organizes the series
 * and its meeting; Bo is a member, added directly in the database. Cy is in another team.
 */
@IntegrationTest
class RunMeetingTests {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  @Autowired RecordingMeetingClosedListener closedMeetings;

  Cookie csrfToken;

  Cookie ana;

  Cookie bo;

  Cookie cy;

  String teamId;

  String seriesId;

  String meetingId;

  String budget;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa");
    bo = signUp("Bo Lind");
    cy = signUp("Cy Park");
    teamId = JsonPath.read(body(post(ana, "/api/teams", "{\"name\": \"Ops team\"}")), "$.id");
    jdbc.update(
        "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
            + " VALUES (?::uuid, ?::uuid, 'MEMBER', now())",
        teamId,
        userId(bo));
    seriesId =
        JsonPath.read(
            body(
                post(
                    ana,
                    "/api/teams/" + teamId + "/meeting-series",
                    "{\"name\": \"Weekly ops sync\"}")),
            "$.id");
    meetingId =
        JsonPath.read(
            body(
                post(
                    ana,
                    "/api/meeting-series/" + seriesId + "/meetings",
                    "{\"scheduledAt\": \"2026-11-02T08:30:00Z\", \"participantIds\": []}")),
            "$.id");
    budget = addItem("Budget");
  }

  @Test
  void theOrganizerStartsAMeeting() {
    assertThat(start(ana))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("IN_PROGRESS");

    assertThat(
            jdbc.queryForObject(
                "SELECT started_at IS NOT NULL FROM meeting WHERE id = ?::uuid",
                Boolean.class,
                meetingId))
        .isTrue();
  }

  @Test
  void startingARunningMeetingAgainChangesNothing() {
    start(ana);
    var startedAt = startedAt();

    assertThat(start(ana))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("IN_PROGRESS");

    assertThat(startedAt()).isEqualTo(startedAt);
  }

  @Test
  void aClosedMeetingCannotStartAgain() {
    start(ana);
    close(ana);

    assertThat(start(ana)).hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void theOrganizerTakesNotesAndEveryMemberReadsThem() {
    start(ana);

    assertThat(writeNotes(ana, budget, "Forecast is up by 35%"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.notes")
        .isEqualTo("Forecast is up by 35%");

    assertThat(agenda(bo))
        .bodyJson()
        .extractingPath("$[0].notes")
        .isEqualTo("Forecast is up by 35%");
  }

  @Test
  void blankNotesCountAsNoNotes() {
    start(ana);
    writeNotes(ana, budget, "Forecast");

    assertThat(writeNotes(ana, budget, "   ")).hasStatusOk();

    assertThat(body(agenda(ana))).contains("\"notes\":null");
  }

  @Test
  void rejectsNotesThatAreTooLong() {
    start(ana);

    assertThat(writeNotes(ana, budget, "x".repeat(10_001))).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(writeNotes(ana, budget, "x".repeat(10_000))).hasStatusOk();
  }

  @Test
  void theOrganizerMarksItemsAndCanTakeTheMarkBack() {
    start(ana);

    assertThat(mark(ana, budget, "NOT_COVERED"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("NOT_COVERED");
    assertThat(mark(ana, budget, "COVERED")).hasStatusOk();
    assertThat(mark(ana, budget, "PENDING")).hasStatusOk();

    assertThat(agenda(bo)).bodyJson().extractingPath("$[0].status").isEqualTo("PENDING");
  }

  // Pressing a mark right after typing sends both at once, as leaving the notes box saves them.
  @Test
  void notesAndAMarkSentAtTheSameMomentBothSave() throws Exception {
    start(ana);

    try (var executor = Executors.newFixedThreadPool(2)) {
      for (int round = 0; round < 20; round++) {
        String notes = "Round " + round;
        var saved = executor.submit(() -> writeNotes(ana, budget, notes));
        var marked = executor.submit(() -> mark(ana, budget, "COVERED"));

        assertThat(saved.get().getResponse().getStatus()).isEqualTo(200);
        assertThat(marked.get().getResponse().getStatus()).isEqualTo(200);
        assertThat(agenda(ana)).bodyJson().extractingPath("$[0].notes").isEqualTo(notes);
        assertThat(agenda(ana)).bodyJson().extractingPath("$[0].status").isEqualTo("COVERED");
        mark(ana, budget, "PENDING");
      }
    }
  }

  @Test
  void rejectsAnUnknownMark() {
    start(ana);

    assertThat(mark(ana, budget, "MAYBE")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void notesAndMarksWaitForTheMeetingToStart() {
    assertThat(writeNotes(ana, budget, "Too early")).hasStatus(HttpStatus.CONFLICT);
    assertThat(mark(ana, budget, "COVERED")).hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void aMeetingThatHasNotStartedCannotClose() {
    assertThat(close(ana)).hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void closingLocksTheMeeting() {
    start(ana);
    writeNotes(ana, budget, "Agreed");

    assertThat(close(ana)).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("CLOSED");

    assertThat(writeNotes(ana, budget, "Changed later")).hasStatus(HttpStatus.CONFLICT);
    assertThat(mark(ana, budget, "NOT_COVERED")).hasStatus(HttpStatus.CONFLICT);
    assertThat(close(ana)).hasStatus(HttpStatus.CONFLICT);
    assertThat(agenda(bo)).bodyJson().extractingPath("$[0].notes").isEqualTo("Agreed");
  }

  @Test
  void closingRecordsWhoClosedTheMeetingAndWhen() {
    start(ana);

    assertThat(body(close(ana))).contains("\"closedAt\":\"");

    assertThat(
            jdbc.queryForObject(
                "SELECT closed_by::text FROM meeting WHERE id = ?::uuid", String.class, meetingId))
        .isEqualTo(userId(ana));
  }

  @Test
  void itemsNobodyMarkedCountAsNotCoveredWhenTheMeetingCloses() {
    String staffing = addItem("Staffing");
    String hiring = addItem("Hiring");
    start(ana);
    mark(ana, budget, "COVERED");
    mark(ana, staffing, "NOT_COVERED");

    close(ana);

    assertThat(statusOf(budget)).isEqualTo("COVERED");
    assertThat(statusOf(staffing)).isEqualTo("NOT_COVERED");
    assertThat(statusOf(hiring)).isEqualTo("NOT_COVERED");
  }

  @Test
  void closingPublishesAnEventThatReachesItsListeners() {
    start(ana);

    close(ana);

    var expected =
        new MeetingClosedEvent(
            UUID.fromString(meetingId), UUID.fromString(teamId), UUID.fromString(seriesId));
    await().until(() -> closedMeetings.received().contains(expected));
    await()
        .until(
            () ->
                jdbc.queryForObject(
                    "SELECT count(*) FROM event_publication"
                        + " WHERE serialized_event LIKE ? AND completion_date IS NOT NULL",
                    Long.class,
                    "%" + meetingId + "%"),
            completed -> completed == 1);
  }

  @Test
  void aRejectedCloseDoesNotPublishAnEvent() {
    start(ana);

    assertThat(close(bo)).hasStatus(HttpStatus.FORBIDDEN);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?",
                Long.class,
                "%" + meetingId + "%"))
        .isZero();
  }

  @Test
  void onlyTheOrganizerRunsTheMeeting() {
    assertThat(start(bo)).hasStatus(HttpStatus.FORBIDDEN);
    start(ana);

    assertThat(writeNotes(bo, budget, "Mine")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(mark(bo, budget, "COVERED")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(close(bo)).hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void outsidersGetNotFoundForEverything() {
    start(ana);

    assertThat(start(cy)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(close(cy)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(writeNotes(cy, budget, "Mine")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mark(cy, budget, "COVERED")).hasStatus(HttpStatus.NOT_FOUND);
  }

  private String addItem(String title) {
    return JsonPath.read(
        body(
            post(
                ana,
                "/api/meetings/" + meetingId + "/agenda-items",
                "{\"title\": \"" + title + "\"}")),
        "$.id");
  }

  private MvcTestResult start(Cookie session) {
    return post(session, "/api/meetings/" + meetingId + "/start", "");
  }

  private MvcTestResult close(Cookie session) {
    return post(session, "/api/meetings/" + meetingId + "/close", "");
  }

  private MvcTestResult writeNotes(Cookie session, String itemId, String notes) {
    return send(
        mvc.put().uri("/api/agenda-items/{id}/notes", itemId).cookie(session),
        "{\"notes\": \"" + notes + "\"}");
  }

  private MvcTestResult mark(Cookie session, String itemId, String status) {
    return send(
        mvc.put().uri("/api/agenda-items/{id}/status", itemId).cookie(session),
        "{\"status\": \"" + status + "\"}");
  }

  private MvcTestResult agenda(Cookie session) {
    return mvc.get().uri("/api/meetings/{id}/agenda-items", meetingId).cookie(session).exchange();
  }

  private String statusOf(String itemId) {
    List<String> statuses =
        JsonPath.read(body(agenda(ana)), "$[?(@.id == '" + itemId + "')].status");
    return statuses.getFirst();
  }

  private OffsetDateTime startedAt() {
    return requireNonNull(
        jdbc.queryForObject(
            "SELECT started_at FROM meeting WHERE id = ?::uuid", OffsetDateTime.class, meetingId));
  }

  /** Signs up a new user and returns their session cookie. */
  private Cookie signUp(String name) {
    var json =
        """
        {"name": "%s", "email": "user.%s@example.com", "password": "correct horse battery"}"""
            .formatted(name, UUID.randomUUID());
    var response = send(mvc.post().uri("/api/users"), json).getResponse();
    return requireNonNull(response.getCookie("SESSION"), "sign-up failed");
  }

  private String userId(Cookie session) {
    return JsonPath.read(body(mvc.get().uri("/api/users/me").cookie(session).exchange()), "$.id");
  }

  private MvcTestResult post(Cookie session, String path, String json) {
    return send(mvc.post().uri(path).cookie(session), json);
  }

  // The way the React app sends changes: JSON body, CSRF cookie echoed in the header.
  private MvcTestResult send(MockMvcRequestBuilder request, String json) {
    return request
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .cookie(csrfToken)
        .header("X-XSRF-TOKEN", csrfToken.getValue())
        .exchange();
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private Cookie csrfCookie() {
    var response = mvc.get().uri("/api/health").exchange().getResponse();
    return requireNonNull(response.getCookie("XSRF-TOKEN"), "no XSRF-TOKEN cookie");
  }
}
