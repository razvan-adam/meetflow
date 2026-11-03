package com.meetflow.notifications.repository;

import com.meetflow.notifications.domain.EmailDelivery;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {

  boolean existsByKindAndReferenceIdAndRecipientEmail(
      EmailDelivery.Kind kind, UUID referenceId, String recipientEmail);

  long countByKindAndReferenceId(EmailDelivery.Kind kind, UUID referenceId);

  long countByKindAndReferenceIdAndRecipientEmailIn(
      EmailDelivery.Kind kind, UUID referenceId, Collection<String> recipientEmails);
}
