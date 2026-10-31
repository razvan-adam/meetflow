package com.meetflow.identity;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.meetflow.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/**
 * MF-8: sign-up, login and logout, through the whole app so that sessions are stored in PostgreSQL
 * as in production. Every test uses its own email, since the database is shared.
 */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class AccountTests {

  static final String PASSWORD = "correct horse battery";

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  String email = "ana." + UUID.randomUUID() + "@example.com";

  @Test
  void signUpCreatesTheAccountAndLogsIn() {
    var response = signUp("Ana Costa", email, PASSWORD);

    assertThat(response.getStatus()).isEqualTo(201);
    var session = requireNonNull(response.getCookie("SESSION"));
    assertThat(mvc.get().uri("/api/users/me").cookie(session))
        .hasStatusOk()
        .bodyJson()
        .satisfies(
            body -> {
              assertThat(body).extractingPath("$.name").isEqualTo("Ana Costa");
              assertThat(body).extractingPath("$.email").isEqualTo(email);
            });
  }

  @Test
  void storesOnlyAHashOfThePassword(CapturedOutput output) {
    signUp("Ana Costa", email, PASSWORD);
    logIn(email, PASSWORD);

    var hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM app_user WHERE email = ?", String.class, email);
    assertThat(hash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    assertThat(output.getAll()).doesNotContain(PASSWORD);
  }

  @Test
  void storesTheEmailLowercased() {
    signUp("Ana Costa", email.toUpperCase(), PASSWORD);

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE email = ?", Integer.class, email))
        .isEqualTo(1);
  }

  @Test
  void rejectsASecondAccountWithTheSameEmail() {
    signUp("Ana Costa", email, PASSWORD);

    var response = signUp("Another Ana", email.toUpperCase(), PASSWORD);

    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getCookie("SESSION")).isNull();
  }

  @Test
  void rejectsAnInvalidSignUpListingTheFields() throws Exception {
    var response = signUp("", "not-an-email", "short");

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getContentAsString())
        .contains("\"field\":\"name\"", "\"field\":\"email\"", "\"field\":\"password\"")
        .doesNotContain("short");
  }

  // bcrypt ignores everything after 72 bytes; 25 three-byte characters are 75 bytes.
  @Test
  void rejectsAPasswordLongerThanBcryptAccepts() {
    assertThat(signUp("Ana Costa", email, "€".repeat(25)).getStatus()).isEqualTo(400);
  }

  @Test
  void logsInWithEmailAndPassword() {
    signUp("Ana Costa", email, PASSWORD);

    var response = logIn(email.toUpperCase(), PASSWORD);

    assertThat(response.getStatus()).isEqualTo(200);
    var session = requireNonNull(response.getCookie("SESSION"));
    assertThat(mvc.get().uri("/api/users/me").cookie(session)).hasStatusOk();
  }

  @Test
  void replacesTheCsrfTokenAtLogin() {
    signUp("Ana Costa", email, PASSWORD);
    var before = csrfCookie();

    var response = send(mvc.post().uri("/api/session"), before, logInJson(email, PASSWORD));

    assertThat(lastCookie(response, "XSRF-TOKEN").getValue())
        .isNotEmpty()
        .isNotEqualTo(before.getValue());
  }

  @Test
  void rejectsAWrongPasswordAndAnUnknownEmailAlike() throws Exception {
    signUp("Ana Costa", email, PASSWORD);

    var wrongPassword = logIn(email, "wrong password!");
    var unknownEmail = logIn("nobody." + email, PASSWORD);

    assertThat(wrongPassword.getStatus()).isEqualTo(401);
    assertThat(unknownEmail.getStatus()).isEqualTo(401);
    assertThat(wrongPassword.getContentAsString()).isEqualTo(unknownEmail.getContentAsString());
    assertThat(wrongPassword.getCookie("SESSION")).isNull();
  }

  @Test
  void logOutEndsTheSession() {
    var session = requireNonNull(signUp("Ana Costa", email, PASSWORD).getCookie("SESSION"));
    var token = csrfCookie();

    var response =
        mvc.delete()
            .uri("/api/session")
            .cookie(session, token)
            .header("X-XSRF-TOKEN", token.getValue())
            .exchange()
            .getResponse();

    assertThat(response.getStatus()).isEqualTo(204);
    assertThat(lastCookie(response, "XSRF-TOKEN").getValue()).isNotEmpty();
    assertThat(mvc.get().uri("/api/users/me").cookie(session)).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void currentUserNeedsLogin() {
    assertThat(mvc.get().uri("/api/users/me")).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  private MockHttpServletResponse signUp(String name, String email, String password) {
    return send(
        mvc.post().uri("/api/users"),
        csrfCookie(),
        """
        {"name": "%s", "email": "%s", "password": "%s"}"""
            .formatted(name, email, password));
  }

  private MockHttpServletResponse logIn(String email, String password) {
    return send(mvc.post().uri("/api/session"), csrfCookie(), logInJson(email, password));
  }

  private static String logInJson(String email, String password) {
    return """
        {"email": "%s", "password": "%s"}"""
        .formatted(email, password);
  }

  // The way the React app sends changes: JSON body, CSRF cookie echoed in the header.
  private static MockHttpServletResponse send(
      MockMvcRequestBuilder request, Cookie csrfToken, String json) {
    return request
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .cookie(csrfToken)
        .header("X-XSRF-TOKEN", csrfToken.getValue())
        .exchange()
        .getResponse();
  }

  private Cookie csrfCookie() {
    var response = mvc.get().uri("/api/health").exchange().getResponse();
    return requireNonNull(response.getCookie("XSRF-TOKEN"), "no XSRF-TOKEN cookie");
  }

  // Login and logout first delete the old token cookie, then set the new one; the browser keeps
  // the last.
  private static Cookie lastCookie(MockHttpServletResponse response, String name) {
    return Arrays.stream(response.getCookies())
        .filter(cookie -> cookie.getName().equals(name))
        .reduce((first, second) -> second)
        .orElseThrow();
  }
}
