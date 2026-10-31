package com.meetflow.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class EmailAlreadyUsedException extends ErrorResponseException {

  public EmailAlreadyUsedException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("An account with this email already exists.");
  }
}
