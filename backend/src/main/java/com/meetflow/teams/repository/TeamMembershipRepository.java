package com.meetflow.teams.repository;

import com.meetflow.teams.domain.TeamMembership;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, TeamMembership.Id> {

  List<TeamMembership> findByIdUserId(UUID userId);

  List<TeamMembership> findByIdTeamId(UUID teamId);
}
