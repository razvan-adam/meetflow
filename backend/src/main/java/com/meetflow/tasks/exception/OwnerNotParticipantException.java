package com.meetflow.tasks.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class OwnerNotParticipantException extends ErrorResponseException {

  public OwnerNotParticipantException() {
    super(HttpStatus.BAD_REQUEST);
    getBody().setDetail("A task's owner must be a participant of the meeting.");
  }
}
