package com.meetflow;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Fails when a module reaches into another module's internals or the modules depend on each other
 * in a cycle.
 */
class ModularityTests {

  @Test
  void modulesRespectTheirBoundaries() {
    ApplicationModules.of(MeetflowApplication.class).verify();
  }
}
