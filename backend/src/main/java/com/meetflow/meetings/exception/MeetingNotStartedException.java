package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class MeetingNotStartedException extends ErrorResponseException {

  public MeetingNotStartedException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("The meeting hasn't started yet.");
  }
}
