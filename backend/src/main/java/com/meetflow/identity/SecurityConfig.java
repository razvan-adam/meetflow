package com.meetflow.identity;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

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
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        // No redirect back after login, so don't open a session to remember the denied request;
        // otherwise every anonymous request would store a session in the database.
        .requestCache(cache -> cache.disable())
        // An API answers 401 instead of redirecting to a login page.
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .build();
  }

  /**
   * The session cookie, set here rather than with server.servlet.session.cookie.* properties, which
   * Spring Boot applies only when it starts the embedded server (not in tests). Browsers accept
   * Secure cookies from http://localhost, so this also works in local development.
   */
  @Bean
  CookieSerializer sessionCookieSerializer() {
    var serializer = new DefaultCookieSerializer();
    serializer.setUseSecureCookie(true);
    serializer.setUseHttpOnlyCookie(true);
    serializer.setSameSite("Lax");
    return serializer;
  }
}
