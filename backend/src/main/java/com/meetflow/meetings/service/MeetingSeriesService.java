package com.meetflow.meetings.service;

import com.meetflow.identity.UserDirectory;
import com.meetflow.meetings.domain.MeetingSeries;
import com.meetflow.meetings.dto.SeriesResponse;
import com.meetflow.meetings.exception.OrganizerNotMemberException;
import com.meetflow.meetings.exception.SeriesNotFoundException;
import com.meetflow.meetings.repository.MeetingSeriesRepository;
import com.meetflow.teams.TeamMembers;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Meeting series (MF-12). Any team member can create one and becomes its organizer; team members
 * see all series of their team; a team admin can hand the organizer role to another member.
 */
@Service
public class MeetingSeriesService {

  private final MeetingSeriesRepository series;
  private final TeamMembers teamMembers;
  private final UserDirectory users;

  MeetingSeriesService(
      MeetingSeriesRepository series, TeamMembers teamMembers, UserDirectory users) {
    this.series = series;
    this.teamMembers = teamMembers;
    this.users = users;
  }

  /** Creates a series in one of the user's teams, with the user as its organizer. */
  @Transactional
  public SeriesResponse create(UUID userId, UUID teamId, String name) {
    teamMembers.requireMember(userId, teamId);
    var created = series.save(new MeetingSeries(teamId, name.strip(), userId));
    return toResponse(created, names(List.of(created)));
  }

  /** The team's series, sorted by name. */
  @Transactional(readOnly = true)
  public List<SeriesResponse> listForTeam(UUID userId, UUID teamId) {
    teamMembers.requireMember(userId, teamId);
    var teamSeries = series.findByTeamId(teamId);
    var names = names(teamSeries);
    var byName = Collator.getInstance(Locale.ENGLISH);
    return teamSeries.stream()
        .map(found -> toResponse(found, names))
        .sorted(Comparator.comparing(SeriesResponse::name, byName))
        .toList();
  }

  @Transactional(readOnly = true)
  public SeriesResponse get(UUID userId, UUID seriesId) {
    var found = findVisible(userId, seriesId);
    return toResponse(found, names(List.of(found)));
  }

  /** Hands the organizer role to another member of the team. Team admins only. */
  @Transactional
  public SeriesResponse changeOrganizer(UUID adminId, UUID seriesId, UUID organizerId) {
    var found = findVisible(adminId, seriesId);
    teamMembers.requireAdmin(adminId, found.getTeamId());
    if (!teamMembers.isMember(organizerId, found.getTeamId())) {
      throw new OrganizerNotMemberException();
    }
    found.changeOrganizer(organizerId);
    return toResponse(found, names(List.of(found)));
  }

  /** The series, if the user is a member of its team; otherwise 404, as if it didn't exist. */
  private MeetingSeries findVisible(UUID userId, UUID seriesId) {
    return series
        .findById(seriesId)
        .filter(found -> teamMembers.isMember(userId, found.getTeamId()))
        .orElseThrow(SeriesNotFoundException::new);
  }

  private Map<UUID, String> names(List<MeetingSeries> list) {
    return users.namesOf(list.stream().map(MeetingSeries::getOrganizerId).toList());
  }

  private static SeriesResponse toResponse(MeetingSeries found, Map<UUID, String> names) {
    return new SeriesResponse(
        found.getId(),
        found.getTeamId(),
        found.getName(),
        found.getOrganizerId(),
        names.getOrDefault(found.getOrganizerId(), ""));
  }
}
