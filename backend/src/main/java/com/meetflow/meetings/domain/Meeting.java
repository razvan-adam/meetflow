package com.meetflow.meetings.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * One meeting of a series, with its date and time and its participants. A series has at most one
 * meeting that isn't closed. The start and close timestamps are added with MF-15.
 */
@Entity
public class Meeting implements Persistable<UUID> {

  @Id private UUID id;

  private UUID teamId;

  private UUID seriesId;

  private Instant scheduledAt;

  @Enumerated(EnumType.STRING)
  private MeetingStatus status;

  /** Team members, checked when they are added. */
  @ElementCollection
  @CollectionTable(name = "meeting_participant", joinColumns = @JoinColumn(name = "meeting_id"))
  @Column(name = "user_id")
  private Set<UUID> participantIds = new HashSet<>();

  // Edits made from an outdated page simply replace earlier ones (user decision, MF-13). This only
  // stops two saves that run at the very same moment from mixing their participant lists.
  @Version private long version;

  // The ID is assigned here, so Spring Data can't tell a new meeting from a stored one by its ID.
  @Transient private boolean isNew = true;

  public Meeting(UUID teamId, UUID seriesId, Instant scheduledAt, Collection<UUID> participantIds) {
    this.id = UUID.randomUUID();
    this.teamId = teamId;
    this.seriesId = seriesId;
    this.scheduledAt = scheduledAt;
    this.status = MeetingStatus.SCHEDULED;
    this.participantIds.addAll(participantIds);
  }

  @SuppressWarnings("NullAway.Init") // For JPA only; it fills the fields.
  protected Meeting() {}

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getTeamId() {
    return teamId;
  }

  public UUID getSeriesId() {
    return seriesId;
  }

  public Instant getScheduledAt() {
    return scheduledAt;
  }

  public MeetingStatus getStatus() {
    return status;
  }

  public Set<UUID> getParticipantIds() {
    return Set.copyOf(participantIds);
  }

  /** The participants must already be checked to be members of the team. */
  public void reschedule(Instant scheduledAt, Collection<UUID> participantIds) {
    this.scheduledAt = scheduledAt;
    this.participantIds.retainAll(participantIds);
    this.participantIds.addAll(participantIds);
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
