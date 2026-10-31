package com.meetflow.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/** A record of an email sent, so none is sent twice. The body isn't stored. */
@Entity
public class EmailDelivery implements Persistable<UUID> {

  /** What the email is about; {@code referenceId} points to it. */
  public enum Kind {
    INVITATION,
    MINUTES,
    TASK_SUMMARY
  }

  @Id private UUID id;

  // Only written to the database, never read in Java; @Column keeps Error Prone from flagging these
  // as unused.
  @Column
  @Enumerated(EnumType.STRING)
  private Kind kind;

  @Column private UUID referenceId;

  @Column private String recipientEmail;

  @Column private Instant sentAt;

  @Column private @Nullable String providerMessageId;

  // The ID is assigned here, so Spring Data can't tell a new record from a stored one by its ID.
  @Transient private boolean isNew = true;

  public EmailDelivery(
      Kind kind, UUID referenceId, String recipientEmail, @Nullable String providerMessageId) {
    this.id = UUID.randomUUID();
    this.kind = kind;
    this.referenceId = referenceId;
    this.recipientEmail = recipientEmail;
    this.sentAt = Instant.now();
    this.providerMessageId = providerMessageId;
  }

  @SuppressWarnings("NullAway.Init") // For JPA only; it fills the fields.
  protected EmailDelivery() {}

  @Override
  public UUID getId() {
    return id;
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
