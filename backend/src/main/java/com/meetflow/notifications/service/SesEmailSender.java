package com.meetflow.notifications.service;

import com.meetflow.notifications.EmailNotSentException;
import java.nio.charset.StandardCharsets;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Sends through Amazon SES. AWS credentials come from the ECS task role; nothing is configured in
 * the app.
 */
public class SesEmailSender implements EmailSender {

  private final SesV2Client ses;
  private final String from;

  public SesEmailSender(SesV2Client ses, String from) {
    this.ses = ses;
    this.from = from;
  }

  @Override
  public String send(EmailMessage message) {
    var request =
        SendEmailRequest.builder()
            .fromEmailAddress(from)
            .destination(Destination.builder().toAddresses(message.to()).build())
            .content(
                EmailContent.builder()
                    .simple(
                        Message.builder()
                            .subject(utf8(message.subject()))
                            .body(Body.builder().text(utf8(message.text())).build())
                            .build())
                    .build())
            .build();
    try {
      return ses.sendEmail(request).messageId();
    } catch (SdkException e) {
      throw new EmailNotSentException(e);
    }
  }

  private static Content utf8(String data) {
    return Content.builder().data(data).charset(StandardCharsets.UTF_8.name()).build();
  }
}
