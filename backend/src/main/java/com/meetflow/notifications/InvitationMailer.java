package com.meetflow.notifications;

import com.meetflow.notifications.config.EmailProperties;
import com.meetflow.notifications.domain.EmailDelivery;
import com.meetflow.notifications.repository.EmailDeliveryRepository;
import com.meetflow.notifications.service.EmailMessage;
import com.meetflow.notifications.service.EmailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends invitation emails (E1). Called directly, not through an event: the event table would store
 * the link's token, which must never be stored.
 */
@Service
public class InvitationMailer {

  private final EmailSender sender;
  private final EmailDeliveryRepository deliveries;
  private final EmailProperties properties;

  InvitationMailer(
      EmailSender sender, EmailDeliveryRepository deliveries, EmailProperties properties) {
    this.sender = sender;
    this.deliveries = deliveries;
    this.properties = properties;
  }

  /**
   * Sends the email and records it, in the caller's transaction.
   *
   * @throws EmailNotSentException if it couldn't be sent
   */
  @Transactional
  public void send(InvitationEmail invitation) {
    var link = properties.appUrl() + "/join/" + invitation.token();
    var text =
        """
        %1$s invited you to join %2$s on MeetFlow. You'll see the team's meetings, the decisions \
        taken and the tasks you own.

        Join %2$s:
        %3$s

        The link works once and expires in 7 days. If you weren't expecting this invitation, you \
        can ignore this email.
        """
            .formatted(invitation.inviterName(), invitation.teamName(), link);
    var subject =
        "%s invited you to %s on MeetFlow"
            .formatted(invitation.inviterName(), invitation.teamName());
    var messageId = sender.send(new EmailMessage(invitation.recipient(), subject, text));
    deliveries.save(
        new EmailDelivery(
            EmailDelivery.Kind.INVITATION,
            invitation.invitationId(),
            invitation.recipient(),
            messageId));
  }
}
