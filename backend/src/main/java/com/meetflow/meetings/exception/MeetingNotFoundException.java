package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Also used for a meeting of a team the user isn't a member of, so it isn't revealed. */
public class MeetingNotFoundException extends ErrorResponseException {

  public MeetingNotFoundException() {
    super(HttpStatus.NOT_FOUND);
    getBody().setDetail("Meeting not found.");
  }
}
