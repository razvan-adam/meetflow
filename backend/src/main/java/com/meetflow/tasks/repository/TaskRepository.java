package com.meetflow.tasks.repository;

import com.meetflow.tasks.domain.Task;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {

  /** The tasks created in a meeting, without the deleted ones, oldest first. */
  List<Task> findByMeetingIdAndDeletedAtIsNullOrderByCreatedAt(UUID meetingId);
}
