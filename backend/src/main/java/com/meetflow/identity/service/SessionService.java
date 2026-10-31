package com.meetflow.identity.service;

import static java.util.Objects.requireNonNull;

import com.meetflow.identity.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

/**
 * Logs a user in by storing them in the session, which Spring Session keeps in PostgreSQL. Logout
 * is handled by Spring Security (see {@code SecurityConfig}).
 */
@Service
public class SessionService {

  private final AuthenticationManager authenticationManager;
  private final CsrfTokenRepository csrfTokens;
  private final SecurityContextRepository securityContexts =
      new HttpSessionSecurityContextRepository();

  SessionService(AuthenticationManager authenticationManager, CsrfTokenRepository csrfTokens) {
    this.authenticationManager = authenticationManager;
    this.csrfTokens = csrfTokens;
  }

  /**
   * Checks the email and password and logs the user in.
   *
   * @throws org.springframework.security.core.AuthenticationException if they don't match
   */
  public CurrentUser logIn(
      String email, String password, HttpServletRequest request, HttpServletResponse response) {
    var authentication =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(email, password));
    start(authentication, request, response);
    return (CurrentUser) requireNonNull(authentication.getPrincipal());
  }

  /** Logs in a user who has just signed up, without checking the password again. */
  public void logIn(CurrentUser user, HttpServletRequest request, HttpServletResponse response) {
    start(
        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()),
        request,
        response);
  }

  private void start(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    // Session fixation: a session that existed before login gets a new ID.
    if (request.getSession(false) != null) {
      request.changeSessionId();
    }
    var holder = SecurityContextHolder.getContextHolderStrategy();
    var context = holder.createEmptyContext();
    context.setAuthentication(authentication);
    holder.setContext(context);
    securityContexts.saveContext(context, request, response);
    // A token issued before login must not stay valid after it.
    csrfTokens.saveToken(csrfTokens.generateToken(request), request, response);
  }
}
