package com.meetflow.meetings.service;

import com.meetflow.meetings.domain.AgendaItem;
import com.meetflow.meetings.domain.AgendaItemStatus;
import com.meetflow.meetings.dto.AgendaItemResponse;
import com.meetflow.meetings.exception.AgendaChangedException;
import com.meetflow.meetings.exception.AgendaItemNotFoundException;
import com.meetflow.meetings.repository.AgendaItemRepository;
import com.meetflow.teams.TeamMembers;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A meeting's agenda: preparing it (MF-14) and working through it (MF-15). The series organizer
 * adds, renames and reorders items until the meeting is closed, removes them until it starts, and
 * takes notes and marks items while it runs; every member of the team can read the agenda. As with
 * the meeting itself, the latest edit wins.
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

  /**
   * Only before the meeting starts: 409 afterwards. Decisions and tasks are only created during a
   * meeting, so a removable item has none pointing at it, and no notes can be lost. Items carried
   * over from the previous meeting can be removed like any other.
   */
  @Transactional
  public void remove(UUID userId, UUID itemId) {
    items.delete(lockAndLoad(userId, itemId, meetings::lockUnstartedMeeting));
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

  /** Notes are taken during the meeting: 409 before it starts and after it closes. */
  @Transactional
  public AgendaItemResponse writeNotes(UUID userId, UUID itemId, String notes) {
    var item = findInRunningMeeting(userId, itemId);
    item.writeNotes(notes);
    return toResponse(item);
  }

  /** Items are marked during the meeting: 409 before it starts and after it closes. */
  @Transactional
  public AgendaItemResponse mark(UUID userId, UUID itemId, AgendaItemStatus status) {
    var item = findInRunningMeeting(userId, itemId);
    item.mark(status);
    return toResponse(item);
  }

  /** The item, for its series organizer, while its meeting runs. */
  public AgendaItem findInRunningMeeting(UUID userId, UUID itemId) {
    return lockAndLoad(userId, itemId, meetings::lockRunningMeeting);
  }

  /** The item, for its series organizer. Outsiders get the same 404 as for an unknown item. */
  private AgendaItem findForOrganizerEdit(UUID userId, UUID itemId) {
    return lockAndLoad(userId, itemId, meetings::lockForOrganizerEdit);
  }

  /**
   * The item, loaded only once its meeting is locked. A copy loaded before waiting for the lock
   * would miss a change made meanwhile, and saving it would fail on the version check; that happens
   * whenever notes and a mark are sent together. An item removed meanwhile answers 404.
   */
  private AgendaItem lockAndLoad(
      UUID userId, UUID itemId, BiConsumer<UUID, UUID> lockMeetingForUser) {
    var place =
        items
            .findPlaceById(itemId)
            .filter(found -> teamMembers.isMember(userId, found.teamId()))
            .orElseThrow(AgendaItemNotFoundException::new);
    lockMeetingForUser.accept(userId, place.meetingId());
    return items.findById(itemId).orElseThrow(AgendaItemNotFoundException::new);
  }

  private static AgendaItemResponse toResponse(AgendaItem item) {
    return new AgendaItemResponse(
        item.getId(), item.getTitle(), item.getNotes(), item.getStatus(), item.isCarriedOver());
  }
}
