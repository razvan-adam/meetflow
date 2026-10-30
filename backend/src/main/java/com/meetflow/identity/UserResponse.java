package com.meetflow.identity;

import java.util.UUID;

record UserResponse(UUID id, String name, String email) {}
