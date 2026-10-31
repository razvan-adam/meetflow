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
 * MF-12: meeting series, through the whole app. Ana creates the team (admin); Bo is added as a
 * member directly in the database, since invitations are tested in InvitationTests.
 */
@IntegrationTest
class MeetingSeriesTests {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  Cookie csrfToken;

  Cookie ana;

  Cookie bo;

  String anaId;

  String boId;

  String teamId;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa");
    bo = signUp("Bo Lind");
    anaId = userId(ana);
    boId = userId(bo);
    teamId = JsonPath.read(body(post(ana, "/api/teams", "{\"name\": \"Ops team\"}")), "$.id");
    jdbc.update(
        "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
            + " VALUES (?::uuid, ?::uuid, 'MEMBER', now())",
        teamId,
        boId);
  }

  @Test
  void anyMemberCreatesASeriesAndBecomesItsOrganizer() {
    var response = createSeries(bo, "  Weekly ops sync ");

    assertThat(response)
        .hasStatus(HttpStatus.CREATED)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"teamId": "%s", "name": "Weekly ops sync", "organizerId": "%s",
             "organizerName": "Bo Lind"}"""
                .formatted(teamId, boId));
    String seriesId = JsonPath.read(body(response), "$.id");
    assertThat(mvc.get().uri("/api/meeting-series/{id}", seriesId).cookie(ana))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.organizerName")
        .isEqualTo("Bo Lind");
  }

  @Test
  void membersSeeAllSeriesOfTheTeamByName() {
    createSeries(ana, "Weekly ops sync");
    createSeries(bo, "monthly carrier review");

    assertThat(mvc.get().uri("/api/teams/{id}/meeting-series", teamId).cookie(bo))
        .hasStatusOk()
        .bodyJson()
        .satisfies(
            body -> {
              assertThat(body)
                  .extractingPath("$[*].name")
                  .isEqualTo(List.of("monthly carrier review", "Weekly ops sync"));
              assertThat(body)
                  .extractingPath("$[*].organizerName")
                  .isEqualTo(List.of("Bo Lind", "Ana Costa"));
            });
  }

  @Test
  void outsidersSeeNothingOfTheTeamsSeries() {
    String seriesId = JsonPath.read(body(createSeries(ana, "Weekly ops sync")), "$.id");
    var cy = signUp("Cy Park");

    assertThat(createSeries(cy, "Sneaky series")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/teams/{id}/meeting-series", teamId).cookie(cy))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/meeting-series/{id}", seriesId).cookie(cy))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(changeOrganizer(cy, seriesId, userId(cy))).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/meeting-series/{id}", UUID.randomUUID()).cookie(ana))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void anAdminHandsTheOrganizerRoleToAnotherMember() {
    String seriesId = JsonPath.read(body(createSeries(ana, "Weekly ops sync")), "$.id");

    assertThat(changeOrganizer(ana, seriesId, boId))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            "{\"organizerId\": \"%s\", \"organizerName\": \"Bo Lind\"}".formatted(boId));
    assertThat(mvc.get().uri("/api/meeting-series/{id}", seriesId).cookie(bo))
        .bodyJson()
        .extractingPath("$.organizerId")
        .isEqualTo(boId);
  }

  @Test
  void onlyAdminsChangeTheOrganizerAndOnlyToAMember() {
    String seriesId = JsonPath.read(body(createSeries(bo, "Weekly ops sync")), "$.id");
    var outsider = signUp("Cy Park");

    // Being the organizer doesn't make Bo an admin.
    assertThat(changeOrganizer(bo, seriesId, anaId)).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(changeOrganizer(ana, seriesId, userId(outsider))).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(mvc.get().uri("/api/meeting-series/{id}", seriesId).cookie(ana))
        .bodyJson()
        .extractingPath("$.organizerId")
        .isEqualTo(boId);
  }

  @Test
  void rejectsABlankOrTooLongName() {
    assertThat(createSeries(ana, "   ")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(createSeries(ana, "x".repeat(101))).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void membersSeeTheTeamsMembersByName() {
    assertThat(mvc.get().uri("/api/teams/{id}/members", teamId).cookie(bo))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            [{"id": "%s", "name": "Ana Costa", "role": "ADMIN"},
             {"id": "%s", "name": "Bo Lind", "role": "MEMBER"}]"""
                .formatted(anaId, boId));
    assertThat(mvc.get().uri("/api/teams/{id}/members", teamId).cookie(signUp("Cy Park")))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  private MvcTestResult createSeries(Cookie session, String name) {
    return post(
        session, "/api/teams/" + teamId + "/meeting-series", "{\"name\": \"%s\"}".formatted(name));
  }

  private MvcTestResult changeOrganizer(Cookie session, String seriesId, String organizerId) {
    return send(
        mvc.put().uri("/api/meeting-series/{id}/organizer", seriesId).cookie(session),
        "{\"organizerId\": \"%s\"}".formatted(organizerId));
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
