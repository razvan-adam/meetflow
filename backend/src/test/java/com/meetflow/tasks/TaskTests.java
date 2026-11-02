package com.meetflow.tasks;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.meetflow.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;
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
 * MF-17: creating tasks during a meeting, through the whole app. Ana creates the team and organizes
 * the series and its meeting; Bo is a member and a participant, and Di a member who isn't invited
 * to the meeting, both added directly in the database. Cy is in another team.
 */
@IntegrationTest
class TaskTests {

  private static final String DEADLINE = "2026-11-20";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  Cookie csrfToken;

  Cookie ana;

  Cookie bo;

  Cookie cy;

  String boId;

  String diId;

  String seriesId;

  String meetingId;

  String budget;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa");
    bo = signUp("Bo Lind");
    cy = signUp("Cy Park");
    boId = userId(bo);
    diId = userId(signUp("Di Vega"));
    String teamId =
        JsonPath.read(body(post(ana, "/api/teams", "{\"name\": \"Ops team\"}")), "$.id");
    for (String member : List.of(boId, diId)) {
      jdbc.update(
          "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
              + " VALUES (?::uuid, ?::uuid, 'MEMBER', now())",
          teamId,
          member);
    }
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
                    "{\"scheduledAt\": \"2026-11-02T08:30:00Z\", \"participantIds\": [\"%s\"]}"
                        .formatted(boId))),
            "$.id");
    budget =
        JsonPath.read(
            body(
                post(
                    ana,
                    "/api/meetings/" + meetingId + "/agenda-items",
                    "{\"title\": \"Budget\"}")),
            "$.id");
  }

  @Test
  void theOrganizerCreatesATaskFromAnAgendaItemAndEveryMemberReadsIt() {
    start();

    var expected =
        """
        {"agendaItemId": "%s", "decisionId": null, "description": "Send the draft",
         "ownerId": "%s", "ownerName": "Bo Lind", "deadline": "%s", "status": "OPEN"}"""
            .formatted(budget, boId, DEADLINE);
    assertThat(fromItem(ana, budget, json("  Send the draft  ", boId, DEADLINE)))
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .isLenientlyEqualTo(expected);

    assertThat(list(bo)).hasStatusOk().bodyJson().isLenientlyEqualTo("[" + expected + "]");
  }

  @Test
  void aTaskFromADecisionIsLinkedToTheDecisionAndItsItem() {
    start();
    String decision = addDecision("Hire 6 pickers");

    String task = idOf(fromDecision(ana, decision, json("Sign the contract", boId, DEADLINE)));

    assertThat(list(bo))
        .bodyJson()
        .isLenientlyEqualTo(
            "[{\"id\": \"%s\", \"agendaItemId\": \"%s\", \"decisionId\": \"%s\"}]"
                .formatted(task, budget, decision));
  }

  @Test
  void aTaskIsLinkedToItsMeetingAndSeries() {
    start();

    String task = idOf(fromItem(ana, budget, json("Send the draft", boId, DEADLINE)));

    var row =
        jdbc.queryForMap(
            "SELECT meeting_id::text AS meeting, series_id::text AS series,"
                + " created_by::text AS creator FROM task WHERE id = ?::uuid",
            task);
    assertThat(row.get("meeting")).isEqualTo(meetingId);
    assertThat(row.get("series")).isEqualTo(seriesId);
    assertThat(row.get("creator")).isEqualTo(userId(ana));
  }

  @Test
  void creatingATaskStartsItsHistory() {
    start();

    String task = idOf(fromItem(ana, budget, json("Send the draft", boId, DEADLINE)));

    var row =
        jdbc.queryForMap(
            "SELECT changed_by::text AS by, meeting_id::text AS meeting, old_status, new_status,"
                + " new_owner_id::text AS owner, new_deadline::text AS deadline"
                + " FROM task_change WHERE task_id = ?::uuid",
            task);
    assertThat(row.get("by")).isEqualTo(userId(ana));
    assertThat(row.get("meeting")).isEqualTo(meetingId);
    assertThat(row.get("old_status")).isNull();
    assertThat(row.get("new_status")).isEqualTo("OPEN");
    assertThat(row.get("owner")).isEqualTo(boId);
    assertThat(row.get("deadline")).isEqualTo(DEADLINE);
  }

  @Test
  void listsTasksInTheOrderTheyWereCreatedWithoutTheDeletedOnes() {
    start();
    fromItem(ana, budget, json("First", boId, DEADLINE));
    String second = idOf(fromItem(ana, budget, json("Second", boId, DEADLINE)));
    fromItem(ana, budget, json("Third", boId, DEADLINE));
    jdbc.update(
        "UPDATE task SET deleted_at = now(), deleted_by = owner_id WHERE id = ?::uuid", second);

    assertThat(descriptions(ana)).isEqualTo(List.of("First", "Third"));
  }

  @Test
  void aTaskNeedsADescriptionAnOwnerAndADeadline() {
    start();

    assertThat(fromItem(ana, budget, json("   ", boId, DEADLINE)))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(fromItem(ana, budget, json("x".repeat(501), boId, DEADLINE)))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(
            fromItem(
                ana,
                budget,
                "{\"description\": \"Send\", \"deadline\": \"%s\"}".formatted(DEADLINE)))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(
            fromItem(
                ana, budget, "{\"description\": \"Send\", \"ownerId\": \"%s\"}".formatted(boId)))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(fromItem(ana, budget, json("Send", boId, "next Friday")))
        .hasStatus(HttpStatus.BAD_REQUEST);

    assertThat(descriptions(ana)).isEmpty();

    assertThat(fromItem(ana, budget, json("x".repeat(500), boId, DEADLINE)))
        .hasStatus(HttpStatus.CREATED);
  }

  @Test
  void theOwnerMustBeAParticipantOfTheMeeting() {
    start();
    String decision = addDecision("Hire 6 pickers");

    assertThat(fromItem(ana, budget, json("Send", diId, DEADLINE)))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(fromDecision(ana, decision, json("Send", diId, DEADLINE)))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(fromItem(ana, budget, json("Send", UUID.randomUUID().toString(), DEADLINE)))
        .hasStatus(HttpStatus.BAD_REQUEST);

    assertThat(descriptions(ana)).isEmpty();
  }

  @Test
  void theOrganizerCanOwnATaskWithoutChoosingThemselvesAsAParticipant() {
    start();

    assertThat(fromItem(ana, budget, json("Send", userId(ana), DEADLINE)))
        .hasStatus(HttpStatus.CREATED);
  }

  @Test
  void tasksWaitForTheMeetingToStart() {
    assertThat(fromItem(ana, budget, json("Too early", boId, DEADLINE)))
        .hasStatus(HttpStatus.CONFLICT);

    assertThat(descriptions(ana)).isEmpty();
  }

  @Test
  void closingStopsNewTasksAndKeepsThemReadable() {
    start();
    String decision = addDecision("Hire 6 pickers");
    fromDecision(ana, decision, json("Sign the contract", boId, DEADLINE));
    post(ana, "/api/meetings/" + meetingId + "/close", "");

    assertThat(fromItem(ana, budget, json("Too late", boId, DEADLINE)))
        .hasStatus(HttpStatus.CONFLICT);
    assertThat(fromDecision(ana, decision, json("Too late", boId, DEADLINE)))
        .hasStatus(HttpStatus.CONFLICT);

    assertThat(descriptions(bo)).isEqualTo(List.of("Sign the contract"));
  }

  @Test
  void onlyTheOrganizerCreatesTasks() {
    start();
    String decision = addDecision("Hire 6 pickers");

    assertThat(fromItem(bo, budget, json("Mine", boId, DEADLINE))).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(fromDecision(bo, decision, json("Mine", boId, DEADLINE)))
        .hasStatus(HttpStatus.FORBIDDEN);

    assertThat(descriptions(ana)).isEmpty();
  }

  @Test
  void outsidersGetNotFoundForEverything() {
    start();
    String decision = addDecision("Hire 6 pickers");

    assertThat(list(cy)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(fromItem(cy, budget, json("Mine", boId, DEADLINE))).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(fromDecision(cy, decision, json("Mine", boId, DEADLINE)))
        .hasStatus(HttpStatus.NOT_FOUND);

    assertThat(descriptions(ana)).isEmpty();
  }

  @Test
  void anUnknownItemOrDecisionAnswersNotFound() {
    start();
    String unknown = UUID.randomUUID().toString();

    assertThat(fromItem(ana, unknown, json("Send", boId, DEADLINE)))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(fromDecision(ana, unknown, json("Send", boId, DEADLINE)))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void requestsWithoutASessionGetUnauthorized() {
    assertThat(mvc.get().uri("/api/meetings/{id}/tasks", meetingId).exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void aDecisionWithTasksCantBeRemovedButCanBeEdited() {
    start();
    String decision = addDecision("Hire 6 pickers");
    fromDecision(ana, decision, json("Sign the contract", boId, DEADLINE));

    assertThat(removeDecision(decision)).hasStatus(HttpStatus.CONFLICT);

    assertThat(
            send(
                mvc.put().uri("/api/decisions/{id}", decision).cookie(ana),
                "{\"text\": \"Hire 8 pickers\"}"))
        .hasStatusOk();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM task WHERE decision_id = ?::uuid", Long.class, decision))
        .isEqualTo(1);
  }

  @Test
  void aDecisionWithoutTasksCanStillBeRemoved() {
    start();
    String decision = addDecision("Hire 6 pickers");
    fromItem(ana, budget, json("Send the draft", boId, DEADLINE));

    assertThat(removeDecision(decision)).hasStatus(HttpStatus.NO_CONTENT);
  }

  private void start() {
    assertThat(post(ana, "/api/meetings/" + meetingId + "/start", "")).hasStatusOk();
  }

  private String addDecision(String text) {
    return idOf(
        post(ana, "/api/agenda-items/" + budget + "/decisions", "{\"text\": \"" + text + "\"}"));
  }

  private MvcTestResult removeDecision(String decisionId) {
    return send(mvc.delete().uri("/api/decisions/{id}", decisionId).cookie(ana), "");
  }

  private MvcTestResult fromItem(Cookie session, String itemId, String json) {
    return post(session, "/api/agenda-items/" + itemId + "/tasks", json);
  }

  private MvcTestResult fromDecision(Cookie session, String decisionId, String json) {
    return post(session, "/api/decisions/" + decisionId + "/tasks", json);
  }

  private MvcTestResult list(Cookie session) {
    return mvc.get().uri("/api/meetings/{id}/tasks", meetingId).cookie(session).exchange();
  }

  private List<String> descriptions(Cookie session) {
    return JsonPath.read(body(list(session)), "$[*].description");
  }

  private static String json(String description, String ownerId, String deadline) {
    return "{\"description\": \"%s\", \"ownerId\": \"%s\", \"deadline\": \"%s\"}"
        .formatted(description, ownerId, deadline);
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
