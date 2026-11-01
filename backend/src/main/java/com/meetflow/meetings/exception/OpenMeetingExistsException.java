package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** A series has at most one meeting that isn't closed (docs/data-model.md). */
public class OpenMeetingExistsException extends ErrorResponseException {

  public OpenMeetingExistsException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("This series already has a meeting that isn't closed.");
  }
}
