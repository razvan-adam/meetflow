package com.meetflow.notifications.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Tries events again whose listeners failed or never finished, such as an email the provider
 * rejected or a restart halfway through sending. Listeners that send email check what was already
 * sent, so running one again only reaches those who were missed.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class EventRetry {

  private static final Duration OLDER_THAN = Duration.ofMinutes(5);

  private final IncompleteEventPublications incomplete;

  EventRetry(IncompleteEventPublications incomplete) {
    this.incomplete = incomplete;
  }

  @Scheduled(fixedDelayString = "PT5M")
  void retry() {
    incomplete.resubmitIncompletePublicationsOlderThan(OLDER_THAN);
  }
}
