package com.meetflow.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Email settings ({@code meetflow.email.*}). The defaults suit local development; staging sets them
 * as environment variables in the ECS task definition (infra/modules/environment).
 *
 * @param provider {@code ses} to send through Amazon SES, {@code log} to only log the links
 * @param from the sender address, verified in SES
 * @param region the SES region
 * @param appUrl where the app is served, for links in emails
 */
@ConfigurationProperties("meetflow.email")
public record EmailProperties(
    @DefaultValue("log") Provider provider,
    @DefaultValue("MeetFlow <noreply@meetflow.local>") String from,
    @DefaultValue("eu-west-1") String region,
    @DefaultValue("http://localhost:5173") String appUrl) {

  public enum Provider {
    LOG,
    SES
  }
}
