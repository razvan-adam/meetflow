package com.meetflow.teams.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Taking admin rights from, or removing, the team's only admin. */
public class LastAdminException extends ErrorResponseException {

  public LastAdminException() {
    super(HttpStatus.CONFLICT);
    getBody().setDetail("A team always keeps at least one admin.");
  }
}
