package com.meetflow.tasks.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * One line of a task's history: who changed its status, owner, deadline or description, or deleted
 * it, and when. Rows are only inserted, never updated. Correcting a description during the task's
 * own meeting isn't recorded: nobody has been sent the task yet.
 */
@Entity
public class TaskChange implements Persistable<UUID> {

  @Id private UUID id;

  private UUID taskId;

  @SuppressWarnings("UnusedVariable") // The table requires it.
  private UUID teamId;

  private UUID changedBy;

  private Instant changedAt;

  private @Nullable UUID meetingId;

  @Enumerated(EnumType.STRING)
  private @Nullable TaskStatus oldStatus;

  @Enumerated(EnumType.STRING)
  private @Nullable TaskStatus newStatus;

  private @Nullable UUID oldOwnerId;

  private @Nullable UUID newOwnerId;

  private @Nullable LocalDate oldDeadline;

  private @Nullable LocalDate newDeadline;

  private @Nullable String oldDescription;

  private @Nullable String newDescription;

  private boolean deleted;

  // The ID is assigned here, so Spring Data can't tell a new row from a stored one by its ID.
  @Transient private boolean isNew = true;

  /** The first line of a task's history: it was created, during the meeting it belongs to. */
  public static TaskChange creationOf(Task task, UUID createdBy) {
    var change = new TaskChange(task, createdBy, task.getMeetingId());
    change.newStatus = task.getStatus();
    change.newOwnerId = task.getOwnerId();
    change.newDeadline = task.getDeadline();
    return change;
  }

  public static TaskChange ofStatus(
      Task task, UUID changedBy, @Nullable UUID meetingId, TaskStatus from, TaskStatus to) {
    var change = new TaskChange(task, changedBy, meetingId);
    change.oldStatus = from;
    change.newStatus = to;
    return change;
  }

  public static TaskChange ofOwner(
      Task task, UUID changedBy, @Nullable UUID meetingId, UUID from, UUID to) {
    var change = new TaskChange(task, changedBy, meetingId);
    change.oldOwnerId = from;
    change.newOwnerId = to;
    return change;
  }

  public static TaskChange ofDeadline(
      Task task, UUID changedBy, @Nullable UUID meetingId, LocalDate from, LocalDate to) {
    var change = new TaskChange(task, changedBy, meetingId);
    change.oldDeadline = from;
    change.newDeadline = to;
    return change;
  }

  public static TaskChange ofDescription(
      Task task, UUID changedBy, UUID meetingId, String from, String to) {
    var change = new TaskChange(task, changedBy, meetingId);
    change.oldDescription = from;
    change.newDescription = to;
    return change;
  }

  public static TaskChange ofDeletion(Task task, UUID changedBy, UUID meetingId) {
    var change = new TaskChange(task, changedBy, meetingId);
    change.deleted = true;
    return change;
  }

  private TaskChange(Task task, UUID changedBy, @Nullable UUID meetingId) {
    this.id = UUID.randomUUID();
    this.taskId = task.getId();
    this.teamId = task.getTeamId();
    this.changedBy = changedBy;
    this.changedAt = Instant.now();
    this.meetingId = meetingId;
  }

  @SuppressWarnings("NullAway.Init") // For JPA only; it fills the fields.
  protected TaskChange() {}

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getTaskId() {
    return taskId;
  }

  public UUID getChangedBy() {
    return changedBy;
  }

  public Instant getChangedAt() {
    return changedAt;
  }

  public @Nullable UUID getMeetingId() {
    return meetingId;
  }

  public @Nullable TaskStatus getOldStatus() {
    return oldStatus;
  }

  public @Nullable TaskStatus getNewStatus() {
    return newStatus;
  }

  public @Nullable UUID getOldOwnerId() {
    return oldOwnerId;
  }

  public @Nullable UUID getNewOwnerId() {
    return newOwnerId;
  }

  public @Nullable LocalDate getOldDeadline() {
    return oldDeadline;
  }

  public @Nullable LocalDate getNewDeadline() {
    return newDeadline;
  }

  public @Nullable String getOldDescription() {
    return oldDescription;
  }

  public @Nullable String getNewDescription() {
    return newDescription;
  }

  public boolean isDeleted() {
    return deleted;
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
