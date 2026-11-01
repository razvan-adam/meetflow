package com.meetflow.meetings.repository;

import com.meetflow.meetings.domain.Meeting;
import com.meetflow.meetings.domain.MeetingStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

  /** The series' meetings, latest first, with their participants. */
  @EntityGraph(attributePaths = "participantIds")
  List<Meeting> findBySeriesIdOrderByScheduledAtDesc(UUID seriesId);

  boolean existsBySeriesIdAndStatusNot(UUID seriesId, MeetingStatus status);
}
