package com.meetflow.meetings.repository;

import com.meetflow.meetings.domain.AgendaItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgendaItemRepository extends JpaRepository<AgendaItem, UUID> {

  /** Where an item belongs, read without loading the item itself. */
  record ItemPlace(UUID teamId, UUID meetingId) {}

  List<AgendaItem> findByMeetingIdOrderByPosition(UUID meetingId);

  Optional<ItemPlace> findPlaceById(UUID id);
}
