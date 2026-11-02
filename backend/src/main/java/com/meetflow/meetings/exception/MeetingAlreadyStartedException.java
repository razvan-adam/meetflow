package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class MeetingAlreadyStartedException extends ErrorResponseException {

  public MeetingAlreadyStartedException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("The meeting has already started.");
  }
}
