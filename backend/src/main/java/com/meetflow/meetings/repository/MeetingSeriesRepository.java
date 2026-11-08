package com.meetflow.meetings.repository;

import com.meetflow.meetings.domain.MeetingSeries;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MeetingSeriesRepository extends JpaRepository<MeetingSeries, UUID> {

  List<MeetingSeries> findByTeamId(UUID teamId);

  List<MeetingSeries> findByTeamIdAndOrganizerId(UUID teamId, UUID organizerId);

  /** Holds the series' row until the transaction ends. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<MeetingSeries> findForUpdateById(UUID id);
}
