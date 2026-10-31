package com.meetflow.meetings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Recurring meetings of a team with the same purpose (e.g. "Weekly ops sync"). Open tasks and
 * uncovered agenda items carry over between the meetings of a series. Each series has one
 * organizer, who must be a member of the team.
 */
@Entity
public class MeetingSeries implements Persistable<UUID> {

  @Id private UUID id;

  private UUID teamId;

  private String name;

  private UUID organizerId;

  // Only written to the database, never read in Java; @Column keeps Error Prone from flagging it
  // unused.
  @Column private Instant createdAt;

  // The ID is assigned here, so Spring Data can't tell a new series from a stored one by its ID.
  @Transient private boolean isNew = true;

  public MeetingSeries(UUID teamId, String name, UUID organizerId) {
    this.id = UUID.randomUUID();
    this.teamId = teamId;
    this.name = name;
    this.organizerId = organizerId;
    this.createdAt = Instant.now();
  }

  @SuppressWarnings("NullAway.Init") // For JPA only; it fills the fields.
  protected MeetingSeries() {}

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public String getName() {
    return name;
  }

  public UUID getOrganizerId() {
    return organizerId;
  }

  /** The new organizer must already be checked to be a member of the team. */
  public void changeOrganizer(UUID organizerId) {
    this.organizerId = organizerId;
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
