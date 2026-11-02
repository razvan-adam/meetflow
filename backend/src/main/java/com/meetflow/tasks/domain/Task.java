package com.meetflow.tasks.domain;

import com.meetflow.meetings.TaskOrigin;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * A commitment: something to do, with exactly one owner and a deadline. It is created during a
 * meeting, from an agenda item or a decision of that meeting, and starts open.
 */
@Entity
public class Task implements Persistable<UUID> {

  @Id private UUID id;

  private UUID teamId;

  private UUID seriesId;

  private UUID meetingId;

  private @Nullable UUID decisionId;

  private UUID agendaItemId;

  private String description;

  private UUID ownerId;

  private LocalDate deadline;

  @Enumerated(EnumType.STRING)
  private TaskStatus status;

  @SuppressWarnings("UnusedVariable") // The table requires it.
  private UUID createdBy;

  @SuppressWarnings("UnusedVariable") // Tasks are sorted by this column.
  private Instant createdAt;

  @SuppressWarnings("UnusedVariable") // The table requires it.
  private Instant updatedAt;

  private @Nullable Instant deletedAt;

  private @Nullable UUID deletedBy;

  private @Nullable UUID deletedInMeetingId;

  // Stops two changes to a task made at the very same moment from overwriting each other.
  @SuppressWarnings("UnusedVariable") // Hibernate reads and increments it.
  @Version
  private long version;

  // The ID is assigned here, so Spring Data can't tell a new task from a stored one by its ID.
  @Transient private boolean isNew = true;

  public Task(
      TaskOrigin origin, String description, UUID ownerId, LocalDate deadline, UUID createdBy) {
    this.id = UUID.randomUUID();
    this.teamId = origin.teamId();
    this.seriesId = origin.seriesId();
    this.meetingId = origin.meetingId();
    this.decisionId = origin.decisionId();
    this.agendaItemId = origin.agendaItemId();
    this.description = description;
    this.ownerId = ownerId;
    this.deadline = deadline;
    this.status = TaskStatus.OPEN;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  @SuppressWarnings("NullAway.Init") // For JPA only; it fills the fields.
  protected Task() {}

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

  public UUID getMeetingId() {
    return meetingId;
  }

  public @Nullable UUID getDecisionId() {
    return decisionId;
  }

  public UUID getAgendaItemId() {
    return agendaItemId;
  }

  public String getDescription() {
    return description;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public LocalDate getDeadline() {
    return deadline;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public @Nullable Instant getDeletedAt() {
    return deletedAt;
  }

  public @Nullable UUID getDeletedBy() {
    return deletedBy;
  }

  public @Nullable UUID getDeletedInMeetingId() {
    return deletedInMeetingId;
  }

  public void changeStatus(TaskStatus status) {
    this.status = status;
    this.updatedAt = Instant.now();
  }

  public void changeOwner(UUID ownerId) {
    this.ownerId = ownerId;
    this.updatedAt = Instant.now();
  }

  public void changeDeadline(LocalDate deadline) {
    this.deadline = deadline;
    this.updatedAt = Instant.now();
  }

  public void reword(String description) {
    this.description = description;
    this.updatedAt = Instant.now();
  }

  /**
   * Stops the task from being worked on but keeps it, with its history. During its own meeting it
   * lets go of its decision, so a decision whose tasks were all deleted can be removed again. After
   * that the decision can't be removed anyway, and the closed meeting still shows the task under
   * it.
   */
  public void delete(UUID deletedBy, UUID duringMeetingId) {
    this.deletedAt = Instant.now();
    this.deletedBy = deletedBy;
    this.deletedInMeetingId = duringMeetingId;
    if (duringMeetingId.equals(meetingId)) {
      this.decisionId = null;
    }
    this.updatedAt = this.deletedAt;
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
