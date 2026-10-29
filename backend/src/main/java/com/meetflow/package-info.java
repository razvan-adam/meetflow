/**
 * MeetFlow backend: a Spring Boot modular monolith with one application module per subpackage.
 *
 * <p>Every package is {@code @NullMarked}: types are non-null unless annotated {@code @Nullable}, and NullAway checks it
 * at compile time. See {@code docs/conventions.md}.
 */
@NullMarked
package com.meetflow;

import org.jspecify.annotations.NullMarked;
