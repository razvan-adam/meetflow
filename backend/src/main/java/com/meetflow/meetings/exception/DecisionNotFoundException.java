package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class DecisionNotFoundException extends ErrorResponseException {

  public DecisionNotFoundException() {
    super(HttpStatus.NOT_FOUND);
    getBody().setDetail("This decision doesn't exist or isn't in one of your teams.");
  }
}
