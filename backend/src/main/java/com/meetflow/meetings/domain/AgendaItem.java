package com.meetflow.meetings.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * A topic on a meeting's agenda. Items carried over from the previous meeting are created by MF-19,
 * and notes are added with MF-15.
 */
@Entity
public class AgendaItem implements Persistable<UUID> {

  @Id private UUID id;

  private UUID teamId;

  private UUID meetingId;

  private int position;

  private String title;

  @Enumerated(EnumType.STRING)
  private AgendaItemStatus status;

  private @Nullable UUID carriedOverFromId;

  // Rejects a change to an item that was removed or changed at the very same moment.
  @Version private long version;

  // The ID is assigned here, so Spring Data can't tell a new item from a stored one by its ID.
  @Transient private boolean isNew = true;

  public AgendaItem(UUID teamId, UUID meetingId, int position, String title) {
    this.id = UUID.randomUUID();
    this.teamId = teamId;
    this.meetingId = meetingId;
    this.position = position;
    this.title = title;
    this.status = AgendaItemStatus.PENDING;
  }

  @SuppressWarnings("NullAway.Init") // For JPA only; it fills the fields.
  protected AgendaItem() {}

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public UUID getMeetingId() {
    return meetingId;
  }

  public int getPosition() {
    return position;
  }

  public String getTitle() {
    return title;
  }

  public boolean isCarriedOver() {
    return carriedOverFromId != null;
  }

  public void rename(String title) {
    this.title = title;
  }

  public void moveTo(int position) {
    this.position = position;
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
