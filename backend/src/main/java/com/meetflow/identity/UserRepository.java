package com.meetflow.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<User, UUID> {

  /** Only active accounts have an email, so this never finds a deleted one. */
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);
}
