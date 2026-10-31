package com.meetflow.teams;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.meetflow.IntegrationTest;
import com.meetflow.RecordingEmailSender;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
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
 * MF-10: team admins invite colleagues by email; the invitee joins from the link. Through the whole
 * app, with the email sender replaced by {@link RecordingEmailSender}. Every test signs up its own
 * users, since the database is shared.
 */
@IntegrationTest
class InvitationTests {

  private static final Pattern JOIN_LINK = Pattern.compile("http://localhost:5173/join/(\\S+)");

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  @Autowired RecordingEmailSender emails;

  Cookie csrfToken;

  Cookie ana;

  String teamId;

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa", newEmail());
    teamId = JsonPath.read(body(post(ana, "/api/teams", "{\"name\": \"Ops team\"}")), "$.id");
  }

  @AfterEach
  void resetEmails() {
    emails.setFailing(false);
  }

  @Test
  void anInviteeWithoutAnAccountSignsUpAndJoins() {
    var bosEmail = newEmail();

    var invite = invite(ana, bosEmail.toUpperCase(Locale.ROOT));

    assertThat(invite.getStatus()).isEqualTo(201);
    var email = emails.lastTo(bosEmail);
    assertThat(email.subject()).isEqualTo("Ana Costa invited you to Ops team on MeetFlow");
    var token = tokenIn(email.text());
    assertThat(mvc.get().uri("/api/invitation-links/{token}", token))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"teamName": "Ops team", "invitedBy": "Ana Costa", "email": "%s", "status": "PENDING"}"""
                .formatted(bosEmail));

    var bo = signUp("Bo Lind", bosEmail);
    assertThat(post(bo, "/api/invitation-links/" + token + "/accept", ""))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo("{\"id\": \"%s\", \"role\": \"MEMBER\"}".formatted(teamId));
    assertThat(mvc.get().uri("/api/teams").cookie(bo))
        .bodyJson()
        .extractingPath("$[*].name")
        .isEqualTo(List.of("Ops team"));
  }

  @Test
  void anInviteeWithAnAccountJoinsAndTheLinkCannotBeUsedAgain() {
    var bosEmail = newEmail();
    var bo = signUp("Bo Lind", bosEmail);
    invite(ana, bosEmail);
    var token = tokenIn(emails.lastTo(bosEmail).text());

    assertThat(post(bo, "/api/invitation-links/" + token + "/accept", "")).hasStatusOk();

    assertThat(post(bo, "/api/invitation-links/" + token + "/accept", ""))
        .hasStatus(HttpStatus.GONE)
        .bodyJson()
        .extractingPath("$.invitationStatus")
        .isEqualTo("ACCEPTED");
    assertThat(mvc.get().uri("/api/invitation-links/{token}", token))
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("ACCEPTED");
  }

  @Test
  void onlyTheInvitedAddressCanJoin() {
    var bosEmail = newEmail();
    invite(ana, bosEmail);
    var token = tokenIn(emails.lastTo(bosEmail).text());
    var cy = signUp("Cy Park", newEmail());

    assertThat(post(cy, "/api/invitation-links/" + token + "/accept", ""))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(mvc.get().uri("/api/teams").cookie(cy)).bodyJson().isEqualTo("[]");
    assertThat(mvc.get().uri("/api/invitation-links/{token}", token))
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("PENDING");
  }

  @Test
  void anExpiredLinkCannotBeUsed() {
    var bosEmail = newEmail();
    invite(ana, bosEmail);
    var token = tokenIn(emails.lastTo(bosEmail).text());
    jdbc.update(
        "UPDATE invitation SET expires_at = now() - interval '1 minute' WHERE email = ?", bosEmail);
    var bo = signUp("Bo Lind", bosEmail);

    assertThat(mvc.get().uri("/api/invitation-links/{token}", token))
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("EXPIRED");
    assertThat(post(bo, "/api/invitation-links/" + token + "/accept", ""))
        .hasStatus(HttpStatus.GONE)
        .bodyJson()
        .extractingPath("$.invitationStatus")
        .isEqualTo("EXPIRED");
  }

  @Test
  void anUnknownLinkIsNotFoundAndJoiningNeedsLogin() {
    assertThat(mvc.get().uri("/api/invitation-links/{token}", "no-such-token"))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(send(mvc.post().uri("/api/invitation-links/{token}/accept", "no-such-token"), ""))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void adminsSeeAndCancelPendingInvitations() {
    var first = newEmail();
    var second = newEmail();
    invite(ana, first);
    String secondId = JsonPath.read(body(invite(ana, second)), "$.id");
    var secondToken = tokenIn(emails.lastTo(second).text());

    assertThat(mvc.get().uri("/api/teams/{id}/invitations", teamId).cookie(ana))
        .hasStatusOk()
        .bodyJson()
        .satisfies(
            body -> {
              assertThat(body).extractingPath("$[*].email").isEqualTo(List.of(second, first));
              assertThat(body)
                  .extractingPath("$[*].invitedBy")
                  .isEqualTo(List.of("Ana Costa", "Ana Costa"));
            });

    assertThat(delete(ana, "/api/invitations/" + secondId)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(mvc.get().uri("/api/teams/{id}/invitations", teamId).cookie(ana))
        .bodyJson()
        .extractingPath("$[*].email")
        .isEqualTo(List.of(first));
    assertThat(mvc.get().uri("/api/invitation-links/{token}", secondToken))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void invitingAgainReplacesTheEarlierLink() {
    var bosEmail = newEmail();
    invite(ana, bosEmail);
    var oldToken = tokenIn(emails.lastTo(bosEmail).text());

    invite(ana, bosEmail);
    var newToken = tokenIn(emails.lastTo(bosEmail).text());

    assertThat(emails.countTo(bosEmail)).isEqualTo(2);
    assertThat(mvc.get().uri("/api/invitation-links/{token}", oldToken))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/api/invitation-links/{token}", newToken)).hasStatusOk();
    assertThat(mvc.get().uri("/api/teams/{id}/invitations", teamId).cookie(ana))
        .bodyJson()
        .extractingPath("$[*].email")
        .isEqualTo(List.of(bosEmail));
  }

  @Test
  void onlyAdminsManageInvitations() {
    var bosEmail = newEmail();
    var bo = signUp("Bo Lind", bosEmail);
    invite(ana, bosEmail);
    post(bo, "/api/invitation-links/" + tokenIn(emails.lastTo(bosEmail).text()) + "/accept", "");
    var pendingEmail = newEmail();
    String pendingId = JsonPath.read(body(invite(ana, pendingEmail)), "$.id");
    var outsider = signUp("Cy Park", newEmail());

    // A member who isn't an admin.
    assertThat(invite(bo, newEmail()).getStatus()).isEqualTo(403);
    assertThat(mvc.get().uri("/api/teams/{id}/invitations", teamId).cookie(bo))
        .hasStatus(HttpStatus.FORBIDDEN);
    assertThat(delete(bo, "/api/invitations/" + pendingId)).hasStatus(HttpStatus.FORBIDDEN);
    // Not a member: answered as if the team and invitation didn't exist.
    assertThat(invite(outsider, newEmail()).getStatus()).isEqualTo(404);
    assertThat(mvc.get().uri("/api/teams/{id}/invitations", teamId).cookie(outsider))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(delete(outsider, "/api/invitations/" + pendingId)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void rejectsInvitingAMemberOrAnInvalidEmail() {
    var anasEmail =
        jdbc.queryForObject(
            "SELECT u.email FROM app_user u JOIN team_membership m ON m.user_id = u.id"
                + " WHERE m.team_id = ?::uuid",
            String.class,
            teamId);

    assertThat(invite(ana, requireNonNull(anasEmail)).getStatus()).isEqualTo(409);
    assertThat(invite(ana, "not-an-email").getStatus()).isEqualTo(400);
  }

  @Test
  void savesNothingWhenTheEmailCannotBeSent() {
    emails.setFailing(true);

    assertThat(invite(ana, newEmail()).getStatus()).isEqualTo(503);

    assertThat(mvc.get().uri("/api/teams/{id}/invitations", teamId).cookie(ana))
        .bodyJson()
        .isEqualTo("[]");
  }

  @Test
  void storesOnlyAHashOfTheToken() {
    var bosEmail = newEmail();
    invite(ana, bosEmail);
    var token = tokenIn(emails.lastTo(bosEmail).text());

    var hash =
        jdbc.queryForObject(
            "SELECT token_hash FROM invitation WHERE email = ?", String.class, bosEmail);
    assertThat(hash).hasSize(64).isNotEqualTo(token).doesNotContain(token);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM email_delivery WHERE recipient_email = ?",
                Integer.class,
                bosEmail))
        .isEqualTo(1);
  }

  private MockHttpServletResponse invite(Cookie session, String email) {
    return post(
            session,
            "/api/teams/" + teamId + "/invitations",
            "{\"email\": \"%s\"}".formatted(email))
        .getResponse();
  }

  private static String tokenIn(String emailText) {
    var matcher = JOIN_LINK.matcher(emailText);
    assertThat(matcher.find()).as("join link in the email").isTrue();
    return matcher.group(1);
  }

  private static String newEmail() {
    return "user." + UUID.randomUUID() + "@example.com";
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

  private MvcTestResult post(Cookie session, String path, String json) {
    return send(mvc.post().uri(path).cookie(session), json);
  }

  private MvcTestResult delete(Cookie session, String path) {
    return send(mvc.delete().uri(path).cookie(session), "");
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
