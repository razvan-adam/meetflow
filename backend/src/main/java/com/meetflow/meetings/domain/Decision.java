package com.meetflow.meetings.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Something the team agreed in a meeting, about one of its agenda items. Changes to it are
 * serialized by the lock on its meeting, so it needs no version of its own.
 */
@Entity
public class Decision implements Persistable<UUID> {

  @Id private UUID id;

  @SuppressWarnings("UnusedVariable") // The table requires it.
  private UUID teamId;

  private UUID meetingId;

  private UUID agendaItemId;

  private String text;

  @SuppressWarnings("UnusedVariable") // The table requires it.
  private UUID createdBy;

  @SuppressWarnings("UnusedVariable") // Decisions are sorted by this column.
  private Instant createdAt;

  // The ID is assigned here, so Spring Data can't tell a new decision from a stored one by its ID.
  @Transient private boolean isNew = true;

  public Decision(UUID teamId, UUID meetingId, UUID agendaItemId, String text, UUID createdBy) {
    this.id = UUID.randomUUID();
    this.teamId = teamId;
    this.meetingId = meetingId;
    this.agendaItemId = agendaItemId;
    this.text = text;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
  }

  @SuppressWarnings("NullAway.Init") // For JPA only; it fills the fields.
  protected Decision() {}

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getMeetingId() {
    return meetingId;
  }

  public UUID getAgendaItemId() {
    return agendaItemId;
  }

  public String getText() {
    return text;
  }

  public void reword(String text) {
    this.text = text;
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
