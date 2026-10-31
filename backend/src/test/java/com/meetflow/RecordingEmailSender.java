package com.meetflow;

import com.meetflow.notifications.EmailNotSentException;
import com.meetflow.notifications.service.EmailMessage;
import com.meetflow.notifications.service.EmailSender;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real email sender in integration tests: keeps what would be sent, so tests can read
 * the emails, and can be told to fail like an unreachable provider.
 */
@Primary
public class RecordingEmailSender implements EmailSender {

  private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

  private volatile boolean failing;

  @Override
  public String send(EmailMessage message) {
    if (failing) {
      throw new EmailNotSentException(new IllegalStateException("Provider down (test)"));
    }
    sent.add(message);
    return "test-" + UUID.randomUUID();
  }

  /** The last email sent to this address. */
  public EmailMessage lastTo(String address) {
    return sent.reversed().stream()
        .filter(message -> message.to().equals(address))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No email sent to " + address));
  }

  public long countTo(String address) {
    return sent.stream().filter(message -> message.to().equals(address)).count();
  }

  public void setFailing(boolean failing) {
    this.failing = failing;
  }
}
