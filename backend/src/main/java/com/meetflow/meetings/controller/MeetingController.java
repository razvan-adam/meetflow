package com.meetflow.meetings.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.meetings.dto.MeetingResponse;
import com.meetflow.meetings.dto.ScheduleMeetingRequest;
import com.meetflow.meetings.dto.UpdateMeetingRequest;
import com.meetflow.meetings.service.MeetingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeetingController {

  private final MeetingService meetingService;

  MeetingController(MeetingService meetingService) {
    this.meetingService = meetingService;
  }

  @PostMapping("/api/meeting-series/{seriesId}/meetings")
  @ResponseStatus(HttpStatus.CREATED)
  MeetingResponse schedule(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID seriesId,
      @Valid @RequestBody ScheduleMeetingRequest body) {
    return meetingService.schedule(user.id(), seriesId, body.scheduledAt(), body.participantIds());
  }

  @GetMapping("/api/meeting-series/{seriesId}/meetings")
  List<MeetingResponse> list(
      @AuthenticationPrincipal CurrentUser user, @PathVariable UUID seriesId) {
    return meetingService.listForSeries(user.id(), seriesId);
  }

  @GetMapping("/api/meetings/{meetingId}")
  MeetingResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID meetingId) {
    return meetingService.get(user.id(), meetingId);
  }

  @PutMapping("/api/meetings/{meetingId}")
  MeetingResponse update(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID meetingId,
      @Valid @RequestBody UpdateMeetingRequest body) {
    return meetingService.update(
        user.id(), meetingId, body.scheduledAt(), body.participantIds(), body.version());
  }
}
