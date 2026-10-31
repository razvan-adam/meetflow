package com.meetflow.teams.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class NotTeamAdminException extends ErrorResponseException {

  public NotTeamAdminException() {
    super(HttpStatus.FORBIDDEN);
    getBody().setDetail("Only team admins can do this.");
  }
}
