package com.meetflow.teams;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A member is about to be removed from a team (MF-11), published in the removing transaction before
 * the membership goes. The modules that hold the member's work listen synchronously: each applies
 * its part of the handover and throws if it doesn't cover exactly what the member still has, which
 * rolls the removal back.
 *
 * @param removedBy the admin removing the member
 * @param taskOwners each open task to reassign, with its new owner
 * @param tasksToRemove each open task to remove instead
 * @param seriesOrganizers each series the member organizes, with its new organizer
 */
public record MemberRemovalEvent(
    UUID teamId,
    UUID memberId,
    UUID removedBy,
    Map<UUID, UUID> taskOwners,
    Set<UUID> tasksToRemove,
    Map<UUID, UUID> seriesOrganizers) {}
