package com.meetflow.minutes;

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
 * MF-23: the meeting history, through the whole app. Ana's team has two series, "Weekly ops sync"
 * and "Monthly carrier review", with closed meetings. Bo is a member; Cy has a team of their own.
 */
@IntegrationTest
class HistoryTests {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  Cookie csrfToken;

  Cookie ana;

  Cookie bo;

  Cookie cy;

  String teamId;

  String weekly;

  String monthly;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa");
    bo = signUp("Bo Lind");
    cy = signUp("Cy Park");
    teamId = createTeam(ana, "Ops team");
    jdbc.update(
        "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
            + " VALUES (?::uuid, ?::uuid, 'MEMBER', now())",
        teamId,
        userId(bo));
    weekly = createSeries(ana, teamId, "Weekly ops sync");
    monthly = createSeries(ana, teamId, "Monthly carrier review");
  }

  @Test
  void listsClosedMeetingsBySeriesWithTheNewestFirst() {
    String oldest = closedMeeting(weekly, "2026-10-12T09:00:00Z", List.of("Raise pallet prices"));
    String carriers = closedMeeting(monthly, "2026-10-15T09:00:00Z", List.of());
    String newest =
        closedMeeting(
            weekly, "2026-10-19T09:00:00Z", List.of("Move dock 3 scanners", "Require an RMA"));
    // Neither a running nor a scheduled meeting is history yet.
    startMeeting(weekly, "2026-10-26T09:00:00Z");

    assertThat(history(bo, ""))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            [{"id": "%s", "name": "Weekly ops sync", "meetings": [
               {"id": "%s", "date": "2026-10-19",
                "decisions": ["Move dock 3 scanners", "Require an RMA"], "taskCount": 1},
               {"id": "%s", "date": "2026-10-12",
                "decisions": ["Raise pallet prices"], "taskCount": 1}]},
             {"id": "%s", "name": "Monthly carrier review", "meetings": [
               {"id": "%s", "date": "2026-10-15", "decisions": [], "taskCount": 1}]}]"""
                .formatted(weekly, newest, oldest, monthly, carriers));
  }

  @Test
  void aTeamWithoutClosedMeetingsHasAnEmptyHistory() {
    startMeeting(weekly, "2026-10-26T09:00:00Z");

    assertThat(history(ana, "")).hasStatusOk().bodyJson().isStrictlyEqualTo("[]");
  }

  @Test
  void searchFindsWordsInDecisionsAndListsTheWholeMeeting() {
    String pallets =
        closedMeeting(
            weekly, "2026-10-12T09:00:00Z", List.of("Raise pallet prices", "Hire two drivers"));
    closedMeeting(weekly, "2026-10-19T09:00:00Z", List.of("Move dock 3 scanners"));

    assertThat(meetingIds(history(ana, "PALLET"))).containsExactly(pallets);
    // The start of a word is enough, and every word must appear in the same decision.
    assertThat(meetingIds(history(ana, "pall pri"))).containsExactly(pallets);
    assertThat(meetingIds(history(ana, "pallet drivers"))).isEmpty();
    // Only whole words or their start: "allet" is inside "pallet".
    assertThat(meetingIds(history(ana, "allet"))).isEmpty();
    assertThat(history(ana, "pallet"))
        .bodyJson()
        .extractingPath("$[0].meetings[0].decisions")
        .isEqualTo(List.of("Raise pallet prices", "Hire two drivers"));
  }

  @Test
  void searchFindsWordsInSeriesNames() {
    closedMeeting(weekly, "2026-10-12T09:00:00Z", List.of("Raise pallet prices"));
    String carriers = closedMeeting(monthly, "2026-10-15T09:00:00Z", List.of());

    assertThat(meetingIds(history(ana, "carrier"))).containsExactly(carriers);
    assertThat(history(ana, "carrier"))
        .bodyJson()
        .extractingPath("$[*].name")
        .isEqualTo(List.of("Monthly carrier review"));
  }

  @Test
  void punctuationSeparatesWordsAndCannotChangeTheQuery() {
    String pallets =
        closedMeeting(weekly, "2026-10-12T09:00:00Z", List.of("Bring pallet costs under €18"));
    String carriers = closedMeeting(monthly, "2026-10-15T09:00:00Z", List.of());

    assertThat(meetingIds(history(ana, "'pallet' & !costs:* | (18)"))).containsExactly(pallets);
    // Nothing but punctuation is no search at all.
    assertThat(meetingIds(history(ana, "'&|!:*()"))).containsExactlyInAnyOrder(pallets, carriers);
    assertThat(meetingIds(history(ana, "   "))).containsExactlyInAnyOrder(pallets, carriers);
  }

  @Test
  void findsWordsInAnyLanguage() {
    String spanish =
        closedMeeting(weekly, "2026-10-12T09:00:00Z", List.of("Revisar los camiones de Málaga"));

    assertThat(meetingIds(history(ana, "málaga camión"))).isEmpty();
    assertThat(meetingIds(history(ana, "MÁLAGA camion"))).containsExactly(spanish);
  }

  @Test
  void onlyMembersOfTheTeamSeeItsHistory() {
    closedMeeting(weekly, "2026-10-12T09:00:00Z", List.of("Raise pallet prices"));

    assertThat(history(cy, "")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(history(cy, "pallet")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/teams/{id}/history", teamId).exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void anotherTeamsMeetingsNeverAppear() {
    String ours = closedMeeting(weekly, "2026-10-12T09:00:00Z", List.of("Raise pallet prices"));
    String cysTeam = createTeam(cy, "Cy's team");
    String cysSeries = createSeries(cy, cysTeam, "Weekly ops sync");
    String theirs =
        closedMeeting(cy, cysSeries, "2026-10-13T09:00:00Z", List.of("Raise pallet prices"));

    assertThat(meetingIds(history(ana, ""))).containsExactly(ours);
    assertThat(meetingIds(history(ana, "pallet"))).containsExactly(ours);
    assertThat(meetingIds(history(ana, "weekly"))).containsExactly(ours);
    assertThat(meetingIds(get(cy, "/api/teams/" + cysTeam + "/history?q=pallet")))
        .containsExactly(theirs);
  }

  /** A meeting of Ana's team, held when planned, with one decision per text and one task. */
  private String closedMeeting(String seriesId, String heldAt, List<String> decisions) {
    return closedMeeting(ana, seriesId, heldAt, decisions);
  }

  private String closedMeeting(
      Cookie organizer, String seriesId, String heldAt, List<String> decisions) {
    String meeting = startMeeting(organizer, seriesId, heldAt);
    String item =
        idOf(
            post(
                organizer, "/api/meetings/" + meeting + "/agenda-items", "{\"title\": \"Topic\"}"));
    for (String text : decisions) {
      assertThat(
              post(
                  organizer,
                  "/api/agenda-items/" + item + "/decisions",
                  "{\"text\": \"" + text + "\"}"))
          .hasStatus(HttpStatus.CREATED);
    }
    assertThat(
            post(
                organizer,
                "/api/agenda-items/" + item + "/tasks",
                "{\"description\": \"Follow up\", \"ownerId\": \"%s\", \"deadline\": \"2026-12-01\"}"
                    .formatted(userId(organizer))))
        .hasStatus(HttpStatus.CREATED);
    assertThat(post(organizer, "/api/meetings/" + meeting + "/close", "{\"timeZone\": \"UTC\"}"))
        .hasStatusOk();
    return meeting;
  }

  private String startMeeting(String seriesId, String heldAt) {
    return startMeeting(ana, seriesId, heldAt);
  }

  private String startMeeting(Cookie organizer, String seriesId, String heldAt) {
    String meeting =
        idOf(
            post(
                organizer,
                "/api/meeting-series/" + seriesId + "/meetings",
                "{\"scheduledAt\": \"%s\", \"participantIds\": [\"%s\"]}"
                    .formatted(heldAt, userId(organizer))));
    assertThat(post(organizer, "/api/meetings/" + meeting + "/start", "")).hasStatusOk();
    jdbc.update(
        "UPDATE meeting SET started_at = ?::timestamptz WHERE id = ?::uuid", heldAt, meeting);
    return meeting;
  }

  private String createTeam(Cookie session, String name) {
    return idOf(post(session, "/api/teams", "{\"name\": \"" + name + "\"}"));
  }

  private String createSeries(Cookie session, String team, String name) {
    return idOf(
        post(session, "/api/teams/" + team + "/meeting-series", "{\"name\": \"" + name + "\"}"));
  }

  private MvcTestResult history(Cookie session, String search) {
    return mvc.get()
        .uri("/api/teams/{id}/history", teamId)
        .queryParam("q", search)
        .cookie(session)
        .exchange();
  }

  private static List<String> meetingIds(MvcTestResult result) {
    assertThat(result).hasStatusOk();
    return JsonPath.read(body(result), "$[*].meetings[*].id");
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
        {"name": "%s", "email": "%s", "password": "correct horse battery"}"""
            .formatted(name, "user." + UUID.randomUUID() + "@example.com");
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
