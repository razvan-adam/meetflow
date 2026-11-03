package com.meetflow.identity;

import com.meetflow.identity.domain.User;
import com.meetflow.identity.repository.UserRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looks up accounts for other modules. Callers only show names to people who share a team, and only
 * use email addresses to send email (docs/data-model.md).
 */
@Service
public class UserDirectory {

  private final UserRepository users;

  UserDirectory(UserRepository users) {
    this.users = users;
  }

  /** The ID of the active account with this email, compared case-insensitively. */
  @Transactional(readOnly = true)
  public Optional<UUID> findIdByEmail(String email) {
    return users.findByEmail(normalizeEmail(email)).map(User::getId);
  }

  /** The names of these users, by ID. */
  @Transactional(readOnly = true)
  public Map<UUID, String> namesOf(Collection<UUID> userIds) {
    return users.findAllById(userIds).stream()
        .collect(Collectors.toMap(User::getId, User::getName));
  }

  /**
   * The email addresses of these users, by ID, for sending them email. Never show them to others. A
   * deleted account has none, so it is left out.
   */
  @Transactional(readOnly = true)
  public Map<UUID, String> emailsOf(Collection<UUID> userIds) {
    var emails = new HashMap<UUID, String>();
    for (var user : users.findAllById(userIds)) {
      var email = user.getEmail();
      if (email != null) {
        emails.put(user.getId(), email);
      }
    }
    return emails;
  }

  /** Emails are stored lowercased, so Ana@Example.com and ana@example.com are one account. */
  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
