package com.meetflow.tasks.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class NotAllowedToChangeTaskException extends ErrorResponseException {

  public NotAllowedToChangeTaskException() {
    super(HttpStatus.FORBIDDEN);
    getBody().setDetail("Only the task's owner and the series organizer can change this.");
  }
}
