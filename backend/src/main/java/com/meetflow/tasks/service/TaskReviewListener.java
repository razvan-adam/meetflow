package com.meetflow.tasks.service;

import com.meetflow.meetings.MeetingClosedEvent;
import com.meetflow.meetings.MeetingScheduledEvent;
import com.meetflow.tasks.repository.TaskRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps each meeting's list of carried-over tasks (MF-19). These run inside the transaction that
 * publishes the event, unlike the listeners that send emails: a meeting must never exist without
 * its open tasks, or close without recording their status.
 */
@Component
class TaskReviewListener {

  private final TaskRepository tasks;

  TaskReviewListener(TaskRepository tasks) {
    this.tasks = tasks;
  }

  @EventListener
  void on(MeetingScheduledEvent event) {
    tasks.reviewOpenTasks(event.meetingId(), event.seriesId());
  }

  @EventListener
  void on(MeetingClosedEvent event) {
    tasks.recordStatusAtClose(event.meetingId());
  }
}
