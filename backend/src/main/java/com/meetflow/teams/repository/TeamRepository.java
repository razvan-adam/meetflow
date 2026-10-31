package com.meetflow.teams.repository;

import com.meetflow.teams.domain.Team;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {}
