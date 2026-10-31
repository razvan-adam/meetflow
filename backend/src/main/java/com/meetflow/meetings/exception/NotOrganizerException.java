package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class NotOrganizerException extends ErrorResponseException {

  public NotOrganizerException() {
    super(HttpStatus.FORBIDDEN);
    getBody().setDetail("Only the series organizer can do this.");
  }
}
