package com.meetflow.notifications;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * The email provider rejected the email or couldn't be reached. Thrown inside the caller's
 * transaction, so whatever the email was about (e.g. an invitation) isn't saved either.
 */
public class EmailNotSentException extends ErrorResponseException {

  public EmailNotSentException(Throwable cause) {
    super(HttpStatus.SERVICE_UNAVAILABLE, cause);
    getBody().setDetail("The email couldn't be sent. Try again in a few minutes.");
  }
}
