package com.meetflow.teams;

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
 * MF-9: creating a team and seeing only your own teams, through the whole app. Every test signs up
 * its own users, since the database is shared.
 */
@IntegrationTest
class TeamTests {

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  Cookie csrfToken;

  Cookie ana;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa");
  }

  @Test
  void createsATeamWithTheCreatorAsAdmin() {
    var response = createTeam(ana, "  Ops team ");

    assertThat(response.getStatus()).isEqualTo(201);
    var teamId = teamId(response);
    assertThat(mvc.get().uri("/api/teams/{id}", teamId).cookie(ana))
        .hasStatusOk()
        .bodyJson()
        .satisfies(
            body -> {
              assertThat(body).extractingPath("$.name").isEqualTo("Ops team");
              assertThat(body).extractingPath("$.role").isEqualTo("ADMIN");
            });
    assertThat(
            jdbc.queryForList(
                "SELECT role FROM team_membership WHERE team_id = ?::uuid", String.class, teamId))
        .containsExactly("ADMIN");
  }

  @Test
  void listsEveryTeamOfTheUserByName() {
    var ops = teamId(createTeam(ana, "Ops team"));
    var leadership = teamId(createTeam(ana, "leadership"));

    assertThat(mvc.get().uri("/api/teams").cookie(ana))
        .hasStatusOk()
        .bodyJson()
        .satisfies(
            body -> {
              assertThat(body).extractingPath("$[*].id").isEqualTo(List.of(leadership, ops));
              assertThat(body).extractingPath("$[*].role").isEqualTo(List.of("ADMIN", "ADMIN"));
            });
  }

  @Test
  void showsOnlyTheUsersOwnTeams() {
    var anasTeam = teamId(createTeam(ana, "Ops team"));
    var bo = signUp("Bo Lind");
    createTeam(bo, "Finance");

    assertThat(mvc.get().uri("/api/teams").cookie(bo))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$[*].name")
        .isEqualTo(List.of("Finance"));
    // Not a member: answered like a team that doesn't exist.
    assertThat(mvc.get().uri("/api/teams/{id}", anasTeam).cookie(bo))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/teams/{id}", UUID.randomUUID()).cookie(bo))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void aNewUserHasNoTeams() {
    assertThat(mvc.get().uri("/api/teams").cookie(ana)).hasStatusOk().bodyJson().isEqualTo("[]");
  }

  @Test
  void rejectsABlankOrTooLongName() {
    assertThat(createTeam(ana, "   ").getStatus()).isEqualTo(400);
    assertThat(createTeam(ana, "x".repeat(101)).getStatus()).isEqualTo(400);
  }

  @Test
  void teamsNeedLogin() {
    assertThat(mvc.get().uri("/api/teams")).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(send(mvc.post().uri("/api/teams"), "{\"name\": \"Ops team\"}"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  private MockHttpServletResponse createTeam(Cookie session, String name) {
    return send(mvc.post().uri("/api/teams").cookie(session), "{\"name\": \"%s\"}".formatted(name))
        .getResponse();
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

  // The way the React app sends changes: JSON body, CSRF cookie echoed in the header.
  private MvcTestResult send(MockMvcRequestBuilder request, String json) {
    return request
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .cookie(csrfToken)
        .header("X-XSRF-TOKEN", csrfToken.getValue())
        .exchange();
  }

  private static String teamId(MockHttpServletResponse response) {
    try {
      return JsonPath.read(response.getContentAsString(), "$.id");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private Cookie csrfCookie() {
    var response = mvc.get().uri("/api/health").exchange().getResponse();
    return requireNonNull(response.getCookie("XSRF-TOKEN"), "no XSRF-TOKEN cookie");
  }
}
