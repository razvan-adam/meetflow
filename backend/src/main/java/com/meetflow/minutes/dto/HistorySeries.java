package com.meetflow.minutes.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A series in a team's meeting history, with its closed meetings, newest first. */
public record HistorySeries(UUID id, String name, List<Meeting> meetings) {

  /**
   * A closed meeting, which opens its minutes.
   *
   * @param date the date it was held, as in its minutes
   * @param decisions all of its decisions, in agenda order
   * @param taskCount the tasks created in it, as in its minutes
   */
  public record Meeting(UUID id, LocalDate date, List<String> decisions, int taskCount) {}
}
