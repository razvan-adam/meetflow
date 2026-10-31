package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Also used for a series of a team the user isn't a member of, so it isn't revealed. */
public class SeriesNotFoundException extends ErrorResponseException {

  public SeriesNotFoundException() {
    super(HttpStatus.NOT_FOUND);
    getBody().setDetail("Meeting series not found.");
  }
}
