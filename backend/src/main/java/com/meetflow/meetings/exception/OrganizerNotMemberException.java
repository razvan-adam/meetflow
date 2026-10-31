package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class OrganizerNotMemberException extends ErrorResponseException {

  public OrganizerNotMemberException() {
    super(HttpStatus.BAD_REQUEST);
    getBody().setDetail("The organizer must be a member of the team.");
  }
}
