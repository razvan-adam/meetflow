package com.meetflow.meetings.repository;

import com.meetflow.meetings.domain.Meeting;
import com.meetflow.meetings.domain.MeetingStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

  /** The series' meetings, latest first, with their participants. */
  @EntityGraph(attributePaths = "participantIds")
  List<Meeting> findBySeriesIdOrderByScheduledAtDesc(UUID seriesId);

  boolean existsBySeriesIdAndStatusNot(UUID seriesId, MeetingStatus status);

  /** Holds the meeting's row until the transaction ends, so its agenda has one writer at a time. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Meeting> findForUpdateById(UUID id);
}
