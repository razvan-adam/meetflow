package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class AgendaChangedException extends ErrorResponseException {

  public AgendaChangedException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("The agenda has changed. Reload it and try again.");
  }
}
