package com.meetflow.teams.service;

import com.meetflow.identity.UserDirectory;
import com.meetflow.notifications.InvitationEmail;
import com.meetflow.notifications.InvitationMailer;
import com.meetflow.teams.domain.Invitation;
import com.meetflow.teams.domain.Role;
import com.meetflow.teams.domain.TeamMembership;
import com.meetflow.teams.dto.InvitationLinkResponse;
import com.meetflow.teams.dto.InvitationResponse;
import com.meetflow.teams.dto.TeamResponse;
import com.meetflow.teams.exception.AlreadyTeamMemberException;
import com.meetflow.teams.exception.InvitationForAnotherEmailException;
import com.meetflow.teams.exception.InvitationNotFoundException;
import com.meetflow.teams.exception.InvitationUnusableException;
import com.meetflow.teams.exception.NotTeamAdminException;
import com.meetflow.teams.repository.InvitationRepository;
import com.meetflow.teams.repository.TeamMembershipRepository;
import com.meetflow.teams.repository.TeamRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inviting colleagues to a team by email, and joining from the link (MF-10). Links are single-use,
 * expire after 7 days, and only work for the invited address.
 */
@Service
public class InvitationService {

  private static final SecureRandom RANDOM = new SecureRandom();

  private final InvitationRepository invitations;
  private final TeamRepository teams;
  private final TeamMembershipRepository memberships;
  private final TeamAccess access;
  private final UserDirectory users;
  private final InvitationMailer mailer;

  InvitationService(
      InvitationRepository invitations,
      TeamRepository teams,
      TeamMembershipRepository memberships,
      TeamAccess access,
      UserDirectory users,
      InvitationMailer mailer) {
    this.invitations = invitations;
    this.teams = teams;
    this.memberships = memberships;
    this.access = access;
    this.users = users;
    this.mailer = mailer;
  }

  /**
   * Invites the email to the team and sends the link, replacing an earlier invitation to the same
   * address that wasn't used. If the email can't be sent, nothing is saved.
   */
  @Transactional
  public InvitationResponse invite(UUID adminId, UUID teamId, String email) {
    access.requireAdmin(adminId, teamId);
    var address = UserDirectory.normalizeEmail(email);
    var existingUser = users.findIdByEmail(address);
    if (existingUser.isPresent() && access.membership(existingUser.get(), teamId).isPresent()) {
      throw new AlreadyTeamMemberException();
    }
    invitations.deleteUnaccepted(teamId, address);

    var token = newToken();
    var invitation = invitations.save(new Invitation(teamId, address, hash(token), adminId));
    var team = teams.findById(teamId).orElseThrow();
    var inviterName = nameOf(adminId);
    mailer.send(
        new InvitationEmail(invitation.getId(), address, team.getName(), inviterName, token));
    return new InvitationResponse(
        invitation.getId(),
        address,
        inviterName,
        invitation.getCreatedAt(),
        invitation.getExpiresAt());
  }

  /** The team's invitations that can still be used, newest first. Admins only. */
  @Transactional(readOnly = true)
  public List<InvitationResponse> listPending(UUID adminId, UUID teamId) {
    access.requireAdmin(adminId, teamId);
    var pending = invitations.findPending(teamId, Instant.now());
    var names = users.namesOf(pending.stream().map(Invitation::getInvitedBy).toList());
    return pending.stream()
        .map(
            invitation ->
                new InvitationResponse(
                    invitation.getId(),
                    invitation.getEmail(),
                    names.getOrDefault(invitation.getInvitedBy(), ""),
                    invitation.getCreatedAt(),
                    invitation.getExpiresAt()))
        .toList();
  }

  /** Cancels an invitation that wasn't used yet, so its link stops working. Admins only. */
  @Transactional
  public void cancel(UUID adminId, UUID invitationId) {
    var invitation =
        invitations
            .findById(invitationId)
            .filter(found -> found.status(Instant.now()) != Invitation.Status.ACCEPTED)
            .orElseThrow(InvitationNotFoundException::new);
    var membership =
        access
            .membership(adminId, invitation.getTeamId())
            .orElseThrow(InvitationNotFoundException::new);
    if (membership.getRole() != Role.ADMIN) {
      throw new NotTeamAdminException();
    }
    invitations.delete(invitation);
  }

  /** What the link's holder sees before joining. Needs no login. */
  @Transactional(readOnly = true)
  public InvitationLinkResponse lookUp(String token) {
    var invitation =
        invitations.findByTokenHash(hash(token)).orElseThrow(InvitationNotFoundException::new);
    var team = teams.findById(invitation.getTeamId()).orElseThrow();
    return new InvitationLinkResponse(
        team.getName(),
        nameOf(invitation.getInvitedBy()),
        invitation.getEmail(),
        invitation.status(Instant.now()));
  }

  /**
   * Joins the team as a member with the link, which can't be used again.
   *
   * @param userEmail the logged-in user's email, which must be the invited address
   */
  @Transactional
  public TeamResponse accept(UUID userId, String userEmail, String token) {
    var invitation =
        invitations
            .findByTokenHashForUpdate(hash(token))
            .orElseThrow(InvitationNotFoundException::new);
    var status = invitation.status(Instant.now());
    if (status != Invitation.Status.PENDING) {
      throw new InvitationUnusableException(status);
    }
    if (!invitation.getEmail().equals(UserDirectory.normalizeEmail(userEmail))) {
      throw new InvitationForAnotherEmailException();
    }
    invitation.accept(userId);
    var teamId = invitation.getTeamId();
    var role =
        access
            .membership(userId, teamId)
            .map(TeamMembership::getRole)
            .orElseGet(
                () -> memberships.save(new TeamMembership(teamId, userId, Role.MEMBER)).getRole());
    var team = teams.findById(teamId).orElseThrow();
    return new TeamResponse(teamId, team.getName(), role);
  }

  private String nameOf(UUID userId) {
    return users.namesOf(List.of(userId)).getOrDefault(userId, "");
  }

  /** 256 random bits, URL-safe. Only its hash is stored. */
  private static String newToken() {
    var bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** SHA-256 is enough: the token is random, so it can't be guessed from a list of passwords. */
  private static String hash(String token) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every Java runtime has SHA-256", e);
    }
  }
}
