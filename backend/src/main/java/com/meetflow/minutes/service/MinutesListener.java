package com.meetflow.minutes.service;

import com.meetflow.meetings.MeetingClosedEvent;
import com.meetflow.notifications.MinutesMailer;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to a closed meeting. The minutes are written in the same transaction, so they hold what
 * the meeting held when it closed and can't be lost. The email goes out after it commits, and a
 * failed one is retried through the event publication table.
 *
 * <p>The email listener isn't an {@code @ApplicationModuleListener} because that runs in one
 * transaction: a failure partway through would forget who had already been sent the email.
 */
@Component
class MinutesListener {

  private final MinutesService minutes;
  private final MinutesMailer mailer;

  MinutesListener(MinutesService minutes, MinutesMailer mailer) {
    this.minutes = minutes;
    this.mailer = mailer;
  }

  // Last, so the tasks module has recorded the status at closing.
  @EventListener
  @Order(Ordered.LOWEST_PRECEDENCE)
  void generate(MeetingClosedEvent event) {
    minutes.generate(event);
  }

  @Async
  @TransactionalEventListener
  void email(MeetingClosedEvent event) {
    mailer.send(minutes.emailFor(event.meetingId()));
  }
}
