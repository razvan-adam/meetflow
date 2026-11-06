package com.meetflow.tasks.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Removing a member with a handover that doesn't list exactly their open tasks, usually because a
 * task was assigned, finished or removed while the admin was choosing.
 */
public class UnresolvedTasksException extends ErrorResponseException {

  public UnresolvedTasksException() {
    super(HttpStatus.CONFLICT);
    getBody()
        .setDetail(
            "The member's open tasks have changed. Each one must be reassigned or removed first.");
  }
}
