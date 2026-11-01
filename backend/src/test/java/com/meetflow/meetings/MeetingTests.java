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
 * MF-13: scheduling meetings, through the whole app. Ana creates the team and organizes the series;
 * Bo and Dee are members, added directly in the database since invitations are tested in
 * InvitationTests. Cy is in another team.
 */
@IntegrationTest
class MeetingTests {

  static final String MONDAY = "2026-11-02T08:30:00Z";

  static final String TUESDAY = "2026-11-03T15:00:00Z";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  Cookie csrfToken;

  Cookie ana;

  Cookie bo;

  Cookie cy;

  Cookie dee;

  String anaId;

  String boId;

  String deeId;

  String cyId;

  String teamId;

  String seriesId;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa");
    bo = signUp("Bo Lind");
    cy = signUp("Cy Park");
    anaId = userId(ana);
    boId = userId(bo);
    dee = signUp("Dee Moss");
    deeId = userId(dee);
    cyId = userId(cy);
    teamId = JsonPath.read(body(post(ana, "/api/teams", "{\"name\": \"Ops team\"}")), "$.id");
    addMember(boId);
    addMember(deeId);
    seriesId =
        JsonPath.read(
            body(
                post(
                    ana,
                    "/api/teams/" + teamId + "/meeting-series",
                    "{\"name\": \"Weekly ops sync\"}")),
            "$.id");
  }

  @Test
  void theOrganizerSchedulesAMeetingWithParticipantsFromTheTeam() {
    var response = schedule(ana, MONDAY, boId);

    // The organizer always attends, even if not listed.
    assertThat(response)
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"teamId": "%s", "seriesId": "%s", "scheduledAt": "%s", "status": "SCHEDULED",
             "participants": [{"id": "%s", "name": "Ana Costa"}, {"id": "%s", "name": "Bo Lind"}]}"""
                .formatted(teamId, seriesId, MONDAY, anaId, boId));
    String meetingId = JsonPath.read(body(response), "$.id");
    assertThat(mvc.get().uri("/api/meetings/{id}", meetingId).cookie(bo))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.scheduledAt")
        .isEqualTo(MONDAY);
  }

  @Test
  void onlyTheOrganizerSchedulesAMeeting() {
    assertThat(schedule(bo, MONDAY, boId)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(schedule(cy, MONDAY, cyId)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(meetingsOfSeries(ana)).bodyJson().isLenientlyEqualTo("[]");
  }

  @Test
  void participantsMustBeTeamMembers() {
    assertThat(schedule(ana, MONDAY, boId, cyId)).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(schedule(ana, MONDAY, UUID.randomUUID().toString()))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(meetingsOfSeries(ana)).bodyJson().isLenientlyEqualTo("[]");
  }

  @Test
  void rejectsAMeetingWithoutADate() {
    assertThat(
            post(
                ana,
                "/api/meeting-series/" + seriesId + "/meetings",
                "{\"participantIds\": [\"%s\"]}".formatted(boId)))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void aSeriesHasOneMeetingThatIsNotClosedAtATime() {
    String first = JsonPath.read(body(schedule(ana, MONDAY, boId)), "$.id");

    assertThat(schedule(ana, TUESDAY, boId)).hasStatus(HttpStatus.CONFLICT);

    close(first);
    String next = JsonPath.read(body(schedule(ana, "2026-11-09T08:30:00Z", deeId)), "$.id");
    assertThat(meetingsOfSeries(bo))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[*].id")
        .isEqualTo(List.of(next, first));
  }

  @Test
  void theOrganizerChangesTheDateAndParticipants() {
    String meetingId = JsonPath.read(body(schedule(ana, MONDAY, boId)), "$.id");

    assertThat(update(ana, meetingId, TUESDAY, deeId))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"scheduledAt": "%s",
             "participants": [{"id": "%s", "name": "Ana Costa"}, {"id": "%s", "name": "Dee Moss"}]}"""
                .formatted(TUESDAY, anaId, deeId));
    assertThat(mvc.get().uri("/api/meetings/{id}", meetingId).cookie(bo))
        .bodyJson()
        .extractingPath("$.participants[*].name")
        .isEqualTo(List.of("Ana Costa", "Dee Moss"));
  }

  @Test
  void onlyTheOrganizerChangesAMeetingAndOnlyToTeamMembers() {
    String meetingId = JsonPath.read(body(schedule(ana, MONDAY, boId)), "$.id");

    assertThat(update(bo, meetingId, TUESDAY, boId)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(update(ana, meetingId, TUESDAY, cyId)).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(mvc.get().uri("/api/meetings/{id}", meetingId).cookie(ana))
        .bodyJson()
        .extractingPath("$.scheduledAt")
        .isEqualTo(MONDAY);
  }

  @Test
  void theLatestEditWinsEvenFromAnOutdatedPage() {
    String meetingId = JsonPath.read(body(schedule(ana, MONDAY, boId)), "$.id");
    update(ana, meetingId, TUESDAY, boId);

    // Made on a page that still showed Monday with Bo.
    assertThat(update(ana, meetingId, MONDAY, deeId)).hasStatusOk();
    assertThat(mvc.get().uri("/api/meetings/{id}", meetingId).cookie(ana))
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"scheduledAt": "%s",
             "participants": [{"id": "%s", "name": "Ana Costa"}, {"id": "%s", "name": "Dee Moss"}]}"""
                .formatted(MONDAY, anaId, deeId));
  }

  @Test
  void aClosedMeetingCannotBeChanged() {
    String meetingId = JsonPath.read(body(schedule(ana, MONDAY, boId)), "$.id");
    close(meetingId);

    assertThat(update(ana, meetingId, TUESDAY, boId)).hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void membersWhoDontAttendSeeTheMeetingButOutsidersDont() {
    String meetingId = JsonPath.read(body(schedule(ana, MONDAY, boId)), "$.id");
    assertThat(mvc.get().uri("/api/meetings/{id}", meetingId).cookie(dee)).hasStatusOk();
    assertThat(mvc.get().uri("/api/meetings/{id}", meetingId).cookie(cy))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(meetingsOfSeries(cy)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(update(cy, meetingId, TUESDAY, cyId)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/meetings/{id}", UUID.randomUUID()).cookie(ana))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  private MvcTestResult schedule(Cookie session, String scheduledAt, String... participantIds) {
    return post(
        session,
        "/api/meeting-series/" + seriesId + "/meetings",
        "{\"scheduledAt\": \"%s\", \"participantIds\": %s}"
            .formatted(scheduledAt, jsonIds(participantIds)));
  }

  private MvcTestResult update(
      Cookie session, String meetingId, String scheduledAt, String... participantIds) {
    return send(
        mvc.put().uri("/api/meetings/{id}", meetingId).cookie(session),
        "{\"scheduledAt\": \"%s\", \"participantIds\": %s}"
            .formatted(scheduledAt, jsonIds(participantIds)));
  }

  private MvcTestResult meetingsOfSeries(Cookie session) {
    return mvc.get().uri("/api/meeting-series/{id}/meetings", seriesId).cookie(session).exchange();
  }

  // Closing a meeting comes with MF-15; until then the tests close it in the database.
  private void close(String meetingId) {
    jdbc.update(
        "UPDATE meeting SET status = 'CLOSED', started_at = now(), closed_at = now(),"
            + " closed_by = ?::uuid WHERE id = ?::uuid",
        anaId,
        meetingId);
  }

  private void addMember(String userId) {
    jdbc.update(
        "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
            + " VALUES (?::uuid, ?::uuid, 'MEMBER', now())",
        teamId,
        userId);
  }

  private static String jsonIds(String... ids) {
    return List.of(ids).stream()
        .map(id -> "\"" + id + "\"")
        .reduce((a, b) -> a + ", " + b)
        .map(list -> "[" + list + "]")
        .orElse("[]");
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
