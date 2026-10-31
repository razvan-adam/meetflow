package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ParticipantNotMemberException extends ErrorResponseException {

  public ParticipantNotMemberException() {
    super(HttpStatus.BAD_REQUEST);
    getBody().setDetail("Participants must be members of the team.");
  }
}
