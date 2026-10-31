package com.meetflow.notifications.service;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For local development: sends nothing and logs the links in the email, so they can be opened by
 * hand. Only the links are logged, never the recipient or the text (personal data, GDPR).
 */
public class LogEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

  private static final Pattern LINK = Pattern.compile("https?://\\S+");

  @Override
  public @Nullable String send(EmailMessage message) {
    var links = LINK.matcher(message.text()).results().map(match -> match.group()).toList();
    log.info("Email not sent (meetflow.email.provider=log). Links in it: {}", links);
    return null;
  }
}
