package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class NoRunningMeetingException extends ErrorResponseException {

  public NoRunningMeetingException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("No meeting of this series is running.");
  }
}
