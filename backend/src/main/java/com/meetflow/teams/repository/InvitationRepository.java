package com.meetflow.teams.repository;

import com.meetflow.teams.domain.Invitation;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

  Optional<Invitation> findByTokenHash(String tokenHash);

  /** Locks the row, so two people can't use the same link at the same moment. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM Invitation i WHERE i.tokenHash = :tokenHash")
  Optional<Invitation> findByTokenHashForUpdate(String tokenHash);

  /** Invitations not yet accepted and not expired, newest first. */
  @Query(
      """
      SELECT i FROM Invitation i
      WHERE i.teamId = :teamId AND i.acceptedAt IS NULL AND i.expiresAt > :now
      ORDER BY i.createdAt DESC""")
  List<Invitation> findPending(UUID teamId, Instant now);

  /** Removes the email's earlier invitations to the team that weren't accepted. */
  @Modifying
  @Query(
      "DELETE FROM Invitation i"
          + " WHERE i.teamId = :teamId AND i.email = :email AND i.acceptedAt IS NULL")
  void deleteUnaccepted(UUID teamId, String email);
}
