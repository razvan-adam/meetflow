package com.meetflow.teams.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** A handover that lists a task or series twice, or hands work to the member being removed. */
public class InvalidHandoverException extends ErrorResponseException {

  public InvalidHandoverException(String detail) {
    super(HttpStatus.BAD_REQUEST);
    getBody().setDetail(detail);
  }
}
