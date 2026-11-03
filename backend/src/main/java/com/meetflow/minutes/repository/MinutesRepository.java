package com.meetflow.minutes.repository;

import com.meetflow.minutes.domain.Minutes;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinutesRepository extends JpaRepository<Minutes, UUID> {

  Optional<Minutes> findByMeetingId(UUID meetingId);
}
