package com.meetflow.meetings.repository;

import com.meetflow.meetings.domain.AgendaItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgendaItemRepository extends JpaRepository<AgendaItem, UUID> {

  List<AgendaItem> findByMeetingIdOrderByPosition(UUID meetingId);
}
