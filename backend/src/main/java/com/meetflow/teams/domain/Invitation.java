package com.meetflow.teams.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * A single-use invitation to join a team, sent by email. Only a hash of the link's token is stored;
 * a cancelled invitation is deleted.
 */
@Entity
public class Invitation implements Persistable<UUID> {

  public static final Duration VALIDITY = Duration.ofDays(7);

  /** Whether the link can still be used. */
  public enum Status {
    PENDING,
    EXPIRED,
    ACCEPTED
  }

  @Id private UUID id;

  private UUID teamId;

  private String email;

  // Only written to the database, never read in Java; @Column keeps Error Prone from flagging it
  // unused. Invitations are looked up by it.
  @Column private String tokenHash;

  private UUID invitedBy;

  private Instant createdAt;

  private Instant expiresAt;

  private @Nullable Instant acceptedAt;

  // Only written to the database, never read in Java.
  @Column private @Nullable UUID acceptedBy;

  // The ID is assigned here, so Spring Data can't tell a new invitation from a stored one by its
  // ID.
  @Transient private boolean isNew = true;

  /** A new invitation, valid for 7 days. The email must already be lowercased. */
  public Invitation(UUID teamId, String email, String tokenHash, UUID invitedBy) {
    this.id = UUID.randomUUID();
    this.teamId = teamId;
    this.email = email;
    this.tokenHash = tokenHash;
    this.invitedBy = invitedBy;
    this.createdAt = Instant.now();
    this.expiresAt = createdAt.plus(VALIDITY);
  }

  @SuppressWarnings("NullAway.Init") // For JPA only; it fills the fields.
  protected Invitation() {}

  public Status status(Instant now) {
    if (acceptedAt != null) {
      return Status.ACCEPTED;
    }
    return now.isBefore(expiresAt) ? Status.PENDING : Status.EXPIRED;
  }

  public void accept(UUID userId) {
    acceptedAt = Instant.now();
    acceptedBy = userId;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public String getEmail() {
    return email;
  }

  public UUID getInvitedBy() {
    return invitedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markStored() {
    isNew = false;
  }
}
