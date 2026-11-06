package com.meetflow.teams.repository;

import com.meetflow.teams.domain.Team;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface TeamRepository extends JpaRepository<Team, UUID> {

  /**
   * Holds the team's row until the transaction ends, so two admins changing roles or removing
   * members at the same time can't leave the team without an admin.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Team> findForUpdateById(UUID id);
}
