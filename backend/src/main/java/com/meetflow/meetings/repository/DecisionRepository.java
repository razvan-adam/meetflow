package com.meetflow.meetings.repository;

import com.meetflow.meetings.domain.Decision;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

  /** Where a decision belongs, read without loading the decision itself. */
  record DecisionPlace(UUID teamId, UUID meetingId) {}

  List<Decision> findByMeetingIdOrderByCreatedAt(UUID meetingId);

  Optional<DecisionPlace> findPlaceById(UUID id);
}
