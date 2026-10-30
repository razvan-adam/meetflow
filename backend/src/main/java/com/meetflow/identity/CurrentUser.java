package com.meetflow.identity;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The logged-in user, as stored in the session. Other modules get it with
 * {@code @AuthenticationPrincipal CurrentUser user} and use its ID.
 *
 * <p>It holds only the ID and email: the session is serialized into the database, and the name can
 * change while the user is logged in. Roles belong to teams, so there are no authorities.
 */
public final class CurrentUser implements UserDetails, CredentialsContainer {

  // Sessions are stored serialized; changing the fields logs everyone out.
  @Serial private static final long serialVersionUID = 1L;

  private final UUID id;
  private final String email;
  private @Nullable String passwordHash;

  CurrentUser(UUID id, String email, @Nullable String passwordHash) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
  }

  public UUID id() {
    return id;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public @Nullable String getPassword() {
    return passwordHash;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of();
  }

  /** Called after login, so the password hash never reaches the session. */
  @Override
  public void eraseCredentials() {
    passwordHash = null;
  }
}
