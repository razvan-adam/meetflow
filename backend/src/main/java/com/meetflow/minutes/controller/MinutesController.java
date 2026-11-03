package com.meetflow.minutes.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.minutes.dto.MinutesResponse;
import com.meetflow.minutes.service.MinutesService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Minutes can only be read: nothing here, or anywhere else, changes them. */
@RestController
class MinutesController {

  private final MinutesService minutes;

  MinutesController(MinutesService minutes) {
    this.minutes = minutes;
  }

  @GetMapping("/api/meetings/{meetingId}/minutes")
  MinutesResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID meetingId) {
    return minutes.get(user.id(), meetingId);
  }
}
