package com.meetflow.notifications.repository;

import com.meetflow.notifications.domain.EmailDelivery;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {}
