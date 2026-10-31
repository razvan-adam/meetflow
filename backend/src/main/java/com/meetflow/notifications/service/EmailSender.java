package com.meetflow.notifications.service;

import com.meetflow.notifications.EmailNotSentException;
import org.jspecify.annotations.Nullable;

/** Delivers emails. Which implementation runs is set by {@code meetflow.email.provider}. */
public interface EmailSender {

  /**
   * Sends the email.
   *
   * @return the provider's message ID, or null when the email wasn't really sent (local log)
   * @throws EmailNotSentException if the provider rejected it or couldn't be reached
   */
  @Nullable String send(EmailMessage message);
}
