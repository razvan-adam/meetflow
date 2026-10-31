package com.meetflow.teams.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** A group of users who meet regularly. */
@Entity
public class Team implements Persistable<UUID> {

  @Id private UUID id;

  private String name;

  private UUID createdBy;

  private Instant createdAt;

  // The ID is assigned here, so Spring Data can't tell a new team from a stored one by its ID.
  @Transient private boolean isNew = true;

  public Team(String name, UUID createdBy) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
  }

  @SuppressWarnings("NullAway.Init") // For JPA only; it fills the fields.
  protected Team() {}

  @Override
  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
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
