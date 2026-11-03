package com.meetflow.minutes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * The record of a closed meeting, written once when it closes. It has no setters, and a database
 * trigger rejects any update, so nobody can edit it.
 */
@Entity
public class Minutes implements Persistable<UUID> {

  @Id private UUID id;

  private UUID teamId;

  // Minutes are looked up by this column, and the table requires it.
  @Column private UUID meetingId;

  private Instant generatedAt;

  /** JSON: see {@code MinutesContent}. */
  @JdbcTypeCode(SqlTypes.JSON)
  private String content;

  // The ID is assigned here, so Spring Data can't tell new minutes from stored ones by their ID.
  @Transient private boolean isNew = true;

  public Minutes(UUID teamId, UUID meetingId, String content) {
    this.id = UUID.randomUUID();
    this.teamId = teamId;
    this.meetingId = meetingId;
    this.generatedAt = Instant.now();
    this.content = content;
  }

  @SuppressWarnings("NullAway.Init") // For JPA only; it fills the fields.
  protected Minutes() {}

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public String getContent() {
    return content;
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
