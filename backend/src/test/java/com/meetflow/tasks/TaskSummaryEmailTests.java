package com.meetflow.tasks;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.meetflow.IntegrationTest;
import com.meetflow.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * MF-20: the email each owner gets with their tasks from a meeting, through the whole app. Ana
 * organizes a series with a running meeting, and Bo and Di take part in it. The minutes go to the
 * same people on their own, so these tests only look at emails with the task email's subject.
 */
@IntegrationTest
class TaskSummaryEmailTests {

  private static final String SUBJECT = "Your tasks from ";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  @Autowired RecordingEmailSender emails;

  @Autowired IncompleteEventPublications incomplete;

  Cookie csrfToken;

  Cookie ana;

  String anaEmail;

  String boEmail;

  String diEmail;

  String anaId;

  String boId;

  String diId;

  String teamId;

  String seriesId;

  String meeting;

  String item;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    anaEmail = "ana." + UUID.randomUUID() + "@example.com";
    boEmail = "bo." + UUID.randomUUID() + "@example.com";
    diEmail = "di." + UUID.randomUUID() + "@example.com";
    ana = signUp("Ana Costa", anaEmail);
    anaId = userId(ana);
    boId = userId(signUp("Bo Lind", boEmail));
    diId = userId(signUp("Di Moreno", diEmail));
    teamId = idOf(post(ana, "/api/teams", "{\"name\": \"Ops team\"}"));
    addToTeam(teamId, boId);
    addToTeam(teamId, diId);
    seriesId =
        idOf(
            post(
                ana,
                "/api/teams/" + teamId + "/meeting-series",
                "{\"name\": \"Weekly ops sync\"}"));
    meeting = startMeeting(seriesId, "2026-11-02T09:00:00Z", boId, diId);
    item = addItem(meeting, "Budget");
  }

  @AfterEach
  void stopFailing() {
    emails.setFailingFor(null);
  }

  @Test
  void emailsEachOwnerOnlyTheirOwnTasksWithALinkToEach() {
    String first = addTask(item, "Sign the contract", boId, "2026-11-20");
    String second = addTask(item, "Call the supplier", boId, "2026-12-01");
    addTask(item, "Book the room", diId, "2026-11-25");

    assertThat(close(meeting, "UTC")).hasStatusOk();

    await().until(() -> !emails.to(boEmail, SUBJECT).isEmpty());
    var email = emails.to(boEmail, SUBJECT).getFirst();
    assertThat(email.subject()).isEqualTo("Your tasks from Weekly ops sync, Mon 2 Nov 2026");
    assertThat(email.text())
        .isEqualTo(
            """
            These are your tasks from the meeting:

            - Sign the contract · due Fri 20 Nov 2026
              http://localhost:5173/tasks/%s
            - Call the supplier · due Tue 1 Dec 2026
              http://localhost:5173/tasks/%s

            They'll come back at the next meeting until they're done. Update them any time from \
            My tasks:
            http://localhost:5173/

            This is the only email about these tasks. MeetFlow doesn't send deadline reminders.
            """
                .formatted(first, second));
    await().until(() -> !emails.to(diEmail, SUBJECT).isEmpty());
    assertThat(emails.to(diEmail, SUBJECT).getFirst().text())
        .contains("Book the room")
        .doesNotContain("Sign the contract");
  }

  @Test
  void usesTheDateTheMeetingWasHeldInTheOrganizersTimeZone() {
    addTask(item, "Sign the contract", boId, "2026-11-20");
    // 23:30 UTC on the 2nd is already the 3rd in Madrid.
    jdbc.update(
        "UPDATE meeting SET started_at = '2026-11-02T23:30:00Z' WHERE id = ?::uuid", meeting);

    assertThat(close(meeting, "Europe/Madrid")).hasStatusOk();

    await().until(() -> !emails.to(boEmail, SUBJECT).isEmpty());
    assertThat(emails.to(boEmail, SUBJECT).getFirst().subject())
        .isEqualTo("Your tasks from Weekly ops sync, Tue 3 Nov 2026");
  }

  @Test
  void anOwnerWithNoOpenTaskFromTheMeetingGetsNoTaskEmail() {
    String finished = addTask(item, "Already finished", boId, "2026-11-20");
    assertThat(setStatus(finished, "DONE")).hasStatusOk();
    String mistake = addTask(item, "A mistake", diId, "2026-11-25");
    assertThat(remove(mistake)).hasStatus(HttpStatus.NO_CONTENT);
    String running = addTask(item, "Still running", anaId, "2026-11-25");
    assertThat(setStatus(running, "IN_PROGRESS")).hasStatusOk();

    assertThat(close(meeting, "UTC")).hasStatusOk();

    // Ana's in-progress task shows the listener has run, so nothing more will arrive.
    await().until(() -> !emails.to(anaEmail, SUBJECT).isEmpty());
    assertThat(emails.to(anaEmail, SUBJECT).getFirst().text()).contains("Still running");
    assertThat(emails.to(boEmail, SUBJECT)).isEmpty();
    assertThat(emails.to(diEmail, SUBJECT)).isEmpty();
  }

  @Test
  void aMeetingWithoutTasksSendsNoTaskEmail() {
    assertThat(close(meeting, "UTC")).hasStatusOk();

    await().until(() -> !emails.to(boEmail, "Minutes: ").isEmpty());
    await().until(() -> taskEmailPublicationsPending() == 0);
    assertThat(emails.to(anaEmail, SUBJECT)).isEmpty();
    assertThat(emails.to(boEmail, SUBJECT)).isEmpty();
  }

  @Test
  void leavesOutCarriedOverTasksAndTheRestOfTheOwnersWork() {
    addTask(item, "From the first meeting", boId, "2026-11-20");
    assertThat(close(meeting, "UTC")).hasStatusOk();
    await().until(() -> !emails.to(boEmail, SUBJECT).isEmpty());
    // Another team of Bo's, whose tasks mustn't reach this team's email.
    String otherTeam = idOf(post(ana, "/api/teams", "{\"name\": \"Secret team\"}"));
    addToTeam(otherTeam, boId);
    String otherSeries =
        idOf(
            post(
                ana, "/api/teams/" + otherTeam + "/meeting-series", "{\"name\": \"Secret sync\"}"));
    String otherMeeting = startMeeting(otherSeries, "2026-11-09T09:00:00Z", boId);
    addTask(addItem(otherMeeting, "Secrets"), "A secret task", boId, "2026-12-15");
    String second = startMeeting(seriesId, "2026-11-09T09:00:00Z", boId);
    addTask(addItem(second, "Staffing"), "From the second meeting", boId, "2026-12-01");

    assertThat(close(second, "UTC")).hasStatusOk();

    await().until(() -> emails.to(boEmail, SUBJECT).size() == 2);
    assertThat(emails.to(boEmail, SUBJECT))
        .filteredOn(email -> email.subject().contains("9 Nov 2026"))
        .singleElement()
        .satisfies(
            email ->
                assertThat(email.text())
                    .contains("From the second meeting")
                    .doesNotContain("From the first meeting")
                    .doesNotContain("secret"));
  }

  @Test
  void anOwnerGivenTheTaskDuringTheMeetingGetsItEvenIfNotAParticipant() {
    String outsiderEmail = "ed." + UUID.randomUUID() + "@example.com";
    String outsiderId = userId(signUp("Ed Quinn", outsiderEmail));
    addToTeam(teamId, outsiderId);
    String task = addTask(item, "Handed over", boId, "2026-11-20");
    assertThat(
            send(
                mvc.put().uri("/api/tasks/{id}/owner", task).cookie(ana),
                "{\"ownerId\": \"" + outsiderId + "\"}"))
        .hasStatusOk();

    assertThat(close(meeting, "UTC")).hasStatusOk();

    await().until(() -> !emails.to(outsiderEmail, SUBJECT).isEmpty());
    assertThat(emails.to(boEmail, SUBJECT)).isEmpty();
  }

  @Test
  void aFailedEmailIsRetriedOnlyForWhoeverMissedIt() {
    addTask(item, "For Bo", boId, "2026-11-20");
    addTask(item, "For Di", diId, "2026-11-25");
    emails.setFailingFor(boEmail);

    assertThat(close(meeting, "UTC")).hasStatusOk();

    await().until(() -> !emails.to(diEmail, SUBJECT).isEmpty());
    await().until(() -> taskEmailDeliveries() == 1 && failedPublications() > 0);
    assertThat(taskEmailsSent()).isEqualTo(1);

    emails.setFailingFor(null);
    incomplete.resubmitIncompletePublications(publication -> true);

    await().until(() -> emails.to(boEmail, SUBJECT).size() == 1);
    await().until(() -> taskEmailDeliveries() == 2 && failedPublications() == 0);
    assertThat(emails.to(diEmail, SUBJECT)).hasSize(1);
    assertThat(taskEmailsSent()).isEqualTo(2);
  }

  @Test
  void countsOwnersNotTasksAndNotTheMinutes() {
    addTask(item, "Sign the contract", boId, "2026-11-20");
    addTask(item, "Call the supplier", boId, "2026-12-01");
    assertThat(taskEmailsSent()).isZero();

    assertThat(close(meeting, "UTC")).hasStatusOk();

    await().until(() -> !emails.to(boEmail, SUBJECT).isEmpty() && taskEmailDeliveries() == 1);
    await().until(() -> !emails.to(diEmail, "Minutes: ").isEmpty());
    assertThat(taskEmailsSent()).isEqualTo(1);
  }

  @Test
  void onlyMembersOfTheTeamCanSeeTheCount() {
    Cookie outsider = signUp("Cy Park", "cy." + UUID.randomUUID() + "@example.com");

    assertThat(mvc.get().uri("/api/meetings/{id}/task-emails", meeting).cookie(outsider).exchange())
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void aSecondCloseSendsNoSecondTaskEmail() {
    addTask(item, "Sign the contract", boId, "2026-11-20");
    assertThat(close(meeting, "UTC")).hasStatusOk();
    await().until(() -> !emails.to(boEmail, SUBJECT).isEmpty());

    assertThat(close(meeting, "UTC")).hasStatus(HttpStatus.CONFLICT);

    assertThat(emails.to(boEmail, SUBJECT)).hasSize(1);
    assertThat(taskEmailDeliveries()).isEqualTo(1);
  }

  private int taskEmailsSent() {
    var result = mvc.get().uri("/api/meetings/{id}/task-emails", meeting).cookie(ana).exchange();
    assertThat(result).hasStatusOk();
    return JsonPath.<Integer>read(body(result), "$.sent");
  }

  private long taskEmailDeliveries() {
    return requireNonNull(
        jdbc.queryForObject(
            "SELECT count(*) FROM email_delivery WHERE kind = 'TASK_SUMMARY'"
                + " AND reference_id = ?::uuid",
            Long.class,
            meeting));
  }

  private long failedPublications() {
    return requireNonNull(
        jdbc.queryForObject(
            "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?"
                + " AND status = 'FAILED'",
            Long.class,
            "%" + meeting + "%"));
  }

  private long taskEmailPublicationsPending() {
    return requireNonNull(
        jdbc.queryForObject(
            "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?"
                + " AND listener_id LIKE '%TaskSummaryListener%' AND completion_date IS NULL",
            Long.class, "%" + meeting + "%"));
  }

  private void addToTeam(String team, String userId) {
    jdbc.update(
        "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
            + " VALUES (?::uuid, ?::uuid, 'MEMBER', now())",
        team,
        userId);
  }

  private String startMeeting(String series, String scheduledAt, String... participantIds) {
    String participants = "\"" + String.join("\", \"", participantIds) + "\"";
    String id =
        idOf(
            post(
                ana,
                "/api/meeting-series/" + series + "/meetings",
                "{\"scheduledAt\": \"%s\", \"participantIds\": [%s]}"
                    .formatted(scheduledAt, participants)));
    assertThat(post(ana, "/api/meetings/" + id + "/start", "")).hasStatusOk();
    jdbc.update(
        "UPDATE meeting SET started_at = ?::timestamptz WHERE id = ?::uuid", scheduledAt, id);
    return id;
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

  private MvcTestResult setStatus(String taskId, String status) {
    return send(
        mvc.put().uri("/api/tasks/{id}/status", taskId).cookie(ana),
        "{\"status\": \"" + status + "\"}");
  }

  private MvcTestResult remove(String taskId) {
    return send(mvc.delete().uri("/api/tasks/{id}", taskId).cookie(ana), "");
  }

  private MvcTestResult close(String meetingId, String timeZone) {
    return post(
        ana, "/api/meetings/" + meetingId + "/close", "{\"timeZone\": \"" + timeZone + "\"}");
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
    return JsonPath.read(body(mvc.get().uri("/api/users/me").cookie(session).exchange()), "$.id");
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
