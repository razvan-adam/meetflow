package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class DecisionHasTasksException extends ErrorResponseException {

  public DecisionHasTasksException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("Tasks came from this decision, so it can't be removed.");
  }
}
