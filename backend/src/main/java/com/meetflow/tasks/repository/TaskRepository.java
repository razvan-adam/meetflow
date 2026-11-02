package com.meetflow.tasks.repository;

import com.meetflow.tasks.domain.Task;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<Task, UUID> {

  /** Where a task belongs, read without loading the task itself. */
  record TaskPlace(UUID teamId, UUID seriesId, UUID meetingId) {}

  /**
   * The tasks created in a meeting, oldest first. Those deleted after it stay, as part of its
   * record; those deleted during it were mistakes, so they don't.
   */
  @Query(
      """
      SELECT t FROM Task t
      WHERE t.meetingId = :meetingId
        AND (t.deletedAt IS NULL
          OR t.deletedInMeetingId IS NULL
          OR t.deletedInMeetingId <> t.meetingId)
      ORDER BY t.createdAt
      """)
  List<Task> findRecordedInMeeting(UUID meetingId);

  /** The task, unless it was deleted during its own meeting: see {@link #findRecordedInMeeting}. */
  @Query(
      """
      SELECT t FROM Task t
      WHERE t.id = :id
        AND (t.deletedAt IS NULL
          OR t.deletedInMeetingId IS NULL
          OR t.deletedInMeetingId <> t.meetingId)
      """)
  Optional<Task> findRecordedById(UUID id);

  Optional<Task> findByIdAndDeletedAtIsNull(UUID id);

  Optional<TaskPlace> findPlaceByIdAndDeletedAtIsNull(UUID id);
}
