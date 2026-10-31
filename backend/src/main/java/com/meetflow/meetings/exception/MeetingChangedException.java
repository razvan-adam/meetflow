package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** The edit was made on an outdated copy of the meeting (optimistic locking). */
public class MeetingChangedException extends ErrorResponseException {

  public MeetingChangedException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("The meeting was changed by someone else. Reload it and try again.");
  }
}
