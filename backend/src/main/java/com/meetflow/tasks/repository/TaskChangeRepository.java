package com.meetflow.tasks.repository;

import com.meetflow.tasks.domain.TaskChange;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskChangeRepository extends JpaRepository<TaskChange, UUID> {

  /** A task's history, latest first. */
  List<TaskChange> findByTaskIdOrderByChangedAtDesc(UUID taskId);
}
