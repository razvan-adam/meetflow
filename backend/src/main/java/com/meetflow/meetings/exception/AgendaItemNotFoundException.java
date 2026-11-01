package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class AgendaItemNotFoundException extends ErrorResponseException {

  public AgendaItemNotFoundException() {
    super(HttpStatus.NOT_FOUND);
    getBody().setDetail("This agenda item doesn't exist or isn't in one of your teams.");
  }
}
