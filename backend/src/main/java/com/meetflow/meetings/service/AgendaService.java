package com.meetflow.meetings.service;

import com.meetflow.meetings.domain.AgendaItem;
import com.meetflow.meetings.dto.AgendaItemResponse;
import com.meetflow.meetings.exception.AgendaChangedException;
import com.meetflow.meetings.exception.AgendaItemNotFoundException;
import com.meetflow.meetings.repository.AgendaItemRepository;
import com.meetflow.teams.TeamMembers;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preparing a meeting's agenda (MF-14). The series organizer adds, renames, reorders and removes
 * items until the meeting is closed; every member of the team can read the agenda. As with the
 * meeting itself, the latest edit wins.
 */
@Service
public class AgendaService {

  private final AgendaItemRepository items;
  private final MeetingService meetings;
  private final TeamMembers teamMembers;

  AgendaService(AgendaItemRepository items, MeetingService meetings, TeamMembers teamMembers) {
    this.items = items;
    this.meetings = meetings;
    this.teamMembers = teamMembers;
  }

  @Transactional(readOnly = true)
  public List<AgendaItemResponse> list(UUID userId, UUID meetingId) {
    meetings.findVisible(userId, meetingId);
    return items.findByMeetingIdOrderByPosition(meetingId).stream()
        .map(AgendaService::toResponse)
        .toList();
  }

  /** Adds an item at the end of the agenda. */
  @Transactional
  public AgendaItemResponse add(UUID userId, UUID meetingId, String title) {
    var meeting = meetings.lockForOrganizerEdit(userId, meetingId);
    var agenda = items.findByMeetingIdOrderByPosition(meetingId);
    int position = agenda.isEmpty() ? 1 : agenda.getLast().getPosition() + 1;
    var item = new AgendaItem(meeting.getTeamId(), meetingId, position, title.strip());
    return toResponse(items.save(item));
  }

  @Transactional
  public AgendaItemResponse rename(UUID userId, UUID itemId, String title) {
    var item = findForOrganizerEdit(userId, itemId);
    item.rename(title.strip());
    return toResponse(item);
  }

  /** Items carried over from the previous meeting can be removed like any other. */
  @Transactional
  public void remove(UUID userId, UUID itemId) {
    items.delete(findForOrganizerEdit(userId, itemId));
  }

  /**
   * Puts the agenda in the given order. The list must hold exactly the agenda's items: one sent
   * from a page that missed an added or removed item can't be applied, so it answers 409.
   */
  @Transactional
  public List<AgendaItemResponse> reorder(UUID userId, UUID meetingId, List<UUID> itemIds) {
    meetings.lockForOrganizerEdit(userId, meetingId);
    var agenda = items.findByMeetingIdOrderByPosition(meetingId);
    var currentIds = agenda.stream().map(AgendaItem::getId).toList();
    if (itemIds.size() != currentIds.size()
        || !new HashSet<>(itemIds).equals(new HashSet<>(currentIds))) {
      throw new AgendaChangedException();
    }
    agenda.forEach(item -> item.moveTo(itemIds.indexOf(item.getId()) + 1));
    return agenda.stream()
        .sorted(Comparator.comparingInt(AgendaItem::getPosition))
        .map(AgendaService::toResponse)
        .toList();
  }

  /** The item, for its series organizer. Outsiders get the same 404 as for an unknown item. */
  private AgendaItem findForOrganizerEdit(UUID userId, UUID itemId) {
    var item =
        items
            .findById(itemId)
            .filter(found -> teamMembers.isMember(userId, found.getTeamId()))
            .orElseThrow(AgendaItemNotFoundException::new);
    meetings.lockForOrganizerEdit(userId, item.getMeetingId());
    return item;
  }

  private static AgendaItemResponse toResponse(AgendaItem item) {
    return new AgendaItemResponse(item.getId(), item.getTitle(), item.isCarriedOver());
  }
}
