package com.meetflow.tasks.repository;

import com.meetflow.tasks.domain.Task;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  /** What the user still has to do, soonest deadline first. */
  @Query(
      """
      SELECT t FROM Task t
      WHERE t.ownerId = :ownerId AND t.status <> com.meetflow.tasks.domain.TaskStatus.DONE
        AND t.deletedAt IS NULL
      ORDER BY t.deadline, t.createdAt
      """)
  List<Task> findPendingOwnedBy(UUID ownerId);

  /** The tasks carried over to a meeting, soonest deadline first. */
  @Query(
      nativeQuery = true,
      value =
          """
          SELECT t.* FROM task t
          JOIN meeting_task_review r ON r.task_id = t.id
          WHERE r.meeting_id = :meetingId
            AND (t.deleted_at IS NULL OR t.deleted_in_meeting_id = :meetingId OR :closed)
          ORDER BY t.deadline, t.created_at
          """)
  List<Task> findCarriedOverTo(UUID meetingId, boolean closed);

  /** Puts every task of the series that isn't done or deleted on the meeting's list. */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          INSERT INTO meeting_task_review (meeting_id, task_id, series_id)
          SELECT CAST(:meetingId AS uuid), t.id, t.series_id FROM task t
          WHERE t.series_id = :seriesId AND t.status <> 'DONE' AND t.deleted_at IS NULL
          """)
  void reviewOpenTasks(UUID meetingId, UUID seriesId);

  /** Puts the task on the meeting's list, unless it is already there. */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          INSERT INTO meeting_task_review (meeting_id, task_id, series_id)
          VALUES (:meetingId, :taskId, :seriesId)
          ON CONFLICT DO NOTHING
          """)
  void reviewTask(UUID meetingId, UUID taskId, UUID seriesId);

  @Modifying
  @Query(
      nativeQuery = true,
      value = "DELETE FROM meeting_task_review WHERE meeting_id = :meetingId AND task_id = :taskId")
  void removeFromReview(UUID meetingId, UUID taskId);

  /** Keeps the status of the meeting's carried-over tasks as they were when it closed. */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          UPDATE meeting_task_review r SET status_at_close = t.status
          FROM task t
          WHERE r.task_id = t.id AND r.meeting_id = :meetingId
          """)
  void recordStatusAtClose(UUID meetingId);
}
