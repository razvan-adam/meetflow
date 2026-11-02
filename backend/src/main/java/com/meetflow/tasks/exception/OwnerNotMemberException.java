package com.meetflow.tasks.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class OwnerNotMemberException extends ErrorResponseException {

  public OwnerNotMemberException() {
    super(HttpStatus.BAD_REQUEST);
    getBody().setDetail("A task's new owner must be a member of the team.");
  }
}
