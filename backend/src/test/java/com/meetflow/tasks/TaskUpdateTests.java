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
 * MF-18: keeping a task up to date, through the whole app. Ana creates the team and organizes the
 * series; the task belongs to Bo. Di is a member who neither owns the task nor is at the meeting,
 * and Cy is in another team. The meeting is running unless a test closes it.
 */
@IntegrationTest
class TaskUpdateTests {

  private static final String DEADLINE = "2026-11-20";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  Cookie csrfToken;

  Cookie ana;

  Cookie bo;

  Cookie cy;

  Cookie di;

  String boId;

  String diId;

  String seriesId;

  String meetingId;

  String budget;

  String decision;

  String task;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa");
    bo = signUp("Bo Lind");
    cy = signUp("Cy Park");
    di = signUp("Di Vega");
    boId = userId(bo);
    diId = userId(di);
    String teamId = idOf(post(ana, "/api/teams", "{\"name\": \"Ops team\"}"));
    for (String member : List.of(boId, diId)) {
      jdbc.update(
          "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
              + " VALUES (?::uuid, ?::uuid, 'MEMBER', now())",
          teamId,
          member);
    }
    seriesId =
        idOf(
            post(
                ana,
                "/api/teams/" + teamId + "/meeting-series",
                "{\"name\": \"Weekly ops sync\"}"));
    meetingId = scheduleAndStart();
    budget =
        idOf(post(ana, "/api/meetings/" + meetingId + "/agenda-items", "{\"title\": \"Budget\"}"));
    decision = addDecision("Hire 6 pickers");
    task = fromDecision(decision, "Sign the contract");
  }

  @Test
  void everyMemberReadsATask() {
    assertThat(get(di, "/api/tasks/" + task))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"id": "%s", "seriesId": "%s", "meetingId": "%s", "decisionId": "%s",
             "description": "Sign the contract", "ownerName": "Bo Lind", "status": "OPEN"}"""
                .formatted(task, seriesId, meetingId, decision));
  }

  @Test
  void theOwnerMovesTheirTaskThroughItsStatusesAndReopensIt() {
    assertThat(status(bo, "IN_PROGRESS"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("IN_PROGRESS");
    assertThat(status(bo, "DONE")).hasStatusOk();
    assertThat(status(bo, "OPEN")).hasStatusOk();

    assertThat(body(get(di, "/api/tasks/" + task))).contains("\"status\":\"OPEN\"");
  }

  @Test
  void theOrganizerChangesTheStatusOwnerAndDeadline() {
    assertThat(status(ana, "DONE")).hasStatusOk();
    assertThat(owner(ana, diId))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("{\"ownerId\": \"%s\", \"ownerName\": \"Di Vega\"}".formatted(diId));
    assertThat(deadline(ana, "2026-12-01"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.deadline")
        .isEqualTo("2026-12-01");
  }

  @Test
  void theOwnerCantChangeTheOwnerOrDeadline() {
    assertThat(owner(bo, diId)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(deadline(bo, "2026-12-01")).hasStatus(HttpStatus.FORBIDDEN);

    assertThat(body(get(bo, "/api/tasks/" + task)))
        .contains("\"ownerId\":\"" + boId + "\"")
        .contains("\"deadline\":\"" + DEADLINE + "\"");
  }

  @Test
  void otherMembersCantChangeATask() {
    assertThat(status(di, "DONE")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(owner(di, diId)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(deadline(di, "2026-12-01")).hasStatus(HttpStatus.FORBIDDEN);

    assertThat(body(get(ana, "/api/tasks/" + task))).contains("\"status\":\"OPEN\"");
  }

  @Test
  void aTaskFollowsItsNewOwner() {
    owner(ana, diId);

    assertThat(status(di, "DONE")).hasStatusOk();
    assertThat(status(bo, "OPEN")).hasStatus(HttpStatus.FORBIDDEN);
  }

  @Test
  void organizerRightsFollowTheSeriesNotWhoCreatedTheTask() {
    assertThat(
            send(
                mvc.put().uri("/api/meeting-series/{id}/organizer", seriesId).cookie(ana),
                "{\"organizerId\": \"%s\"}".formatted(diId)))
        .hasStatusOk();

    assertThat(status(ana, "DONE")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(deadline(di, "2026-12-01")).hasStatusOk();
  }

  @Test
  void theNewOwnerMustBeATeamMemberButNeedntBeAtTheMeeting() {
    assertThat(owner(ana, userId(cy))).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(owner(ana, UUID.randomUUID().toString())).hasStatus(HttpStatus.BAD_REQUEST);

    assertThat(owner(ana, diId)).hasStatusOk();
  }

  @Test
  void rejectsInvalidChanges() {
    assertThat(status(bo, "OVERDUE")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(send(put(bo, "status"), "{}")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(send(put(ana, "owner"), "{}")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(deadline(ana, "next Friday")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(reword(ana, "   ")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(reword(ana, "x".repeat(501))).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void theHistoryRecordsEveryChangeWithWhoAndWhenLatestFirst() {
    status(bo, "IN_PROGRESS");
    owner(ana, diId);
    deadline(ana, "2026-12-01");

    assertThat(get(di, "/api/tasks/" + task + "/changes"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            [{"changedByName": "Ana Costa", "duringMeeting": true,
              "oldDeadline": "%s", "newDeadline": "2026-12-01",
              "oldStatus": null, "newOwnerName": null},
             {"changedByName": "Ana Costa", "oldOwnerName": "Bo Lind", "newOwnerName": "Di Vega",
              "newStatus": null},
             {"changedByName": "Bo Lind", "oldStatus": "OPEN", "newStatus": "IN_PROGRESS"},
             {"changedByName": "Ana Costa", "oldStatus": null, "newStatus": "OPEN",
              "oldOwnerName": null, "newOwnerName": "Bo Lind", "newDeadline": "%s"}]"""
                .formatted(DEADLINE, DEADLINE));
  }

  @Test
  void choosingTheCurrentValueChangesNothing() {
    assertThat(status(bo, "OPEN")).hasStatusOk();
    assertThat(owner(ana, boId)).hasStatusOk();
    assertThat(deadline(ana, DEADLINE)).hasStatusOk();

    assertThat(historySize()).isEqualTo(1);
  }

  @Test
  void updatesWorkBetweenMeetingsAndAfterClosing() {
    close();

    assertThat(status(bo, "DONE")).hasStatusOk();
    assertThat(status(bo, "OPEN")).hasStatusOk();
    assertThat(owner(ana, diId)).hasStatusOk();
    assertThat(deadline(ana, "2026-12-01")).hasStatusOk();

    assertThat(body(get(bo, "/api/tasks/" + task + "/changes")))
        .contains("\"duringMeeting\":false");
  }

  @Test
  void aChangeIsTiedToTheMeetingRunningWhenItWasMadeEvenALaterOne() {
    status(bo, "IN_PROGRESS");
    assertThat(meetingOfLatestChange()).isEqualTo(meetingId);

    close();
    status(bo, "DONE");
    assertThat(meetingOfLatestChange()).isNull();

    String next = scheduleAndStart();
    status(bo, "OPEN");
    assertThat(meetingOfLatestChange()).isEqualTo(next);
  }

  @Test
  void theOrganizerRewordsATaskWhileItsMeetingRunsWithoutTouchingTheHistory() {
    assertThat(reword(ana, "  Sign the Temporis contract  "))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.description")
        .isEqualTo("Sign the Temporis contract");

    assertThat(body(get(bo, "/api/tasks/" + task))).contains("Sign the Temporis contract");
    assertThat(historySize()).isEqualTo(1);
  }

  @Test
  void onlyTheOrganizerRewordsOrDeletesEvenTheOwnerCant() {
    assertThat(reword(bo, "Mine")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(reword(di, "Mine")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(delete(bo)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(delete(di)).hasStatus(HttpStatus.FORBIDDEN);

    assertThat(body(get(ana, "/api/tasks/" + task))).contains("Sign the contract");
  }

  @Test
  void aTaskCantBeRewordedBetweenMeetings() {
    close();

    assertThat(reword(ana, "Too late")).hasStatus(HttpStatus.CONFLICT);

    // A scheduled meeting that hasn't started doesn't count.
    post(
        ana,
        "/api/meeting-series/" + seriesId + "/meetings",
        "{\"scheduledAt\": \"2026-11-09T08:30:00Z\", \"participantIds\": []}");
    assertThat(reword(ana, "Too late")).hasStatus(HttpStatus.CONFLICT);

    assertThat(body(get(ana, "/api/tasks/" + task))).contains("Sign the contract");
  }

  @Test
  void theOrganizerRewordsATaskDuringALaterMeetingAndTheHistoryRecordsIt() {
    close();
    String next = scheduleAndStart();

    assertThat(reword(ana, " Sign the Temporis contract "))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.description")
        .isEqualTo("Sign the Temporis contract");

    assertThat(get(bo, "/api/tasks/" + task + "/changes"))
        .bodyJson()
        .isLenientlyEqualTo(
            """
            [{"changedByName": "Ana Costa", "duringMeeting": true,
              "oldDescription": "Sign the contract",
              "newDescription": "Sign the Temporis contract",
              "newStatus": null, "newOwnerName": null, "newDeadline": null},
             {"oldDescription": null, "newDescription": null, "newStatus": "OPEN"}]""");
    assertThat(meetingOfLatestChange()).isEqualTo(next);
  }

  @Test
  void rewordingWithTheSameWordsDuringALaterMeetingChangesNothing() {
    close();
    scheduleAndStart();

    assertThat(reword(ana, "Sign the contract")).hasStatusOk();

    assertThat(historySize()).isEqualTo(1);
  }

  @Test
  void onlyTheOrganizerRewordsDuringALaterMeeting() {
    close();
    scheduleAndStart();

    assertThat(reword(bo, "Mine")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(reword(di, "Mine")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(reword(cy, "Mine")).hasStatus(HttpStatus.NOT_FOUND);

    assertThat(body(get(ana, "/api/tasks/" + task))).contains("Sign the contract");
  }

  @Test
  void aTaskCantBeDeletedBetweenMeetings() {
    close();

    assertThat(delete(ana)).hasStatus(HttpStatus.CONFLICT);

    // A scheduled meeting that hasn't started doesn't count.
    post(
        ana,
        "/api/meeting-series/" + seriesId + "/meetings",
        "{\"scheduledAt\": \"2026-11-09T08:30:00Z\", \"participantIds\": []}");
    assertThat(delete(ana)).hasStatus(HttpStatus.CONFLICT);

    assertThat(body(get(ana, "/api/tasks/" + task))).contains("Sign the contract");
  }

  @Test
  void theOrganizerDeletesATaskDuringALaterMeeting() {
    close();
    String next = scheduleAndStart();

    assertThat(delete(bo)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(delete(ana)).hasStatus(HttpStatus.NO_CONTENT);

    var row =
        jdbc.queryForMap(
            "SELECT deleted_by::text AS by, deleted_in_meeting_id::text AS meeting"
                + " FROM task WHERE id = ?::uuid",
            task);
    assertThat(row.get("by")).isEqualTo(userId(ana));
    assertThat(row.get("meeting")).isEqualTo(next);
  }

  @Test
  void aTaskDeletedDuringALaterMeetingStaysOnItsClosedMeetingUnderItsDecision() {
    close();
    scheduleAndStart();
    delete(ana);

    String list = body(get(ana, "/api/meetings/" + meetingId + "/tasks"));
    assertThat(JsonPath.<String>read(list, "$[0].id")).isEqualTo(task);
    assertThat(JsonPath.<String>read(list, "$[0].decisionId")).isEqualTo(decision);
    assertThat(JsonPath.<String>read(list, "$[0].deletion.byName")).isEqualTo("Ana Costa");
    assertThat(JsonPath.<String>read(list, "$[0].deletion.meetingScheduledAt"))
        .isEqualTo("2026-11-02T08:30:00Z");

    // It can be read by the team, but no longer changed.
    String read = body(get(bo, "/api/tasks/" + task));
    assertThat(JsonPath.<String>read(read, "$.deletion.byName")).isEqualTo("Ana Costa");
    assertThat(status(ana, "DONE")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(reword(ana, "Back")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(delete(ana)).hasStatus(HttpStatus.NOT_FOUND);

    assertThat(get(cy, "/api/tasks/" + task)).hasStatus(HttpStatus.NOT_FOUND);

    String history = body(get(ana, "/api/tasks/" + task + "/changes"));
    assertThat(JsonPath.<Boolean>read(history, "$[0].deleted")).isTrue();
    assertThat(JsonPath.<Boolean>read(history, "$[0].duringMeeting")).isTrue();
  }

  @Test
  void aTaskDeletedDuringItsOwnMeetingRecordsThatMeeting() {
    assertThat(delete(ana)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(
            jdbc.queryForObject(
                "SELECT deleted_in_meeting_id::text FROM task WHERE id = ?::uuid",
                String.class,
                task))
        .isEqualTo(meetingId);
  }

  @Test
  void aDeletedTaskIsHiddenButKeptWithItsHistory() {
    assertThat(delete(ana)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(get(ana, "/api/tasks/" + task)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(get(ana, "/api/tasks/" + task + "/changes")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(status(ana, "DONE")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(delete(ana)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(body(get(ana, "/api/meetings/" + meetingId + "/tasks"))).isEqualTo("[]");

    var row =
        jdbc.queryForMap(
            "SELECT deleted_at, deleted_by::text AS by FROM task WHERE id = ?::uuid", task);
    assertThat(row.get("deleted_at")).isNotNull();
    assertThat(row.get("by")).isEqualTo(userId(ana));
    // The creation and the deletion.
    assertThat(historySize()).isEqualTo(2);
  }

  @Test
  void aDecisionCanBeRemovedOnceAllItsTasksAreDeleted() {
    String second = fromDecision(decision, "Announce it");

    delete(ana);
    assertThat(removeDecision()).hasStatus(HttpStatus.CONFLICT);

    assertThat(send(mvc.delete().uri("/api/tasks/{id}", second).cookie(ana), ""))
        .hasStatus(HttpStatus.NO_CONTENT);
    assertThat(removeDecision()).hasStatus(HttpStatus.NO_CONTENT);
  }

  @Test
  void outsidersGetNotFoundForEverything() {
    assertThat(get(cy, "/api/tasks/" + task)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(get(cy, "/api/tasks/" + task + "/changes")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(status(cy, "DONE")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(owner(cy, userId(cy))).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(deadline(cy, "2026-12-01")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(reword(cy, "Mine")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(delete(cy)).hasStatus(HttpStatus.NOT_FOUND);

    // The answer is the same as for a task that doesn't exist, so nothing is revealed.
    String path = "/api/tasks/" + task;
    assertThat(body(get(cy, path)))
        .isEqualTo(
            body(get(cy, "/api/tasks/" + UUID.randomUUID())).replaceAll("/api/tasks/[^\"]+", path));

    assertThat(body(get(ana, path))).contains("Sign the contract").contains("\"status\":\"OPEN\"");
  }

  @Test
  void requestsWithoutASessionGetUnauthorized() {
    assertThat(mvc.get().uri("/api/tasks/{id}", task).exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.get().uri("/api/tasks/{id}/changes", task).exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  private String scheduleAndStart() {
    String meeting =
        idOf(
            post(
                ana,
                "/api/meeting-series/" + seriesId + "/meetings",
                "{\"scheduledAt\": \"2026-11-02T08:30:00Z\", \"participantIds\": [\"%s\"]}"
                    .formatted(boId)));
    assertThat(post(ana, "/api/meetings/" + meeting + "/start", "")).hasStatusOk();
    return meeting;
  }

  private void close() {
    assertThat(post(ana, "/api/meetings/" + meetingId + "/close", "")).hasStatusOk();
  }

  private String addDecision(String text) {
    return idOf(
        post(ana, "/api/agenda-items/" + budget + "/decisions", "{\"text\": \"" + text + "\"}"));
  }

  private String fromDecision(String decisionId, String description) {
    return idOf(
        post(
            ana,
            "/api/decisions/" + decisionId + "/tasks",
            "{\"description\": \"%s\", \"ownerId\": \"%s\", \"deadline\": \"%s\"}"
                .formatted(description, boId, DEADLINE)));
  }

  private MvcTestResult removeDecision() {
    return send(mvc.delete().uri("/api/decisions/{id}", decision).cookie(ana), "");
  }

  private MockMvcRequestBuilder put(Cookie session, String field) {
    return mvc.put().uri("/api/tasks/{id}/" + field, task).cookie(session);
  }

  private MvcTestResult status(Cookie session, String value) {
    return send(put(session, "status"), "{\"status\": \"" + value + "\"}");
  }

  private MvcTestResult owner(Cookie session, String ownerId) {
    return send(put(session, "owner"), "{\"ownerId\": \"" + ownerId + "\"}");
  }

  private MvcTestResult deadline(Cookie session, String value) {
    return send(put(session, "deadline"), "{\"deadline\": \"" + value + "\"}");
  }

  private MvcTestResult reword(Cookie session, String description) {
    return send(put(session, "description"), "{\"description\": \"" + description + "\"}");
  }

  private MvcTestResult delete(Cookie session) {
    return send(mvc.delete().uri("/api/tasks/{id}", task).cookie(session), "");
  }

  private MvcTestResult get(Cookie session, String path) {
    return mvc.get().uri(path).cookie(session).exchange();
  }

  private @Nullable Long historySize() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM task_change WHERE task_id = ?::uuid", Long.class, task);
  }

  private @Nullable String meetingOfLatestChange() {
    return jdbc.queryForObject(
        "SELECT meeting_id::text FROM task_change WHERE task_id = ?::uuid"
            + " ORDER BY changed_at DESC LIMIT 1",
        String.class,
        task);
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

  private String userId(Cookie session) {
    return JsonPath.read(body(get(session, "/api/users/me")), "$.id");
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
