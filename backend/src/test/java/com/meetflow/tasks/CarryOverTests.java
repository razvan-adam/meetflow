package com.meetflow.tasks;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.meetflow.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
 * MF-19: open tasks and uncovered agenda items reappear at the next meeting of the series, through
 * the whole app. Ana organizes the series and Bo owns the tasks; Cy is in another team. The first
 * meeting is running, with one agenda item and one task, unless a test closes it.
 */
@IntegrationTest
class CarryOverTests {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  Cookie csrfToken;

  Cookie ana;

  Cookie bo;

  Cookie cy;

  String boId;

  String teamId;

  String seriesId;

  String firstMeeting;

  String budget;

  String task;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa");
    bo = signUp("Bo Lind");
    cy = signUp("Cy Park");
    boId = JsonPath.read(body(get(bo, "/api/users/me")), "$.id");
    teamId = idOf(post(ana, "/api/teams", "{\"name\": \"Ops team\"}"));
    jdbc.update(
        "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
            + " VALUES (?::uuid, ?::uuid, 'MEMBER', now())",
        teamId,
        boId);
    seriesId = createSeries("Weekly ops sync");
    firstMeeting = schedule(seriesId);
    start(firstMeeting);
    budget = addItem(firstMeeting, "Budget");
    task = addTask(budget, "Sign the contract", "2026-11-20");
  }

  @Test
  void openAndInProgressTasksReappearAtTheNextMeeting() {
    String started = addTask(budget, "Call the agency", "2026-11-10");
    String done = addTask(budget, "Book the room", "2026-11-05");
    assertThat(status(bo, started, "IN_PROGRESS")).hasStatusOk();
    assertThat(status(bo, done, "DONE")).hasStatusOk();
    close(firstMeeting);

    String next = schedule(seriesId);

    // Soonest deadline first, and the tasks are the same ones, not copies.
    assertThat(carriedIds(ana, next)).isEqualTo(List.of(started, task));
  }

  @Test
  void aTaskKeepsComingBackUntilItIsDone() {
    close(firstMeeting);
    String second = schedule(seriesId);
    start(second);
    close(second);

    String third = schedule(seriesId);
    assertThat(carriedIds(ana, third)).isEqualTo(List.of(task));

    start(third);
    assertThat(status(bo, task, "DONE")).hasStatusOk();
    close(third);
    assertThat(carriedIds(ana, schedule(seriesId))).isEmpty();
  }

  @Test
  void aMeetingDoesNotListTheTasksCreatedInIt() {
    assertThat(carriedIds(ana, firstMeeting)).isEmpty();
  }

  @Test
  void carryOverStaysWithinTheSeries() {
    close(firstMeeting);
    String otherSeries = createSeries("Monthly budget review");

    String other = schedule(otherSeries);

    assertThat(carriedIds(ana, other)).isEmpty();
    assertThat(agenda(ana, other)).isEqualTo("[]");
  }

  @Test
  void uncoveredAndUnmarkedItemsCarryOverInOrderWithoutTheirNotes() {
    String covered = addItem(firstMeeting, "Staffing");
    String uncovered = addItem(firstMeeting, "Returns backlog");
    assertThat(
            send(
                mvc.put().uri("/api/agenda-items/{id}/status", covered).cookie(ana),
                "{\"status\": \"COVERED\"}"))
        .hasStatusOk();
    assertThat(
            send(
                mvc.put().uri("/api/agenda-items/{id}/status", uncovered).cookie(ana),
                "{\"status\": \"NOT_COVERED\"}"))
        .hasStatusOk();
    assertThat(
            send(
                mvc.put().uri("/api/agenda-items/{id}/notes", uncovered).cookie(ana),
                "{\"notes\": \"Waiting for numbers\"}"))
        .hasStatusOk();
    close(firstMeeting);

    String next = schedule(seriesId);

    assertThat(mvc.get().uri("/api/meetings/{id}/agenda-items", next).cookie(ana))
        .bodyJson()
        .isStrictlyEqualTo(
            """
            [{"id": "%s", "title": "Budget", "notes": null, "status": "PENDING",
              "carriedOver": true},
             {"id": "%s", "title": "Returns backlog", "notes": null, "status": "PENDING",
              "carriedOver": true}]"""
                .formatted(itemIdAt(next, 0), itemIdAt(next, 1)));
  }

  @Test
  void itemsOnlyCarryOverFromTheLastMeeting() {
    close(firstMeeting);
    String second = schedule(seriesId);
    start(second);
    String carried = itemIdAt(second, 0);
    assertThat(
            send(
                mvc.put().uri("/api/agenda-items/{id}/status", carried).cookie(ana),
                "{\"status\": \"COVERED\"}"))
        .hasStatusOk();
    close(second);

    assertThat(agenda(ana, schedule(seriesId))).isEqualTo("[]");
  }

  @Test
  void statusChangesBetweenMeetingsShowOnTheAgenda() {
    close(firstMeeting);
    String next = schedule(seriesId);

    assertThat(status(bo, task, "IN_PROGRESS")).hasStatusOk();

    assertThat(get(ana, carriedPath(next)))
        .bodyJson()
        .extractingPath("$[0].status")
        .isEqualTo("IN_PROGRESS");
  }

  @Test
  void aTaskDoneBeforeTheMeetingStartsLeavesItsAgendaAndComesBackIfReopened() {
    close(firstMeeting);
    String next = schedule(seriesId);

    assertThat(status(bo, task, "DONE")).hasStatusOk();
    assertThat(carriedIds(ana, next)).isEmpty();

    assertThat(status(bo, task, "OPEN")).hasStatusOk();
    assertThat(carriedIds(ana, next)).isEqualTo(List.of(task));
  }

  @Test
  void aTaskDoneDuringTheMeetingStaysOnItsList() {
    close(firstMeeting);
    String next = schedule(seriesId);
    start(next);

    assertThat(status(bo, task, "DONE")).hasStatusOk();
    assertThat(get(ana, carriedPath(next)))
        .bodyJson()
        .extractingPath("$[0].status")
        .isEqualTo("DONE");

    // Reopening it doesn't list it twice.
    assertThat(status(bo, task, "OPEN")).hasStatusOk();
    assertThat(carriedIds(ana, next)).isEqualTo(List.of(task));
  }

  @Test
  void aReopenedTaskJoinsTheNextMeetingWhetherScheduledOrRunning() {
    assertThat(status(bo, task, "DONE")).hasStatusOk();
    close(firstMeeting);
    String scheduled = schedule(seriesId);
    assertThat(carriedIds(ana, scheduled)).isEmpty();

    start(scheduled);
    assertThat(status(bo, task, "IN_PROGRESS")).hasStatusOk();

    assertThat(carriedIds(ana, scheduled)).isEqualTo(List.of(task));
  }

  @Test
  void aTaskReopenedDuringItsOwnMeetingIsNotListedAsCarriedOver() {
    assertThat(status(bo, task, "DONE")).hasStatusOk();
    assertThat(status(bo, task, "OPEN")).hasStatusOk();

    assertThat(carriedIds(ana, firstMeeting)).isEmpty();
  }

  @Test
  void aTaskReopenedWithNoMeetingScheduledReappearsAtTheNextOne() {
    assertThat(status(bo, task, "DONE")).hasStatusOk();
    close(firstMeeting);
    assertThat(status(bo, task, "OPEN")).hasStatusOk();

    assertThat(carriedIds(ana, schedule(seriesId))).isEqualTo(List.of(task));
  }

  @Test
  void aDeletedTaskStaysMarkedOnTheMeetingsThatReviewedItButNeverCarriesOverAgain() {
    close(firstMeeting);
    String second = schedule(seriesId);
    start(second);
    close(second);
    String third = schedule(seriesId);
    start(third);

    assertThat(send(mvc.delete().uri("/api/tasks/{id}", task).cookie(ana), ""))
        .hasStatus(HttpStatus.NO_CONTENT);

    // The meeting it is deleted in shows that straight away, not only once it closes.
    assertThat(get(ana, carriedPath(third)))
        .bodyJson()
        .isLenientlyEqualTo(
            "[{\"id\": \"%s\", \"deletion\": {\"byName\": \"Ana Costa\"}}]".formatted(task));
    assertThat(get(ana, carriedPath(second)))
        .bodyJson()
        .isLenientlyEqualTo(
            "[{\"id\": \"%s\", \"deletion\": {\"byName\": \"Ana Costa\"}}]".formatted(task));

    close(third);
    assertThat(carriedIds(ana, third)).isEqualTo(List.of(task));
    assertThat(carriedIds(ana, schedule(seriesId))).isEmpty();
  }

  @Test
  void aTaskDeletedInItsOwnMeetingNeverCarriesOver() {
    assertThat(send(mvc.delete().uri("/api/tasks/{id}", task).cookie(ana), ""))
        .hasStatus(HttpStatus.NO_CONTENT);
    close(firstMeeting);

    assertThat(carriedIds(ana, schedule(seriesId))).isEmpty();
  }

  @Test
  void closingAMeetingRecordsTheStatusOfItsCarriedOverTasks() {
    close(firstMeeting);
    String next = schedule(seriesId);
    start(next);
    assertThat(status(bo, task, "IN_PROGRESS")).hasStatusOk();
    assertThat(statusAtClose(next)).containsExactly((String) null);

    close(next);
    assertThat(status(bo, task, "DONE")).hasStatusOk();

    assertThat(statusAtClose(next)).containsExactly("IN_PROGRESS");
  }

  @Test
  void onlyMembersOfTheTeamReadTheList() {
    assertThat(get(bo, carriedPath(firstMeeting))).hasStatusOk();
    assertThat(get(cy, carriedPath(firstMeeting))).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri(carriedPath(firstMeeting)).exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  private List<String> carriedIds(Cookie session, String meetingId) {
    return JsonPath.read(body(get(session, carriedPath(meetingId))), "$[*].id");
  }

  private static String carriedPath(String meetingId) {
    return "/api/meetings/" + meetingId + "/carried-over-tasks";
  }

  private String agenda(Cookie session, String meetingId) {
    return body(get(session, "/api/meetings/" + meetingId + "/agenda-items"));
  }

  private String itemIdAt(String meetingId, int index) {
    return JsonPath.read(agenda(ana, meetingId), "$[" + index + "].id");
  }

  private List<@Nullable String> statusAtClose(String meetingId) {
    return jdbc.queryForList(
        "SELECT status_at_close FROM meeting_task_review WHERE meeting_id = ?::uuid",
        String.class,
        meetingId);
  }

  private String createSeries(String name) {
    return idOf(
        post(ana, "/api/teams/" + teamId + "/meeting-series", "{\"name\": \"" + name + "\"}"));
  }

  private String schedule(String series) {
    return idOf(
        post(
            ana,
            "/api/meeting-series/" + series + "/meetings",
            "{\"scheduledAt\": \"2026-11-09T08:30:00Z\", \"participantIds\": [\"%s\"]}"
                .formatted(boId)));
  }

  private void start(String meetingId) {
    assertThat(post(ana, "/api/meetings/" + meetingId + "/start", "")).hasStatusOk();
  }

  private void close(String meetingId) {
    assertThat(post(ana, "/api/meetings/" + meetingId + "/close", "")).hasStatusOk();
  }

  private String addItem(String meetingId, String title) {
    return idOf(
        post(
            ana, "/api/meetings/" + meetingId + "/agenda-items", "{\"title\": \"" + title + "\"}"));
  }

  private String addTask(String itemId, String description, String deadline) {
    return idOf(
        post(
            ana,
            "/api/agenda-items/" + itemId + "/tasks",
            "{\"description\": \"%s\", \"ownerId\": \"%s\", \"deadline\": \"%s\"}"
                .formatted(description, boId, deadline)));
  }

  private MvcTestResult status(Cookie session, String taskId, String value) {
    return send(
        mvc.put().uri("/api/tasks/{id}/status", taskId).cookie(session),
        "{\"status\": \"" + value + "\"}");
  }

  private MvcTestResult get(Cookie session, String path) {
    return mvc.get().uri(path).cookie(session).exchange();
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

  private static String idOf(MvcTestResult result) {
    return JsonPath.read(body(result), "$.id");
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
