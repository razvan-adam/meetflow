package com.meetflow.identity.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.identity.dto.LogInRequest;
import com.meetflow.identity.dto.UserResponse;
import com.meetflow.identity.service.SessionService;
import com.meetflow.identity.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login. Logout ({@code DELETE /api/session}) is handled by Spring Security's logout filter, set up
 * in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/session")
class SessionController {

  private final SessionService sessionService;
  private final UserService userService;

  SessionController(SessionService sessionService, UserService userService) {
    this.sessionService = sessionService;
    this.userService = userService;
  }

  @PostMapping
  UserResponse logIn(
      @Valid @RequestBody LogInRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    CurrentUser user;
    try {
      user = sessionService.logIn(body.email(), body.password(), request, response);
    } catch (AuthenticationException e) {
      // The same answer for an unknown email and a wrong password, so neither reveals the other.
      var error = new ErrorResponseException(HttpStatus.UNAUTHORIZED, e);
      error.getBody().setDetail("Email or password is incorrect.");
      throw error;
    }
    return userService.describe(user);
  }
}
