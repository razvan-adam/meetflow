package com.meetflow.notifications.service;

import com.meetflow.notifications.EmailNotSentException;
import com.meetflow.notifications.domain.EmailDelivery;
import com.meetflow.notifications.repository.EmailDeliveryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Sends the emails that follow a closed meeting. Not transactional: each delivery is recorded as
 * soon as its email is sent, so a retry only reaches those who didn't get it.
 */
@Service
public class MeetingEmails {

  private final EmailSender sender;
  private final EmailDeliveryRepository deliveries;

  MeetingEmails(EmailSender sender, EmailDeliveryRepository deliveries) {
    this.sender = sender;
    this.deliveries = deliveries;
  }

  /**
   * Tries every recipient who hasn't had this kind of email about the meeting yet.
   *
   * @throws IllegalStateException if it couldn't be sent to at least one of them. It doesn't carry
   *     the provider's error, whose message can hold the recipient's address: this exception gets
   *     logged, and personal data must stay out of the logs.
   */
  public void sendOnce(EmailDelivery.Kind kind, UUID meetingId, List<EmailMessage> messages) {
    var failed = 0;
    String errorType = "";
    for (var message : messages) {
      try {
        if (!deliveries.existsByKindAndReferenceIdAndRecipientEmail(
            kind, meetingId, message.to())) {
          var messageId = sender.send(message);
          deliveries.save(new EmailDelivery(kind, meetingId, message.to(), messageId));
        }
      } catch (EmailNotSentException e) {
        failed++;
        errorType = e.getCause() == null ? "unknown" : e.getCause().getClass().getSimpleName();
      }
    }
    if (failed > 0) {
      throw new IllegalStateException(
          "The %s email of meeting %s couldn't be sent to %d recipient(s): %s. It will be retried."
              .formatted(kind, meetingId, failed, errorType));
    }
  }
}
