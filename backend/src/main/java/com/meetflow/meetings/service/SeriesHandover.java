package com.meetflow.meetings.service;

import static java.util.Objects.requireNonNull;

import com.meetflow.meetings.domain.MeetingSeries;
import com.meetflow.meetings.exception.OrganizerNotMemberException;
import com.meetflow.meetings.exception.UnresolvedSeriesException;
import com.meetflow.meetings.repository.MeetingRepository;
import com.meetflow.meetings.repository.MeetingSeriesRepository;
import com.meetflow.teams.MemberRemovalEvent;
import com.meetflow.teams.TeamMembers;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Hands over the series a removed member organizes (MF-11), inside the transaction that removes
 * them, and takes them off the meetings that haven't closed, so they aren't sent those minutes.
 * Closed meetings keep them as participants: that is the record of who was there.
 */
@Component
class SeriesHandover {

  private final MeetingSeriesRepository series;
  private final MeetingRepository meetings;
  private final TeamMembers teamMembers;

  SeriesHandover(
      MeetingSeriesRepository series, MeetingRepository meetings, TeamMembers teamMembers) {
    this.series = series;
    this.meetings = meetings;
    this.teamMembers = teamMembers;
  }

  @EventListener
  void on(MemberRemovalEvent event) {
    var organized =
        series.findByTeamId(event.teamId()).stream()
            .filter(found -> found.getOrganizerId().equals(event.memberId()))
            .toList();
    var organizedIds = organized.stream().map(MeetingSeries::getId).collect(Collectors.toSet());
    if (!organizedIds.equals(event.seriesOrganizers().keySet())) {
      throw new UnresolvedSeriesException();
    }
    var newOrganizers = event.seriesOrganizers().values().stream().distinct().toList();
    if (!teamMembers.areMembers(newOrganizers, event.teamId())) {
      throw new OrganizerNotMemberException();
    }
    for (var found : organized) {
      // Present: the keys were just checked to be exactly these series.
      found.changeOrganizer(requireNonNull(event.seriesOrganizers().get(found.getId())));
    }
    meetings.removeParticipantFromOpenMeetings(event.teamId(), event.memberId());
  }
}
