package com.meetflow.tasks.repository;

import com.meetflow.tasks.domain.TaskChange;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskChangeRepository extends JpaRepository<TaskChange, UUID> {

  /** A task's history, latest first. */
  List<TaskChange> findByTaskIdOrderByChangedAtDesc(UUID taskId);

  /** The status changes made during a meeting, oldest first. */
  List<TaskChange> findByMeetingIdAndOldStatusIsNotNullOrderByChangedAt(UUID meetingId);

  /** The rewordings of these tasks since a moment, oldest first. */
  @Query(
      """
      SELECT c FROM TaskChange c
      WHERE c.taskId IN :taskIds AND c.oldDescription IS NOT NULL AND c.changedAt > :since
      ORDER BY c.changedAt
      """)
  List<TaskChange> findRewordingsSince(Collection<UUID> taskIds, Instant since);
}
