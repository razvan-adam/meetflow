package com.meetflow.tasks.repository;

import com.meetflow.tasks.domain.TaskChange;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskChangeRepository extends JpaRepository<TaskChange, UUID> {}
