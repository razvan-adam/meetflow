package com.meetflow;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Baseline security from MF-36: authentication, CSRF and cookie settings.
 *
 * <p>CSRF tokens are obtained the way the React app does it (cookie in, header out) rather than
 * with Spring Security's csrf() test helper, which swaps the token repository in the shared filter
 * chain and breaks later tests.
 */
@IntegrationTest
class SecurityTests {

  @Autowired MockMvcTester mvc;

  @Autowired CookieSerializer sessionCookies;

  @Test
  void rejectsChangesFromAnonymousUsers() {
    var token = csrfCookie();
    assertThat(mvc.post().uri("/api/teams").cookie(token).header("X-XSRF-TOKEN", token.getValue()))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  // Sessions are stored in PostgreSQL; anonymous traffic must not create rows.
  @Test
  void anonymousRequestsDoNotOpenASession() {
    assertThat(mvc.get().uri("/api/teams"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .cookies()
        .doesNotContainCookie("SESSION");
  }

  @Test
  void rejectsChangesWithoutCsrfToken() {
    assertThat(mvc.post().uri("/api/teams").with(user("member"))).hasStatus(HttpStatus.FORBIDDEN);
  }

  // Passes security and reaches the app, which has no such endpoint yet.
  @Test
  void acceptsChangesWithCsrfToken() {
    var token = csrfCookie();
    assertThat(
            mvc.post()
                .uri("/api/teams")
                .with(user("member"))
                .cookie(token)
                .header("X-XSRF-TOKEN", token.getValue()))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  // The React app reads this cookie and echoes it in the X-XSRF-TOKEN header, so it must not be
  // HttpOnly.
  @Test
  void givesTheSpaACsrfCookie() {
    assertThat(csrfCookie().isHttpOnly()).isFalse();
  }

  @Test
  void sessionCookieIsSecureHttpOnlyAndSameSite() {
    var response = new MockHttpServletResponse();
    sessionCookies.writeCookieValue(
        new CookieValue(new MockHttpServletRequest(), response, "session-id"));

    assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
        .contains("Secure", "HttpOnly", "SameSite=Lax");
  }

  private Cookie csrfCookie() {
    var response = mvc.get().uri("/api/health").exchange().getResponse();
    return requireNonNull(response.getCookie("XSRF-TOKEN"), "no XSRF-TOKEN cookie");
  }
}
