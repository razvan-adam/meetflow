package com.meetflow.teams.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * How a member's work is handed over before they are removed: every open task they own and every
 * series they organize, each exactly once.
 */
public record RemovalRequest(
    @NotNull @Size(max = 1000) List<@NotNull @Valid TaskHandover> tasks,
    @NotNull @Size(max = 500) List<@NotNull @Valid SeriesHandover> series) {

  /** {@code newOwnerId} is the member who takes the task over, or null to remove the task. */
  public record TaskHandover(@NotNull UUID taskId, @Nullable UUID newOwnerId) {}

  public record SeriesHandover(@NotNull UUID seriesId, @NotNull UUID organizerId) {}
}
