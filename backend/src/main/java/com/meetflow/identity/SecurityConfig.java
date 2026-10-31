package com.meetflow.identity;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Every endpoint requires an authenticated session, except the ones listed here. Invitation lookup
 * is added to the public list when it is built.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokens)
      throws Exception {
    var matcher = PathPatternRequestMatcher.withDefaults();
    return http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/api/health")
                    .permitAll()
                    // Sign-up and login.
                    .requestMatchers(
                        matcher.matcher(HttpMethod.POST, "/api/users"),
                        matcher.matcher(HttpMethod.POST, "/api/session"))
                    .permitAll()
                    // Errors are forwarded to /error; checking it again
                    // would turn every error into a 401.
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // The React app reads the XSRF-TOKEN cookie and sends it back as the X-XSRF-TOKEN header.
        .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        // Logout deletes the session from the database and replaces the CSRF token; the new one is
        // sent right away so the login form can be used without another request.
        .logout(
            logout ->
                logout
                    .logoutRequestMatcher(matcher.matcher(HttpMethod.DELETE, "/api/session"))
                    .logoutSuccessHandler(
                        (request, response, authentication) -> {
                          csrfTokens.saveToken(
                              csrfTokens.generateToken(request), request, response);
                          response.setStatus(HttpStatus.NO_CONTENT.value());
                        }))
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

  /** Shared by the filter chain and {@link SessionService}, which replaces the token at login. */
  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    return CookieCsrfTokenRepository.withHttpOnlyFalse();
  }

  /** bcrypt, with the algorithm stored in each hash so it can be upgraded later. */
  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    var provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
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
