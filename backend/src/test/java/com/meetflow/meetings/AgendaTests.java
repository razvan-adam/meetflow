package com.meetflow.meetings;

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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * MF-14: preparing the agenda, through the whole app. Ana creates the team and organizes the series
 * and its meeting; Bo is a member, added directly in the database. Cy is in another team.
 */
@IntegrationTest
class AgendaTests {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  Cookie csrfToken;

  Cookie ana;

  Cookie bo;

  Cookie cy;

  String anaId;

  String teamId;

  String seriesId;

  String meetingId;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa");
    bo = signUp("Bo Lind");
    cy = signUp("Cy Park");
    anaId = userId(ana);
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
    meetingId = schedule("2026-11-02T08:30:00Z");
  }

  @Test
  void theOrganizerAddsItemsAtTheEndOfTheAgenda() {
    assertThat(add(ana, "  Budget  "))
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .isLenientlyEqualTo("{\"title\": \"Budget\", \"carriedOver\": false}");
    add(ana, "Staffing");

    assertThat(titles(ana)).isEqualTo(List.of("Budget", "Staffing"));
  }

  @Test
  void aNewItemGoesLastEvenAfterRemovals() {
    add(ana, "Budget");
    String staffing = itemId(add(ana, "Staffing"));
    remove(ana, staffing);

    add(ana, "Hiring");

    assertThat(titles(ana)).isEqualTo(List.of("Budget", "Hiring"));
  }

  @Test
  void rejectsAMissingOrTooLongTitle() {
    assertThat(add(ana, "   ")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(add(ana, "x".repeat(201))).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(add(ana, "x".repeat(200))).hasStatus(HttpStatus.CREATED);

    String item = itemId(add(ana, "Budget"));
    assertThat(rename(ana, item, " ")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void theOrganizerRenamesAnItem() {
    String item = itemId(add(ana, "Budget"));

    assertThat(rename(ana, item, " Budget 2027 "))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("{\"id\": \"%s\", \"title\": \"Budget 2027\"}".formatted(item));
    assertThat(titles(bo)).isEqualTo(List.of("Budget 2027"));
  }

  @Test
  void theOrganizerRemovesAnItem() {
    String budget = itemId(add(ana, "Budget"));
    add(ana, "Staffing");

    assertThat(remove(ana, budget)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(titles(ana)).isEqualTo(List.of("Staffing"));
    assertThat(remove(ana, budget)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void theOrganizerReordersTheAgenda() {
    String budget = itemId(add(ana, "Budget"));
    String staffing = itemId(add(ana, "Staffing"));
    String hiring = itemId(add(ana, "Hiring"));

    assertThat(reorder(ana, hiring, budget, staffing))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[*].title")
        .isEqualTo(List.of("Hiring", "Budget", "Staffing"));

    assertThat(titles(bo)).isEqualTo(List.of("Hiring", "Budget", "Staffing"));
  }

  @Test
  void aReorderMustListExactlyTheItemsOfTheAgenda() {
    String budget = itemId(add(ana, "Budget"));
    String staffing = itemId(add(ana, "Staffing"));

    assertThat(reorder(ana, staffing)).hasStatus(HttpStatus.CONFLICT);
    assertThat(reorder(ana, staffing, budget, UUID.randomUUID().toString()))
        .hasStatus(HttpStatus.CONFLICT);
    assertThat(reorder(ana, staffing, staffing)).hasStatus(HttpStatus.CONFLICT);

    assertThat(titles(ana)).isEqualTo(List.of("Budget", "Staffing"));
  }

  @Test
  void onlyTheOrganizerChangesTheAgendaButMembersReadIt() {
    String budget = itemId(add(ana, "Budget"));

    assertThat(add(bo, "Staffing")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(rename(bo, budget, "Mine")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(reorder(bo, budget)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(remove(bo, budget)).hasStatus(HttpStatus.FORBIDDEN);

    assertThat(titles(bo)).isEqualTo(List.of("Budget"));
  }

  @Test
  void outsidersGetNotFoundForEverything() {
    String budget = itemId(add(ana, "Budget"));

    assertThat(list(cy)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(add(cy, "Staffing")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(rename(cy, budget, "Mine")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(reorder(cy, budget)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(remove(cy, budget)).hasStatus(HttpStatus.NOT_FOUND);

    // The answer is the same as for an item that doesn't exist, so nothing is revealed.
    assertThat(body(rename(cy, budget, "Mine")))
        .isEqualTo(
            body(rename(cy, UUID.randomUUID().toString(), "Mine"))
                .replaceAll("/api/agenda-items/[^\"]+", "/api/agenda-items/" + budget));
    assertThat(titles(ana)).isEqualTo(List.of("Budget"));
  }

  @Test
  void theAgendaCanChangeWhileTheMeetingRuns() {
    jdbc.update(
        "UPDATE meeting SET status = 'IN_PROGRESS', started_at = now() WHERE id = ?::uuid",
        meetingId);

    assertThat(add(ana, "Raised in the meeting")).hasStatus(HttpStatus.CREATED);
  }

  @Test
  void theAgendaOfAClosedMeetingCannotChange() {
    String budget = itemId(add(ana, "Budget"));
    close(meetingId);

    assertThat(add(ana, "Staffing")).hasStatus(HttpStatus.CONFLICT);
    assertThat(rename(ana, budget, "Budget 2027")).hasStatus(HttpStatus.CONFLICT);
    assertThat(reorder(ana, budget)).hasStatus(HttpStatus.CONFLICT);
    assertThat(remove(ana, budget)).hasStatus(HttpStatus.CONFLICT);

    assertThat(titles(bo)).isEqualTo(List.of("Budget"));
  }

  @Test
  void carriedOverItemsAreMarkedAndCanBeRemoved() {
    // Carrying items over comes with MF-19; until then the test copies one in the database.
    String uncovered = itemId(add(ana, "Returns backlog"));
    close(meetingId);
    String next = schedule("2026-11-09T08:30:00Z");
    String carried = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO agenda_item"
            + " (id, team_id, meeting_id, position, title, status, carried_over_from_id, version)"
            + " VALUES (?::uuid, ?::uuid, ?::uuid, 1, 'Returns backlog', 'PENDING', ?::uuid, 0)",
        carried,
        teamId,
        next,
        uncovered);

    assertThat(mvc.get().uri("/api/meetings/{id}/agenda-items", next).cookie(bo))
        .bodyJson()
        .isLenientlyEqualTo(
            "[{\"id\": \"%s\", \"title\": \"Returns backlog\", \"carriedOver\": true}]"
                .formatted(carried));
    assertThat(remove(ana, carried)).hasStatus(HttpStatus.NO_CONTENT);
    assertThat(mvc.get().uri("/api/meetings/{id}/agenda-items", next).cookie(ana))
        .bodyJson()
        .isLenientlyEqualTo("[]");
  }

  private String schedule(String scheduledAt) {
    return JsonPath.read(
        body(
            post(
                ana,
                "/api/meeting-series/" + seriesId + "/meetings",
                "{\"scheduledAt\": \"%s\", \"participantIds\": []}".formatted(scheduledAt))),
        "$.id");
  }

  private MvcTestResult list(Cookie session) {
    return mvc.get().uri("/api/meetings/{id}/agenda-items", meetingId).cookie(session).exchange();
  }

  private List<String> titles(Cookie session) {
    return JsonPath.read(body(list(session)), "$[*].title");
  }

  private MvcTestResult add(Cookie session, String title) {
    return post(session, "/api/meetings/" + meetingId + "/agenda-items", titleJson(title));
  }

  private MvcTestResult rename(Cookie session, String itemId, String title) {
    return send(mvc.put().uri("/api/agenda-items/{id}", itemId).cookie(session), titleJson(title));
  }

  private MvcTestResult remove(Cookie session, String itemId) {
    return send(mvc.delete().uri("/api/agenda-items/{id}", itemId).cookie(session), "");
  }

  private MvcTestResult reorder(Cookie session, String... itemIds) {
    var ids =
        List.of(itemIds).stream()
            .map(id -> "\"" + id + "\"")
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    return send(
        mvc.put().uri("/api/meetings/{id}/agenda-items/order", meetingId).cookie(session),
        "{\"itemIds\": [" + ids + "]}");
  }

  private static String titleJson(String title) {
    return "{\"title\": \"" + title + "\"}";
  }

  private static String itemId(MvcTestResult created) {
    return JsonPath.read(body(created), "$.id");
  }

  // Closing a meeting comes with MF-15; until then the tests close it in the database.
  private void close(String meeting) {
    jdbc.update(
        "UPDATE meeting SET status = 'CLOSED', started_at = now(), closed_at = now(),"
            + " closed_by = ?::uuid WHERE id = ?::uuid",
        anaId,
        meeting);
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
    return body(result.getResponse());
  }

  private static String body(MockHttpServletResponse response) {
    try {
      return response.getContentAsString();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private Cookie csrfCookie() {
    var response = mvc.get().uri("/api/health").exchange().getResponse();
    return requireNonNull(response.getCookie("XSRF-TOKEN"), "no XSRF-TOKEN cookie");
  }
}
