package com.meetflow.tasks.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class TaskNotFoundException extends ErrorResponseException {

  public TaskNotFoundException() {
    super(HttpStatus.NOT_FOUND);
    getBody().setDetail("This task doesn't exist or isn't in one of your teams.");
  }
}
