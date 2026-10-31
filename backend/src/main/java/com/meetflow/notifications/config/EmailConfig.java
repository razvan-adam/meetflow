package com.meetflow.notifications.config;

import com.meetflow.notifications.service.EmailSender;
import com.meetflow.notifications.service.LogEmailSender;
import com.meetflow.notifications.service.SesEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailProperties.class)
class EmailConfig {

  private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

  @Bean
  EmailSender emailSender(EmailProperties properties) {
    return switch (properties.provider()) {
      case SES -> {
        // The SES client is only created here, so local development needs no AWS credentials.
        var ses = SesV2Client.builder().region(Region.of(properties.region())).build();
        yield new SesEmailSender(ses, properties.from());
      }
      case LOG -> {
        log.warn(
            "Emails are not sent, only logged: set meetflow.email.provider=ses outside local"
                + " development.");
        yield new LogEmailSender();
      }
    };
  }
}
