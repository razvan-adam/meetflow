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
 * MF-21: the pending-tasks dashboard, through the whole app. Ana organizes a series in each of her
 * two teams, and Bo is a member of both.
 */
@IntegrationTest
class PendingTasksTests {

  private static final String PENDING = "/api/users/me/tasks";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  Cookie csrfToken;

  Cookie ana;

  Cookie bo;

  String anaId;

  String boId;

  String opsTeam;

  String leadershipTeam;

  String opsItem;

  String leadershipItem;

  String opsMeeting;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa");
    bo = signUp("Bo Lind");
    anaId = userId(ana);
    boId = userId(bo);
    opsTeam = createTeamWithBo("Ops team");
    leadershipTeam = createTeamWithBo("Leadership");
    opsMeeting = startMeetingIn(opsTeam, "Weekly ops sync");
    opsItem = addItem(opsMeeting);
    leadershipItem = addItem(startMeetingIn(leadershipTeam, "Monthly budget review"));
  }

  @Test
  void listsATaskWithWhereItBelongs() {
    String task = addTask(opsItem, "Sign the contract", boId, "2026-11-20");

    assertThat(get(bo, PENDING))
        .bodyJson()
        .isLenientlyEqualTo(
            """
            [{"id": "%s", "description": "Sign the contract", "deadline": "2026-11-20",
              "status": "OPEN", "teamId": "%s", "teamName": "Ops team",
              "seriesName": "Weekly ops sync", "meetingId": "%s",
              "meetingScheduledAt": "2026-11-02T08:30:00Z"}]"""
                .formatted(task, opsTeam, opsMeeting));
  }

  @Test
  void listsTasksFromEveryTeamSoonestDeadlineFirst() {
    String latest = addTask(opsItem, "Latest", boId, "2026-12-20");
    String soonest = addTask(leadershipItem, "Soonest", boId, "2026-11-01");
    String middle = addTask(opsItem, "Middle", boId, "2026-11-20");

    assertThat(pendingIds(bo)).isEqualTo(List.of(soonest, middle, latest));
  }

  @Test
  void listsOnlyTheUsersOwnTasks() {
    String mine = addTask(opsItem, "Mine", boId, "2026-11-20");
    addTask(opsItem, "Hers", anaId, "2026-11-20");

    assertThat(pendingIds(bo)).isEqualTo(List.of(mine));
  }

  @Test
  void keepsInProgressTasksAndDropsDoneOnesUntilReopened() {
    String task = addTask(opsItem, "Sign the contract", boId, "2026-11-20");

    assertThat(setStatus(task, "IN_PROGRESS")).hasStatusOk();
    assertThat(pendingIds(bo)).isEqualTo(List.of(task));

    assertThat(setStatus(task, "DONE")).hasStatusOk();
    assertThat(pendingIds(bo)).isEmpty();

    assertThat(setStatus(task, "OPEN")).hasStatusOk();
    assertThat(pendingIds(bo)).isEqualTo(List.of(task));
  }

  @Test
  void dropsRemovedTasks() {
    String task = addTask(opsItem, "Sign the contract", boId, "2026-11-20");
    String later = addTask(opsItem, "Removed in a later meeting", boId, "2026-11-20");
    assertThat(remove(task)).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(post(ana, "/api/meetings/" + opsMeeting + "/close", "")).hasStatusOk();
    String seriesId =
        JsonPath.read(body(get(ana, "/api/meetings/" + opsMeeting)), "$.seriesId").toString();
    startMeeting(seriesId);

    assertThat(remove(later)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(pendingIds(bo)).isEmpty();
  }

  @Test
  void keepsTasksFromClosedMeetings() {
    String task = addTask(opsItem, "Sign the contract", boId, "2026-11-20");

    assertThat(post(ana, "/api/meetings/" + opsMeeting + "/close", "")).hasStatusOk();

    assertThat(pendingIds(bo)).isEqualTo(List.of(task));
  }

  @Test
  void leavesOutATeamTheUserIsNoLongerIn() {
    String kept = addTask(opsItem, "Kept", boId, "2026-11-20");
    addTask(leadershipItem, "Hidden", boId, "2026-11-20");

    jdbc.update(
        "DELETE FROM team_membership WHERE team_id = ?::uuid AND user_id = ?::uuid",
        leadershipTeam,
        boId);

    assertThat(pendingIds(bo)).isEqualTo(List.of(kept));
  }

  @Test
  void aUserWithNoTasksGetsAnEmptyList() {
    assertThat(get(bo, PENDING)).hasStatusOk().bodyJson().isStrictlyEqualTo("[]");
  }

  @Test
  void requestsWithoutASessionGetUnauthorized() {
    assertThat(mvc.get().uri(PENDING).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  private List<String> pendingIds(Cookie session) {
    return JsonPath.read(body(get(session, PENDING)), "$[*].id");
  }

  private String createTeamWithBo(String name) {
    String teamId = idOf(post(ana, "/api/teams", "{\"name\": \"" + name + "\"}"));
    jdbc.update(
        "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
            + " VALUES (?::uuid, ?::uuid, 'MEMBER', now())",
        teamId,
        boId);
    return teamId;
  }

  private String startMeetingIn(String teamId, String seriesName) {
    return startMeeting(
        idOf(
            post(
                ana,
                "/api/teams/" + teamId + "/meeting-series",
                "{\"name\": \"" + seriesName + "\"}")));
  }

  private String startMeeting(String seriesId) {
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

  private String addItem(String meetingId) {
    return idOf(
        post(ana, "/api/meetings/" + meetingId + "/agenda-items", "{\"title\": \"Budget\"}"));
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
        mvc.put().uri("/api/tasks/{id}/status", taskId).cookie(bo),
        "{\"status\": \"" + status + "\"}");
  }

  private MvcTestResult remove(String taskId) {
    return send(mvc.delete().uri("/api/tasks/{id}", taskId).cookie(ana), "");
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
