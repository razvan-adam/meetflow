package com.meetflow.identity;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-up, and looking up accounts for login. */
@Service
class UserService implements UserDetailsService {

  /** Long enough to resist guessing without forcing composition rules (NIST SP 800-63B). */
  static final int MIN_PASSWORD_LENGTH = 12;

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;

  UserService(UserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  /** Creates the account and returns it as the user to log in. */
  @Transactional
  CurrentUser signUp(SignUpRequest request) {
    var email = normalizeEmail(request.email());
    if (users.existsByEmail(email)) {
      throw new EmailAlreadyUsedException();
    }
    var user = new User(email, request.name().strip(), passwordEncoder.encode(request.password()));
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      // Someone signed up with the same email at the same moment.
      throw new EmailAlreadyUsedException();
    }
    return new CurrentUser(user.getId(), email, null);
  }

  /** The name is read from the database: it isn't in the session, which may be older. */
  @Transactional(readOnly = true)
  UserResponse describe(CurrentUser user) {
    var name = users.findById(user.id()).orElseThrow().getName();
    return new UserResponse(user.id(), name, user.getUsername());
  }

  @Override
  @Transactional(readOnly = true)
  public CurrentUser loadUserByUsername(String username) {
    var email = normalizeEmail(username);
    return users
        .findByEmail(email)
        .map(user -> new CurrentUser(user.getId(), email, user.getPasswordHash()))
        .orElseThrow(() -> new UsernameNotFoundException("No account with this email"));
  }

  /** Emails are stored lowercased, so Ana@Example.com and ana@example.com are one account. */
  private static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
