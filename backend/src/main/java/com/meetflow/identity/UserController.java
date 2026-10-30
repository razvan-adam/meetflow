package com.meetflow.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
class UserController {

  private final UserService userService;
  private final SessionService sessionService;

  UserController(UserService userService, SessionService sessionService) {
    this.userService = userService;
    this.sessionService = sessionService;
  }

  /** Signs up and logs in, so the new user lands on their dashboard. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  UserResponse signUp(
      @Valid @RequestBody SignUpRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    var user = userService.signUp(body);
    sessionService.logIn(user, request, response);
    return userService.describe(user);
  }

  @GetMapping("/me")
  UserResponse me(@AuthenticationPrincipal CurrentUser user) {
    return userService.describe(user);
  }
}
