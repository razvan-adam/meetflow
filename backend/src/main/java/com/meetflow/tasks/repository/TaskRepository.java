package com.meetflow.tasks.repository;

import com.meetflow.tasks.domain.Task;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {

  /** Where a task belongs, read without loading the task itself. */
  record TaskPlace(UUID teamId, UUID seriesId, UUID meetingId) {}

  /** The tasks created in a meeting, without the deleted ones, oldest first. */
  List<Task> findByMeetingIdAndDeletedAtIsNullOrderByCreatedAt(UUID meetingId);

  Optional<Task> findByIdAndDeletedAtIsNull(UUID id);

  Optional<TaskPlace> findPlaceByIdAndDeletedAtIsNull(UUID id);
}
