package com.meetflow.meetings.repository;

import com.meetflow.meetings.domain.MeetingSeries;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingSeriesRepository extends JpaRepository<MeetingSeries, UUID> {

  List<MeetingSeries> findByTeamId(UUID teamId);
}
