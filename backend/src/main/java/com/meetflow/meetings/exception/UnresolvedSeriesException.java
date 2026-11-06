package com.meetflow.meetings.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Removing a member with a handover that doesn't list exactly the series they organize, usually
 * because the organizer changed while the admin was choosing.
 */
public class UnresolvedSeriesException extends ErrorResponseException {

  public UnresolvedSeriesException() {
    super(HttpStatus.CONFLICT);
    getBody()
        .setDetail(
            "The series the member organizes have changed. Each one needs a new organizer first.");
  }
}
