package com.meetflow.identity;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

class EmailAlreadyUsedException extends ErrorResponseException {

  EmailAlreadyUsedException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("An account with this email already exists.");
  }
}
