package com.meetflow.minutes;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.meetflow.IntegrationTest;
import com.meetflow.RecordingEmailSender;
import com.meetflow.notifications.MinutesEmail;
import com.meetflow.notifications.MinutesMailer;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * MF-22: minutes, through the whole app. Ana organizes a series with two meetings, the second of
 * them running, and Bo takes part in both. Cy is in another team.
 */
@IntegrationTest
class MinutesTests {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  @Autowired RecordingEmailSender emails;

  @Autowired IncompleteEventPublications incomplete;

  @Autowired MinutesMailer mailer;

  Cookie csrfToken;

  Cookie ana;

  Cookie bo;

  Cookie cy;

  String anaEmail;

  String boEmail;

  String anaId;

  String boId;

  String teamId;

  String seriesId;

  String firstMeeting;

  String firstItem;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    anaEmail = "ana." + UUID.randomUUID() + "@example.com";
    boEmail = "bo." + UUID.randomUUID() + "@example.com";
    ana = signUp("Ana Costa", anaEmail);
    bo = signUp("Bo Lind", boEmail);
    cy = signUp("Cy Park", "cy." + UUID.randomUUID() + "@example.com");
    anaId = userId(ana);
    boId = userId(bo);
    teamId = idOf(post(ana, "/api/teams", "{\"name\": \"Ops team\"}"));
    jdbc.update(
        "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
            + " VALUES (?::uuid, ?::uuid, 'MEMBER', now())",
        teamId,
        boId);
    seriesId =
        idOf(
            post(
                ana,
                "/api/teams/" + teamId + "/meeting-series",
                "{\"name\": \"Weekly ops sync\"}"));
    firstMeeting = startMeeting("2026-10-26T09:00:00Z");
    firstItem = addItem(firstMeeting, "Budget");
  }

  @AfterEach
  void stopFailing() {
    emails.setFailingFor(null);
  }

  @Test
  void closingWritesTheMinutesWithEverythingInTheMeeting() {
    addItem(firstMeeting, "Staffing");
    assertThat(writeNotes(firstItem, "Costs are up")).hasStatusOk();
    assertThat(mark(firstItem, "COVERED")).hasStatusOk();
    assertThat(post(ana, "/api/agenda-items/" + firstItem + "/decisions", text("Cut the costs")))
        .hasStatus(HttpStatus.CREATED);
    String task = addTask(firstItem, "Sign the contract", boId, "2026-11-20");

    assertThat(close(firstMeeting, "Europe/Madrid")).hasStatusOk();

    assertThat(minutes(ana, firstMeeting))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"meetingId": "%s", "teamId": "%s", "seriesName": "Weekly ops sync",
             "meetingDate": "2026-10-26", "closedByName": "Ana Costa",
             "participants": [
               {"id": "%s", "name": "Ana Costa", "organizer": true},
               {"id": "%s", "name": "Bo Lind", "organizer": false}],
             "commitments": [],
             "agenda": [
               {"title": "Budget", "notes": "Costs are up", "covered": true, "carriedOver": false,
                "decisions": ["Cut the costs"]},
               {"title": "Staffing", "notes": null, "covered": false, "carriedOver": false,
                "decisions": []}],
             "newTasks": [
               {"id": "%s", "description": "Sign the contract", "ownerName": "Bo Lind",
                "deadline": "2026-11-20", "statusAtClose": "OPEN", "daysOverdue": 0,
                "removed": false, "removedSince": false, "currentStatus": "OPEN"}]}"""
                .formatted(firstMeeting, teamId, anaId, boId, task));
  }

  @Test
  void showsWhatHappenedToCarriedOverTasksDuringTheMeeting() {
    String started = addTask(firstItem, "Started", boId, "2026-12-01");
    String finished = addTask(firstItem, "Finished", boId, "2026-12-02");
    String untouched = addTask(firstItem, "Untouched", boId, "2026-12-03");
    assertThat(close(firstMeeting, "UTC")).hasStatusOk();
    String second = startMeeting("2026-11-02T09:00:00Z");

    assertThat(setStatus(started, "IN_PROGRESS")).hasStatusOk();
    assertThat(setStatus(finished, "IN_PROGRESS")).hasStatusOk();
    assertThat(setStatus(finished, "DONE")).hasStatusOk();
    assertThat(close(second, "UTC")).hasStatusOk();

    var body = body(minutes(ana, second));
    assertThat(commitment(body, started, "statusBefore")).isEqualTo("OPEN");
    assertThat(commitment(body, started, "statusAtClose")).isEqualTo("IN_PROGRESS");
    assertThat(commitment(body, finished, "statusBefore")).isEqualTo("OPEN");
    assertThat(commitment(body, finished, "statusAtClose")).isEqualTo("DONE");
    assertThat(commitment(body, untouched, "statusBefore")).isEqualTo("OPEN");
    assertThat(commitment(body, untouched, "statusAtClose")).isEqualTo("OPEN");
  }

  @Test
  void countsOverdueDaysToTheMeetingsDateInTheOrganizersTimeZone() {
    String late = addTask(firstItem, "Late", boId, "2026-11-02");
    String finished = addTask(firstItem, "Late but done", boId, "2026-11-02");
    String onTime = addTask(firstItem, "On time", boId, "2026-11-03");
    assertThat(close(firstMeeting, "UTC")).hasStatusOk();
    assertThat(setStatus(finished, "DONE")).hasStatusOk();
    // 23:30 UTC on the 2nd is already the 3rd in Madrid. The task that's done isn't on this list.
    String second = startMeeting("2026-11-02T23:30:00Z");

    assertThat(close(second, "Europe/Madrid")).hasStatusOk();

    var body = body(minutes(ana, second));
    assertThat(JsonPath.<String>read(body, "$.meetingDate")).isEqualTo("2026-11-03");
    assertThat(commitment(body, late, "daysOverdue")).isEqualTo(1);
    assertThat(commitment(body, onTime, "daysOverdue")).isEqualTo(0);
  }

  @Test
  void usesTheDateTheMeetingWasHeldNotTheDateItWasPlannedFor() {
    String task = addTask(firstItem, "Due in between", boId, "2026-11-01");
    assertThat(close(firstMeeting, "UTC")).hasStatusOk();
    String second = startMeeting("2026-10-26T09:00:00Z");
    heldAt(second, "2026-11-03T09:00:00Z");

    assertThat(close(second, "UTC")).hasStatusOk();

    var body = body(minutes(ana, second));
    assertThat(JsonPath.<String>read(body, "$.meetingDate")).isEqualTo("2026-11-03");
    assertThat(JsonPath.<String>read(body, "$.scheduledDate")).isEqualTo("2026-10-26");
    assertThat(commitment(body, task, "daysOverdue")).isEqualTo(2);
    await().until(() -> !latestMinutesTo(boEmail, "2026-11-03").isEmpty());
  }

  @Test
  void aTimeZoneThatDoesNotExistCountsAsUtc() {
    assertThat(close(firstMeeting, "Mars/Olympus")).hasStatusOk();

    assertThat(JsonPath.<String>read(body(minutes(ana, firstMeeting)), "$.meetingDate"))
        .isEqualTo("2026-10-26");
  }

  @Test
  void closingWithoutATimeZoneStillWorks() {
    assertThat(post(ana, "/api/meetings/" + firstMeeting + "/close", "")).hasStatusOk();

    assertThat(minutes(ana, firstMeeting)).hasStatusOk();
  }

  @Test
  void keepsTheRecordWhenATaskChangesLaterAndSaysHowItStandsToday() {
    String reworded = addTask(firstItem, "Old wording", boId, "2026-12-01");
    String removed = addTask(firstItem, "Removed later", boId, "2026-12-02");
    assertThat(close(firstMeeting, "UTC")).hasStatusOk();
    startMeeting("2026-11-02T09:00:00Z");

    assertThat(reword(reworded, "New wording")).hasStatusOk();
    assertThat(setStatus(reworded, "DONE")).hasStatusOk();
    assertThat(remove(removed)).hasStatus(HttpStatus.NO_CONTENT);

    var body = body(minutes(ana, firstMeeting));
    assertThat(newTask(body, reworded, "description")).isEqualTo("Old wording");
    assertThat(newTask(body, reworded, "statusAtClose")).isEqualTo("OPEN");
    assertThat(newTask(body, reworded, "currentStatus")).isEqualTo("DONE");
    assertThat(newTask(body, reworded, "removedSince")).isEqualTo(false);
    assertThat(newTask(body, removed, "removedSince")).isEqualTo(true);
    assertThat(newTask(body, removed, "removed")).isEqualTo(false);
  }

  @Test
  void aClosedMeetingsPageSaysHowATaskReadWhenItClosed() {
    String task = addTask(firstItem, "Old wording", boId, "2026-12-01");
    assertThat(close(firstMeeting, "UTC")).hasStatusOk();
    String second = startMeeting("2026-11-02T09:00:00Z");

    assertThat(reword(task, "Second wording")).hasStatusOk();
    assertThat(reword(task, "Third wording")).hasStatusOk();

    var closedList = body(get(ana, "/api/meetings/" + firstMeeting + "/tasks"));
    assertThat(JsonPath.<String>read(closedList, "$[0].description")).isEqualTo("Third wording");
    assertThat(JsonPath.<String>read(closedList, "$[0].descriptionAtClose"))
        .isEqualTo("Old wording");
    var runningList = body(get(ana, "/api/meetings/" + second + "/carried-over-tasks"));
    assertThat(JsonPath.<String>read(runningList, "$[0].description")).isEqualTo("Third wording");
    assertThat(JsonPath.<String>read(runningList, "$[0].descriptionAtClose")).isNull();
  }

  @Test
  void aRewordingBeforeClosingIsPartOfTheRecord() {
    String task = addTask(firstItem, "Old wording", boId, "2026-12-01");
    assertThat(close(firstMeeting, "UTC")).hasStatusOk();
    String second = startMeeting("2026-11-02T09:00:00Z");
    assertThat(reword(task, "New wording")).hasStatusOk();

    assertThat(close(second, "UTC")).hasStatusOk();

    assertThat(commitment(body(minutes(ana, second)), task, "description"))
        .isEqualTo("New wording");
    var closedList = body(get(ana, "/api/meetings/" + second + "/carried-over-tasks"));
    assertThat(JsonPath.<String>read(closedList, "$[0].descriptionAtClose")).isNull();
  }

  @Test
  void showsTasksRemovedDuringTheMeetingAsRemovedAndLeavesOutMistakes() {
    String carried = addTask(firstItem, "Carried", boId, "2020-01-01");
    assertThat(close(firstMeeting, "UTC")).hasStatusOk();
    String second = startMeeting("2026-11-02T09:00:00Z");
    String item = addItem(second, "Staffing");
    String mistake = addTask(item, "A mistake", boId, "2026-12-01");

    assertThat(remove(carried)).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(remove(mistake)).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(close(second, "UTC")).hasStatusOk();

    var body = body(minutes(ana, second));
    assertThat(commitment(body, carried, "removed")).isEqualTo(true);
    assertThat(commitment(body, carried, "daysOverdue")).isEqualTo(0);
    assertThat(JsonPath.<List<String>>read(body, "$.newTasks[*].id")).isEmpty();
    var email =
        await().until(() -> latestMinutesTo(boEmail, "2026-11-02"), found -> !found.isEmpty());
    assertThat(email).contains("Removed tasks\n- Carried").doesNotContain("A mistake");
  }

  @Test
  void aMeetingThatIsNotClosedHasNoMinutes() {
    assertThat(minutes(ana, firstMeeting)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void onlyMembersOfTheTeamCanOpenTheMinutes() {
    assertThat(close(firstMeeting, "UTC")).hasStatusOk();

    assertThat(minutes(bo, firstMeeting)).hasStatusOk();
    assertThat(minutes(cy, firstMeeting)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/meetings/{id}/minutes", firstMeeting).exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void someoneWhoLeftTheTeamCanNoLongerOpenTheMinutes() {
    assertThat(close(firstMeeting, "UTC")).hasStatusOk();

    jdbc.update(
        "DELETE FROM team_membership WHERE team_id = ?::uuid AND user_id = ?::uuid", teamId, boId);

    assertThat(minutes(bo, firstMeeting)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void nobodyCanEditTheMinutes() {
    assertThat(close(firstMeeting, "UTC")).hasStatusOk();
    String path = "/api/meetings/" + firstMeeting + "/minutes";

    assertThat(post(ana, path, "{}")).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(send(mvc.put().uri(path).cookie(ana), "{}"))
        .hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(send(mvc.delete().uri(path).cookie(ana), ""))
        .hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE minutes SET content = '{}' WHERE meeting_id = ?::uuid", firstMeeting))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("read-only");
  }

  @Test
  void aSecondCloseChangesNothingAndSendsNothingMore() {
    assertThat(close(firstMeeting, "UTC")).hasStatusOk();
    awaitMinutesEmailTo(anaEmail);
    awaitMinutesEmailTo(boEmail);

    assertThat(close(firstMeeting, "UTC")).hasStatus(HttpStatus.CONFLICT);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM minutes WHERE meeting_id = ?::uuid",
                Long.class,
                firstMeeting))
        .isEqualTo(1);
    assertThat(emails.countTo(anaEmail)).isEqualTo(1);
  }

  @Test
  void emailsAShortSummaryToEveryParticipant() {
    addTask(firstItem, "Renegotiate rates", boId, "2026-10-01");
    assertThat(close(firstMeeting, "UTC")).hasStatusOk();
    String second = startMeeting("2026-11-02T09:00:00Z");
    String item = addItem(second, "Holiday cover");
    addItem(second, "Returns backlog");
    assertThat(writeNotes(item, "A private note")).hasStatusOk();
    assertThat(mark(item, "COVERED")).hasStatusOk();
    assertThat(post(ana, "/api/agenda-items/" + item + "/decisions", text("Cover from Madrid")))
        .hasStatus(HttpStatus.CREATED);
    addTask(item, "Confirm the cover", anaId, "2026-12-30");

    assertThat(close(second, "UTC")).hasStatusOk();

    var email =
        await().until(() -> latestMinutesTo(boEmail, "2026-11-02"), found -> !found.isEmpty());
    assertThat(email)
        .isEqualTo(
            """
            Minutes of Weekly ops sync, Mon 2 Nov 2026.

            Decisions
            - Cover from Madrid

            New tasks
            - Confirm the cover · Ana Costa · due Wed 30 Dec 2026

            Open commitments
            - Overdue: Renegotiate rates · Bo Lind · due Thu 1 Oct 2026 · open

            Carried over (not covered)
            - Budget
            - Returns backlog

            Open the full minutes:
            http://localhost:5173/meetings/%s/minutes
            """
                .formatted(second));
    assertThat(
            await().until(() -> latestMinutesTo(anaEmail, "2026-11-02"), found -> !found.isEmpty()))
        .isEqualTo(email);
    assertThat(emails.lastTo(boEmail).subject())
        .isEqualTo("Minutes: Weekly ops sync, Mon 2 Nov 2026");
  }

  @Test
  void aFailedEmailIsRetriedOnlyForWhoeverMissedIt() {
    emails.setFailingFor(boEmail);

    assertThat(close(firstMeeting, "UTC")).hasStatusOk();

    awaitMinutesEmailTo(anaEmail);
    await().until(this::publicationFailed);
    assertThat(deliveriesOfMinutes()).isEqualTo(1);
    var partly = body(minutes(ana, firstMeeting));
    assertThat(JsonPath.<Integer>read(partly, "$.emailRecipients")).isEqualTo(2);
    assertThat(JsonPath.<Integer>read(partly, "$.emailsSent")).isEqualTo(1);

    emails.setFailingFor(null);
    incomplete.resubmitIncompletePublications(publication -> true);

    awaitMinutesEmailTo(boEmail);
    await().until(() -> deliveriesOfMinutes() == 2);
    assertThat(emails.countTo(anaEmail)).isEqualTo(1);
    assertThat(emails.countTo(boEmail)).isEqualTo(1);
    await().until(() -> !publicationFailed() && publicationCompleted());
    assertThat(JsonPath.<Integer>read(body(minutes(ana, firstMeeting)), "$.emailsSent"))
        .isEqualTo(2);
  }

  @Test
  void aFailureNamesNoRecipient() {
    var email =
        new MinutesEmail(
            UUID.randomUUID(),
            "Weekly ops sync",
            LocalDate.of(2026, 11, 2),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(boEmail));
    emails.setFailingFor(boEmail);

    assertThatThrownBy(() -> mailer.send(email))
        .isInstanceOf(IllegalStateException.class)
        .hasNoCause()
        .hasMessageContaining(email.meetingId().toString())
        .hasMessageContaining("IllegalStateException")
        .hasMessageNotContaining(boEmail);
  }

  @Test
  void aFailedEmailDoesNotStopTheMeetingFromClosing() {
    emails.setFailingFor(boEmail);

    assertThat(close(firstMeeting, "UTC")).hasStatusOk();

    assertThat(JsonPath.<String>read(body(get(ana, "/api/meetings/" + firstMeeting)), "$.status"))
        .isEqualTo("CLOSED");
    await().until(this::publicationFailed);
  }

  @Test
  void theEventTableAndEmailRecordsHoldNoMeetingContent() {
    addTask(firstItem, "A secret task", boId, "2026-12-01");

    assertThat(close(firstMeeting, "UTC")).hasStatusOk();

    awaitMinutesEmailTo(boEmail);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_publication"
                    + " WHERE serialized_event LIKE '%' || ? || '%'"
                    + " AND serialized_event LIKE '%secret%'",
                Long.class, firstMeeting))
        .isZero();
  }

  private boolean publicationFailed() {
    return requireNonNull(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?"
                    + " AND status = 'FAILED'",
                Long.class,
                "%" + firstMeeting + "%"))
        > 0;
  }

  private boolean publicationCompleted() {
    return requireNonNull(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?"
                    + " AND completion_date IS NOT NULL",
                Long.class,
                "%" + firstMeeting + "%"))
        > 0;
  }

  private long deliveriesOfMinutes() {
    return requireNonNull(
        jdbc.queryForObject(
            "SELECT count(*) FROM email_delivery WHERE kind = 'MINUTES'"
                + " AND reference_id = ?::uuid",
            Long.class,
            firstMeeting));
  }

  private void awaitMinutesEmailTo(String address) {
    await().until(() -> emails.countTo(address) > 0);
  }

  /** The text of the latest email to this address, once it is about that meeting's date. */
  private String latestMinutesTo(String address, String isoDate) {
    if (emails.countTo(address) == 0) {
      return "";
    }
    var day = Integer.parseInt(isoDate.substring(8));
    var email = emails.lastTo(address);
    return email.subject().contains(" " + day + " Nov 2026") ? email.text() : "";
  }

  private static Object commitment(String minutes, String taskId, String field) {
    return first(minutes, "commitments", taskId, field);
  }

  private static Object newTask(String minutes, String taskId, String field) {
    return first(minutes, "newTasks", taskId, field);
  }

  private static Object first(String minutes, String list, String taskId, String field) {
    List<Object> found =
        JsonPath.read(minutes, "$.%s[?(@.id == '%s')].%s".formatted(list, taskId, field));
    return found.getFirst();
  }

  private String startMeeting(String scheduledAt) {
    String meeting =
        idOf(
            post(
                ana,
                "/api/meeting-series/" + seriesId + "/meetings",
                "{\"scheduledAt\": \"%s\", \"participantIds\": [\"%s\"]}"
                    .formatted(scheduledAt, boId)));
    assertThat(post(ana, "/api/meetings/" + meeting + "/start", "")).hasStatusOk();
    // Held exactly when planned, unless a test says otherwise.
    heldAt(meeting, scheduledAt);
    return meeting;
  }

  private void heldAt(String meetingId, String startedAt) {
    jdbc.update(
        "UPDATE meeting SET started_at = ?::timestamptz WHERE id = ?::uuid", startedAt, meetingId);
  }

  private String addItem(String meetingId, String title) {
    return idOf(
        post(
            ana, "/api/meetings/" + meetingId + "/agenda-items", "{\"title\": \"" + title + "\"}"));
  }

  private String addTask(String itemId, String description, String ownerId, String deadline) {
    return idOf(
        post(
            ana,
            "/api/agenda-items/" + itemId + "/tasks",
            "{\"description\": \"%s\", \"ownerId\": \"%s\", \"deadline\": \"%s\"}"
                .formatted(description, ownerId, deadline)));
  }

  private static String text(String text) {
    return "{\"text\": \"" + text + "\"}";
  }

  private MvcTestResult writeNotes(String itemId, String notes) {
    return send(
        mvc.put().uri("/api/agenda-items/{id}/notes", itemId).cookie(ana),
        "{\"notes\": \"" + notes + "\"}");
  }

  private MvcTestResult mark(String itemId, String status) {
    return send(
        mvc.put().uri("/api/agenda-items/{id}/status", itemId).cookie(ana),
        "{\"status\": \"" + status + "\"}");
  }

  private MvcTestResult setStatus(String taskId, String status) {
    return send(
        mvc.put().uri("/api/tasks/{id}/status", taskId).cookie(ana),
        "{\"status\": \"" + status + "\"}");
  }

  private MvcTestResult reword(String taskId, String description) {
    return send(
        mvc.put().uri("/api/tasks/{id}/description", taskId).cookie(ana),
        "{\"description\": \"" + description + "\"}");
  }

  private MvcTestResult remove(String taskId) {
    return send(mvc.delete().uri("/api/tasks/{id}", taskId).cookie(ana), "");
  }

  private MvcTestResult close(String meetingId, String timeZone) {
    return post(
        ana, "/api/meetings/" + meetingId + "/close", "{\"timeZone\": \"" + timeZone + "\"}");
  }

  private MvcTestResult minutes(Cookie session, String meetingId) {
    return get(session, "/api/meetings/" + meetingId + "/minutes");
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
  private Cookie signUp(String name, String email) {
    var json =
        """
        {"name": "%s", "email": "%s", "password": "correct horse battery"}"""
            .formatted(name, email);
    var response = send(mvc.post().uri("/api/users"), json).getResponse();
    return requireNonNull(response.getCookie("SESSION"), "sign-up failed");
  }

  private String userId(Cookie session) {
    return JsonPath.read(body(get(session, "/api/users/me")), "$.id");
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
