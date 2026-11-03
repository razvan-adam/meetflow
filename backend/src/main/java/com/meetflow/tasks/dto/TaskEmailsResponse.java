package com.meetflow.tasks.dto;

/**
 * The task emails of a meeting.
 *
 * @param sent how many owners have been sent theirs so far
 */
public record TaskEmailsResponse(long sent) {}
