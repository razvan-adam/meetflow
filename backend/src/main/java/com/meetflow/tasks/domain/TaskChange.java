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
 * One line of a task's history: who changed what and when. Rows are only inserted, never updated.
 * Nothing reads them yet; the task screen (MF-18) and the minutes (MF-22) will.
 */
@Entity
@SuppressWarnings("UnusedVariable")
public class TaskChange implements Persistable<UUID> {

  @Id private UUID id;

  private UUID taskId;

  private UUID teamId;

  private UUID changedBy;

  private Instant changedAt;

  private @Nullable UUID meetingId;

  @Enumerated(EnumType.STRING)
  private @Nullable TaskStatus newStatus;

  private @Nullable UUID newOwnerId;

  private @Nullable LocalDate newDeadline;

  // The ID is assigned here, so Spring Data can't tell a new row from a stored one by its ID.
  @Transient private boolean isNew = true;

  /** The first line of a task's history: it was created, during the meeting it belongs to. */
  public static TaskChange creationOf(Task task, UUID createdBy) {
    var change = new TaskChange();
    change.id = UUID.randomUUID();
    change.taskId = task.getId();
    change.teamId = task.getTeamId();
    change.changedBy = createdBy;
    change.changedAt = Instant.now();
    change.meetingId = task.getMeetingId();
    change.newStatus = task.getStatus();
    change.newOwnerId = task.getOwnerId();
    change.newDeadline = task.getDeadline();
    return change;
  }

  @SuppressWarnings("NullAway.Init") // For JPA and creationOf, which fill the fields.
  protected TaskChange() {}

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
