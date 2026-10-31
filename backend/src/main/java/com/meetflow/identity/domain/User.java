package com.meetflow.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/** A person with an account. A deleted account keeps its row but loses its email and password. */
@Entity
@Table(name = "app_user")
public class User implements Persistable<UUID> {

  @Id private UUID id;

  private @Nullable String email;

  private String name;

  private @Nullable String passwordHash;

  // Only written to the database, never read in Java; @Column keeps Error Prone from flagging these
  // as unused.
  @Column private Instant createdAt;

  @Column private @Nullable Instant deletedAt;

  // The ID is assigned here, so Spring Data can't tell a new user from a stored one by its ID.
  @Transient private boolean isNew = true;

  public User(String email, String name, String passwordHash) {
    this.id = UUID.randomUUID();
    this.email = email;
    this.name = name;
    this.passwordHash = passwordHash;
    this.createdAt = Instant.now();
  }

  @SuppressWarnings("NullAway.Init") // For JPA only; it fills the fields.
  protected User() {}

  @Override
  public UUID getId() {
    return id;
  }

  public @Nullable String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public @Nullable String getPasswordHash() {
    return passwordHash;
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
