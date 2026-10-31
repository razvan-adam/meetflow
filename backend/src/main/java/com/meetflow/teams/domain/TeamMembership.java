package com.meetflow.teams.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/** A user's membership and role in a team. */
@Entity
public class TeamMembership implements Persistable<TeamMembership.Id> {

  /** The primary key: one membership per user and team. */
  @Embeddable
  public record Id(UUID teamId, UUID userId) {}

  @EmbeddedId private Id id;

  @Enumerated(EnumType.STRING)
  private Role role;

  private Instant joinedAt;

  // The ID is known up front, so Spring Data can't tell a new membership from a stored one by it.
  @Transient private boolean isNew = true;

  public TeamMembership(UUID teamId, UUID userId, Role role) {
    this.id = new Id(teamId, userId);
    this.role = role;
    this.joinedAt = Instant.now();
  }

  @SuppressWarnings("NullAway.Init") // For JPA only; it fills the fields.
  protected TeamMembership() {}

  @Override
  public Id getId() {
    return id;
  }

  public Role getRole() {
    return role;
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
