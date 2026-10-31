package com.meetflow.meetings.controller;

import com.meetflow.identity.CurrentUser;
import com.meetflow.meetings.dto.ChangeOrganizerRequest;
import com.meetflow.meetings.dto.CreateSeriesRequest;
import com.meetflow.meetings.dto.SeriesResponse;
import com.meetflow.meetings.service.MeetingSeriesService;
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
class MeetingSeriesController {

  private final MeetingSeriesService seriesService;

  MeetingSeriesController(MeetingSeriesService seriesService) {
    this.seriesService = seriesService;
  }

  @PostMapping("/api/teams/{teamId}/meeting-series")
  @ResponseStatus(HttpStatus.CREATED)
  SeriesResponse create(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID teamId,
      @Valid @RequestBody CreateSeriesRequest body) {
    return seriesService.create(user.id(), teamId, body.name());
  }

  @GetMapping("/api/teams/{teamId}/meeting-series")
  List<SeriesResponse> list(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID teamId) {
    return seriesService.listForTeam(user.id(), teamId);
  }

  @GetMapping("/api/meeting-series/{seriesId}")
  SeriesResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID seriesId) {
    return seriesService.get(user.id(), seriesId);
  }

  @PutMapping("/api/meeting-series/{seriesId}/organizer")
  SeriesResponse changeOrganizer(
      @AuthenticationPrincipal CurrentUser user,
      @PathVariable UUID seriesId,
      @Valid @RequestBody ChangeOrganizerRequest body) {
    return seriesService.changeOrganizer(user.id(), seriesId, body.organizerId());
  }
}
