package com.meetflow.minutes.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Also used for the minutes of a team the user isn't a member of, so they aren't revealed. */
public class MinutesNotFoundException extends ErrorResponseException {

  public MinutesNotFoundException() {
    super(HttpStatus.NOT_FOUND);
    getBody().setDetail("Minutes not found.");
  }
}
