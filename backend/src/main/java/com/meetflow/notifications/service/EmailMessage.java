package com.meetflow.notifications.service;

/** A plain-text email to one recipient. */
public record EmailMessage(String to, String subject, String text) {}
