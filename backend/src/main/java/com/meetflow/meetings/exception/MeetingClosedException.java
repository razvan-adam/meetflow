package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class MeetingClosedException extends ErrorResponseException {

  public MeetingClosedException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("The meeting is closed and can't be changed.");
  }
}
