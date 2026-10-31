package com.meetflow.identity.repository;

import com.meetflow.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  /** Only active accounts have an email, so this never finds a deleted one. */
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);
}
