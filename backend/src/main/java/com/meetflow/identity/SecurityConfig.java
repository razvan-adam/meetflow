package com.meetflow.identity;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Every endpoint requires an authenticated session, except the ones listed here. Sign-up, login and
 * invitation lookup are added to the public list when they are built.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/api/health")
                    .permitAll()
                    // Errors are forwarded to /error; checking it again
                    // would turn every error into a 401.
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // The React app reads the XSRF-TOKEN cookie and sends it back as the X-XSRF-TOKEN header.
        .csrf(csrf -> csrf.spa())
        // An API answers 401 instead of redirecting to a login page.
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .build();
  }
}
