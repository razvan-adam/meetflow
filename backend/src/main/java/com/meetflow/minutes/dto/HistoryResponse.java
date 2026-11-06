package com.meetflow.minutes.dto;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One page of a team's meeting history.
 *
 * @param series the page's meetings by series. A series can continue on the next page, which then
 *     starts with it again.
 * @param next the meeting to ask for older meetings after, or null if there are none
 */
public record HistoryResponse(List<HistorySeries> series, @Nullable UUID next) {}
