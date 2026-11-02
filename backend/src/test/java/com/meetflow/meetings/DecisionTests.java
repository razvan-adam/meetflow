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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * MF-16: recording decisions, through the whole app. Ana creates the team and organizes the series
 * and its meeting; Bo is a member, added directly in the database. Cy is in another team.
 */
@IntegrationTest
class DecisionTests {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  Cookie csrfToken;

  Cookie ana;

  Cookie bo;

  Cookie cy;

  String meetingId;

  String budget;

  String staffing;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa");
    bo = signUp("Bo Lind");
    cy = signUp("Cy Park");
    String teamId =
        JsonPath.read(body(post(ana, "/api/teams", "{\"name\": \"Ops team\"}")), "$.id");
    jdbc.update(
        "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
            + " VALUES (?::uuid, ?::uuid, 'MEMBER', now())",
        teamId,
        userId(bo));
    String seriesId =
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
    staffing = addItem("Staffing");
  }

  @Test
  void theOrganizerRecordsADecisionAboutAnItemAndEveryMemberReadsIt() {
    start();

    assertThat(add(ana, budget, "  Hire 6 pickers  "))
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .isLenientlyEqualTo(
            "{\"agendaItemId\": \"%s\", \"text\": \"Hire 6 pickers\"}".formatted(budget));

    assertThat(list(bo))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            "[{\"agendaItemId\": \"%s\", \"text\": \"Hire 6 pickers\"}]".formatted(budget));
    assertThat(
            jdbc.queryForObject(
                "SELECT created_by::text FROM decision WHERE meeting_id = ?::uuid",
                String.class,
                meetingId))
        .isEqualTo(userId(ana));
  }

  @Test
  void listsDecisionsInTheOrderTheyWereRecorded() {
    start();
    add(ana, budget, "First");
    add(ana, staffing, "Second");
    add(ana, budget, "Third");

    assertThat(texts(ana)).isEqualTo(List.of("First", "Second", "Third"));
  }

  @Test
  void theOrganizerEditsADecision() {
    start();
    String decision = idOf(add(ana, budget, "Hire 6 pickers"));

    assertThat(edit(ana, decision, " Hire 8 pickers "))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.text")
        .isEqualTo("Hire 8 pickers");

    assertThat(texts(bo)).isEqualTo(List.of("Hire 8 pickers"));
  }

  @Test
  void theOrganizerRemovesADecision() {
    start();
    String decision = idOf(add(ana, budget, "Hire 6 pickers"));
    add(ana, budget, "Keep the budget");

    assertThat(remove(ana, decision)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(texts(bo)).isEqualTo(List.of("Keep the budget"));
    assertThat(remove(ana, decision)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(edit(ana, decision, "Too late")).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void rejectsADecisionThatIsBlankOrTooLong() {
    start();
    String decision = idOf(add(ana, budget, "Hire 6 pickers"));

    assertThat(add(ana, budget, "   ")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(add(ana, budget, "x".repeat(2001))).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(edit(ana, decision, "   ")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(edit(ana, decision, "x".repeat(2001))).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(add(ana, budget, "x".repeat(2000))).hasStatus(HttpStatus.CREATED);
  }

  @Test
  void decisionsWaitForTheMeetingToStart() {
    assertThat(add(ana, budget, "Too early")).hasStatus(HttpStatus.CONFLICT);

    assertThat(list(ana)).hasStatusOk().bodyJson().isLenientlyEqualTo("[]");
  }

  @Test
  void closingLocksDecisionsAndKeepsThemReadable() {
    start();
    String decision = idOf(add(ana, budget, "Hire 6 pickers"));
    post(ana, "/api/meetings/" + meetingId + "/close", "");

    assertThat(add(ana, budget, "Changed my mind")).hasStatus(HttpStatus.CONFLICT);
    assertThat(edit(ana, decision, "Changed my mind")).hasStatus(HttpStatus.CONFLICT);
    assertThat(remove(ana, decision)).hasStatus(HttpStatus.CONFLICT);

    assertThat(texts(bo)).isEqualTo(List.of("Hire 6 pickers"));
  }

  @Test
  void onlyTheOrganizerChangesDecisions() {
    start();
    String decision = idOf(add(ana, budget, "Hire 6 pickers"));

    assertThat(add(bo, budget, "Mine")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(edit(bo, decision, "Mine")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(remove(bo, decision)).hasStatus(HttpStatus.FORBIDDEN);

    assertThat(texts(ana)).isEqualTo(List.of("Hire 6 pickers"));
  }

  @Test
  void outsidersGetNotFoundForEverything() {
    start();
    String decision = idOf(add(ana, budget, "Hire 6 pickers"));

    assertThat(list(cy)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(add(cy, budget, "Mine")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(edit(cy, decision, "Mine")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(remove(cy, decision)).hasStatus(HttpStatus.NOT_FOUND);

    // The answer is the same as for a decision that doesn't exist, so nothing is revealed.
    assertThat(body(edit(cy, decision, "Mine")))
        .isEqualTo(
            body(edit(cy, UUID.randomUUID().toString(), "Mine"))
                .replaceAll("/api/decisions/[^\"]+", "/api/decisions/" + decision));

    assertThat(texts(ana)).isEqualTo(List.of("Hire 6 pickers"));
  }

  @Test
  void requestsWithoutASessionGetUnauthorized() {
    assertThat(mvc.get().uri("/api/meetings/{id}/decisions", meetingId).exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void itemsCanOnlyBeRemovedBeforeTheMeetingStarts() {
    assertThat(removeItem(budget)).hasStatus(HttpStatus.NO_CONTENT);
    start();

    assertThat(removeItem(staffing)).hasStatus(HttpStatus.CONFLICT);

    assertThat(body(agenda())).contains("Staffing");
  }

  @Test
  void itemsCanStillBeRenamedAndReorderedWhileTheMeetingRuns() {
    start();
    add(ana, budget, "Hire 6 pickers");

    assertThat(
            send(
                mvc.put().uri("/api/agenda-items/{id}", budget).cookie(ana),
                "{\"title\": \"Budget 2027\"}"))
        .hasStatusOk();
    assertThat(
            send(
                mvc.put().uri("/api/meetings/{id}/agenda-items/order", meetingId).cookie(ana),
                "{\"itemIds\": [\"%s\", \"%s\"]}".formatted(staffing, budget)))
        .hasStatusOk();

    List<String> titles = JsonPath.read(body(agenda()), "$[*].title");
    assertThat(titles).isEqualTo(List.of("Staffing", "Budget 2027"));
    assertThat(texts(ana)).isEqualTo(List.of("Hire 6 pickers"));
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

  private void start() {
    assertThat(post(ana, "/api/meetings/" + meetingId + "/start", "")).hasStatusOk();
  }

  private MvcTestResult add(Cookie session, String itemId, String text) {
    return post(session, "/api/agenda-items/" + itemId + "/decisions", textJson(text));
  }

  private MvcTestResult edit(Cookie session, String decisionId, String text) {
    return send(mvc.put().uri("/api/decisions/{id}", decisionId).cookie(session), textJson(text));
  }

  private MvcTestResult remove(Cookie session, String decisionId) {
    return send(mvc.delete().uri("/api/decisions/{id}", decisionId).cookie(session), "");
  }

  private MvcTestResult removeItem(String itemId) {
    return send(mvc.delete().uri("/api/agenda-items/{id}", itemId).cookie(ana), "");
  }

  private MvcTestResult list(Cookie session) {
    return mvc.get().uri("/api/meetings/{id}/decisions", meetingId).cookie(session).exchange();
  }

  private MvcTestResult agenda() {
    return mvc.get().uri("/api/meetings/{id}/agenda-items", meetingId).cookie(ana).exchange();
  }

  private List<String> texts(Cookie session) {
    return JsonPath.read(body(list(session)), "$[*].text");
  }

  private static String idOf(MvcTestResult result) {
    return JsonPath.read(body(result), "$.id");
  }

  private static String textJson(String text) {
    return "{\"text\": \"" + text + "\"}";
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
