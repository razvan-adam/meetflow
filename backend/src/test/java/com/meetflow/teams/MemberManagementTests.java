package com.meetflow.teams;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import com.meetflow.IntegrationTest;
import com.meetflow.RecordingEmailSender;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MF-11: managing a team's members, through the whole app. Ana is the admin of Ops team; Bo and Di
 * are members. Bo organizes "Bo's sync", whose first meeting gave Bo two open tasks and a done one,
 * and whose next meeting is scheduled. Cy has a team of their own.
 */
@IntegrationTest
class MemberManagementTests {

  private static final Duration PATIENCE = Duration.ofSeconds(10);

  private static Instant classStarted;

  @Autowired MockMvcTester mvc;

  @Autowired JdbcTemplate jdbc;

  @Autowired TransactionTemplate transaction;

  @Autowired RecordingEmailSender emails;

  @Autowired EntityManagerFactory entityManagerFactory;

  final ExecutorService otherThreads = Executors.newCachedThreadPool();

  /** Each user's email address, by name. */
  final Map<String, String> emailByName = new HashMap<>();

  Cookie csrfToken;

  Cookie ana;

  Cookie bo;

  Cookie cy;

  Cookie di;

  String anaId;

  String boId;

  String cyId;

  String diId;

  String teamId;

  String boSeries;

  String closedMeeting;

  String nextMeeting;

  String budgetTask;

  String hiringTask;

  String doneTask;

  @BeforeAll
  static void recordClassStart() {
    classStarted = Instant.now();
  }

  @BeforeEach
  void setUp() {
    csrfToken = csrfCookie();
    ana = signUp("Ana Costa");
    bo = signUp("Bo Lind");
    cy = signUp("Cy Park");
    di = signUp("Di Moreau");
    anaId = userId(ana);
    boId = userId(bo);
    cyId = userId(cy);
    diId = userId(di);
    teamId = idOf(post(ana, "/api/teams", "{\"name\": \"Ops team\"}"));
    join(boId);
    join(diId);
    boSeries =
        idOf(post(bo, "/api/teams/" + teamId + "/meeting-series", "{\"name\": \"Bo's sync\"}"));
    closedMeeting = scheduleMeeting("2026-10-26T09:00:00Z");
    assertThat(post(bo, "/api/meetings/" + closedMeeting + "/start", "")).hasStatusOk();
    String item =
        idOf(post(bo, "/api/meetings/" + closedMeeting + "/agenda-items", "{\"title\": \"Plan\"}"));
    budgetTask = addTask(item, "Send the budget", "2026-11-20");
    hiringTask = addTask(item, "Post the job ad", "2026-11-25");
    doneTask = addTask(item, "Book the room", "2026-11-01");
    assertThat(setStatus(bo, doneTask, "DONE")).hasStatusOk();
    assertThat(post(bo, "/api/meetings/" + closedMeeting + "/close", "{\"timeZone\": \"UTC\"}"))
        .hasStatusOk();
    nextMeeting = scheduleMeeting("2026-11-02T09:00:00Z");
  }

  @AfterEach
  void tearDown() {
    otherThreads.shutdownNow();
  }

  @Test
  void adminsMakeAMemberAnAdminAndTakeItBack() {
    assertThat(changeRole(ana, boId, "ADMIN"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            "{\"id\": \"%s\", \"name\": \"Bo Lind\", \"role\": \"ADMIN\"}".formatted(boId));
    assertThat(get(bo, "/api/teams/" + teamId))
        .bodyJson()
        .extractingPath("$.role")
        .isEqualTo("ADMIN");

    assertThat(changeRole(bo, anaId, "MEMBER")).hasStatusOk();
    assertThat(get(ana, "/api/teams/" + teamId + "/members"))
        .bodyJson()
        .extractingPath("$[*].role")
        .isEqualTo(List.of("MEMBER", "ADMIN", "MEMBER"));
  }

  @Test
  void aTeamAlwaysKeepsAnAdmin() {
    assertThat(changeRole(ana, anaId, "MEMBER")).hasStatus(HttpStatus.CONFLICT);
    assertThat(remove(ana, anaId, "{\"tasks\": [], \"series\": []}"))
        .hasStatus(HttpStatus.CONFLICT);

    // With a second admin, the first can step down.
    assertThat(changeRole(ana, diId, "ADMIN")).hasStatusOk();
    assertThat(changeRole(ana, anaId, "MEMBER")).hasStatusOk();
    assertThat(changeRole(di, diId, "MEMBER")).hasStatus(HttpStatus.CONFLICT);
  }

  @Test
  void onlyAdminsManageMembers() {
    assertThat(changeRole(bo, diId, "ADMIN")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(remove(bo, diId, "{\"tasks\": [], \"series\": []}")).hasStatus(HttpStatus.FORBIDDEN);
    assertThat(changeRole(cy, diId, "ADMIN")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(remove(cy, diId, "{\"tasks\": [], \"series\": []}")).hasStatus(HttpStatus.NOT_FOUND);
    // Someone outside the team can't be managed in it.
    assertThat(changeRole(ana, cyId, "ADMIN")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(
            send(
                mvc.put().uri("/api/teams/{t}/members/{m}/role", teamId, diId),
                "{\"role\": \"ADMIN\"}"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void listsTheTeamsOpenTasksToItsMembers() {
    assertThat(get(di, "/api/teams/" + teamId + "/open-tasks"))
        .hasStatusOk()
        .bodyJson()
        .isLenientlyEqualTo(
            """
            [{"id": "%s", "description": "Send the budget", "ownerId": "%s",
              "deadline": "2026-11-20", "status": "OPEN"},
             {"id": "%s", "description": "Post the job ad", "ownerId": "%s",
              "deadline": "2026-11-25", "status": "OPEN"}]"""
                .formatted(budgetTask, boId, hiringTask, boId));
    assertThat(get(cy, "/api/teams/" + teamId + "/open-tasks")).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void aMemberWithNoWorkIsRemovedAtOnce() {
    assertThat(remove(ana, diId, "{\"tasks\": [], \"series\": []}"))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(get(di, "/api/teams/" + teamId)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(get(di, "/api/teams")).bodyJson().isStrictlyEqualTo("[]");
    assertThat(get(ana, "/api/teams/" + teamId + "/members"))
        .bodyJson()
        .extractingPath("$[*].name")
        .isEqualTo(List.of("Ana Costa", "Bo Lind"));
  }

  @Test
  void removalWaitsForEveryOpenTaskAndSeriesToBeHandedOver() {
    // A task missing.
    assertThat(
            remove(
                ana,
                boId,
                handover(
                    List.of(reassign(budgetTask, anaId)), List.of(organizer(boSeries, anaId)))))
        .hasStatus(HttpStatus.CONFLICT);
    // The series missing.
    assertThat(
            remove(
                ana,
                boId,
                handover(List.of(reassign(budgetTask, anaId), removal(hiringTask)), List.of())))
        .hasStatus(HttpStatus.CONFLICT);
    // A done task isn't open, so it can't be handed over.
    assertThat(
            remove(
                ana,
                boId,
                handover(
                    List.of(reassign(budgetTask, anaId), removal(hiringTask), removal(doneTask)),
                    List.of(organizer(boSeries, anaId)))))
        .hasStatus(HttpStatus.CONFLICT);

    // Nothing changed.
    assertThat(get(bo, "/api/teams/" + teamId)).hasStatusOk();
    assertThat(get(ana, "/api/tasks/" + budgetTask))
        .bodyJson()
        .extractingPath("$.ownerName")
        .isEqualTo("Bo Lind");
  }

  @Test
  void workCanOnlyGoToAnotherMemberOfTheTeam() {
    var toBo =
        handover(
            List.of(reassign(budgetTask, boId), removal(hiringTask)),
            List.of(organizer(boSeries, anaId)));
    var toCy =
        handover(
            List.of(reassign(budgetTask, cyId), removal(hiringTask)),
            List.of(organizer(boSeries, anaId)));
    var seriesToCy =
        handover(
            List.of(removal(budgetTask), removal(hiringTask)), List.of(organizer(boSeries, cyId)));
    var twice =
        handover(
            List.of(reassign(budgetTask, anaId), removal(budgetTask), removal(hiringTask)),
            List.of(organizer(boSeries, anaId)));

    assertThat(remove(ana, boId, toBo)).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(remove(ana, boId, toCy)).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(remove(ana, boId, seriesToCy)).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(remove(ana, boId, twice)).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(get(bo, "/api/teams/" + teamId)).hasStatusOk();
  }

  @Test
  void removalHandsOverTheWorkAndKeepsTheRecord() {
    assertThat(
            remove(
                ana,
                boId,
                handover(
                    List.of(reassign(budgetTask, diId), removal(hiringTask)),
                    List.of(organizer(boSeries, anaId)))))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(get(ana, "/api/tasks/" + budgetTask))
        .bodyJson()
        .extractingPath("$.ownerName")
        .isEqualTo("Di Moreau");
    assertThat(get(ana, "/api/tasks/" + budgetTask + "/changes"))
        .bodyJson()
        .extractingPath("$[0].changedByName")
        .isEqualTo("Ana Costa");
    assertThat(get(ana, "/api/teams/" + teamId + "/open-tasks"))
        .bodyJson()
        .extractingPath("$[*].id")
        .isEqualTo(List.of(budgetTask));
    assertThat(get(ana, "/api/meeting-series/" + boSeries))
        .bodyJson()
        .extractingPath("$.organizerId")
        .isEqualTo(anaId);
    // The removed task leaves the next meeting; the reassigned one stays on it.
    assertThat(get(ana, "/api/meetings/" + nextMeeting + "/carried-over-tasks"))
        .bodyJson()
        .extractingPath("$[*].id")
        .isEqualTo(List.of(budgetTask));
    // Bo leaves the meeting still to come, but stays on the record of the one held.
    assertThat(get(ana, "/api/meetings/" + nextMeeting))
        .bodyJson()
        .extractingPath("$.participants[*].name")
        .isEqualTo(List.of("Ana Costa"));
    assertThat(get(ana, "/api/meetings/" + closedMeeting + "/minutes"))
        .bodyJson()
        .extractingPath("$.participants[*].name")
        .isEqualTo(List.of("Ana Costa", "Bo Lind"));
  }

  @Test
  void duringAMeetingATaskIsRemovedInThatMeeting() {
    assertThat(post(bo, "/api/meetings/" + nextMeeting + "/start", "")).hasStatusOk();

    assertThat(
            remove(
                ana,
                boId,
                handover(
                    List.of(reassign(budgetTask, anaId), removal(hiringTask)),
                    List.of(organizer(boSeries, anaId)))))
        .hasStatus(HttpStatus.NO_CONTENT);

    // The running meeting shows the removed task, as when its organizer removes one.
    assertThat(get(ana, "/api/meetings/" + nextMeeting + "/carried-over-tasks"))
        .bodyJson()
        .extractingPath("$[*].id")
        .isEqualTo(List.of(budgetTask, hiringTask));
    var latest = JsonPath.parse(body(get(ana, "/api/tasks/" + hiringTask + "/changes")));
    assertThat(latest.<String>read("$[0].changedByName")).isEqualTo("Ana Costa");
    assertThat(latest.<Boolean>read("$[0].duringMeeting")).isTrue();
    assertThat(latest.<Boolean>read("$[0].deleted")).isTrue();
  }

  @Test
  void handingOverMoreTasksOfASeriesAddsOnlyTheirOwnChanges() {
    assertThat(setStatus(bo, doneTask, "OPEN")).hasStatusOk();
    String edId = userId(signUp("Ed Novak"));
    join(teamId, edId);
    assertThat(changeOwner(bo, budgetTask, diId)).hasStatusOk();
    assertThat(changeOwner(bo, hiringTask, edId)).hasStatusOk();
    assertThat(changeOwner(bo, doneTask, edId)).hasStatusOk();

    long forOneTask =
        statementsDuring(
            () ->
                assertThat(remove(ana, diId, handover(List.of(removal(budgetTask)), List.of())))
                    .hasStatus(HttpStatus.NO_CONTENT));
    long forTwoTasks =
        statementsDuring(
            () ->
                assertThat(
                        remove(
                            ana,
                            edId,
                            handover(List.of(removal(hiringTask), removal(doneTask)), List.of())))
                    .hasStatus(HttpStatus.NO_CONTENT));

    // The second task only adds its own update and the line in its history.
    assertThat(forTwoTasks - forOneTask).isEqualTo(2);
    assertThat(get(ana, "/api/meetings/" + nextMeeting + "/carried-over-tasks"))
        .bodyJson()
        .extractingPath("$[*].id")
        .isEqualTo(List.of());
  }

  @Test
  void removalLeavesTheMembersWorkInOtherTeamsAlone() {
    String otherTeam = idOf(post(cy, "/api/teams", "{\"name\": \"Cy's team\"}"));
    join(otherTeam, boId);
    String otherSeries =
        idOf(post(bo, "/api/teams/" + otherTeam + "/meeting-series", "{\"name\": \"Other\"}"));
    String otherMeeting =
        idOf(
            post(
                bo,
                "/api/meeting-series/" + otherSeries + "/meetings",
                "{\"scheduledAt\": \"2026-11-03T09:00:00Z\", \"participantIds\": [\"%s\"]}"
                    .formatted(boId)));
    assertThat(post(bo, "/api/meetings/" + otherMeeting + "/start", "")).hasStatusOk();
    String item =
        idOf(post(bo, "/api/meetings/" + otherMeeting + "/agenda-items", "{\"title\": \"Plan\"}"));
    String otherTask = addTask(item, "Elsewhere", "2026-11-30");

    assertThat(
            remove(
                ana,
                boId,
                handover(
                    List.of(reassign(budgetTask, diId), reassign(hiringTask, diId)),
                    List.of(organizer(boSeries, anaId)))))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(get(bo, "/api/tasks/" + otherTask))
        .bodyJson()
        .extractingPath("$.ownerName")
        .isEqualTo("Bo Lind");
    assertThat(get(bo, "/api/meeting-series/" + otherSeries))
        .bodyJson()
        .extractingPath("$.organizerId")
        .isEqualTo(boId);
  }

  @Test
  void aRemovedMemberLosesAccessAtOnce() {
    assertThat(
            remove(
                ana,
                boId,
                handover(
                    List.of(removal(budgetTask), removal(hiringTask)),
                    List.of(organizer(boSeries, anaId)))))
        .hasStatus(HttpStatus.NO_CONTENT);

    // Bo's session still works, but nothing of the team is reachable with it.
    assertThat(get(bo, "/api/users/me")).hasStatusOk();
    for (String path :
        List.of(
            "/api/teams/" + teamId,
            "/api/teams/" + teamId + "/members",
            "/api/teams/" + teamId + "/meeting-series",
            "/api/teams/" + teamId + "/history",
            "/api/teams/" + teamId + "/open-tasks",
            "/api/meeting-series/" + boSeries,
            "/api/meetings/" + closedMeeting,
            "/api/meetings/" + closedMeeting + "/minutes",
            "/api/tasks/" + doneTask)) {
      assertThat(get(bo, path)).as(path).hasStatus(HttpStatus.NOT_FOUND);
    }
    assertThat(get(bo, "/api/users/me/tasks")).bodyJson().isStrictlyEqualTo("[]");
  }

  @Test
  void anAdminCanRemoveThemselvesIfAnotherAdminStays() {
    assertThat(changeRole(ana, diId, "ADMIN")).hasStatusOk();

    assertThat(remove(ana, anaId, "{\"tasks\": [], \"series\": []}"))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(get(ana, "/api/teams/" + teamId)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void aNewOrganizerJoinsTheSeriesNextMeeting() {
    assertThat(
            remove(
                ana,
                boId,
                handover(
                    List.of(removal(budgetTask), removal(hiringTask)),
                    List.of(organizer(boSeries, diId)))))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(get(ana, "/api/meetings/" + nextMeeting))
        .bodyJson()
        .extractingPath("$.participants[*].name")
        .isEqualTo(List.of("Ana Costa", "Di Moreau"));

    // Handing the series over without removing anyone does the same, and keeps the old organizer.
    assertThat(
            send(
                mvc.put().uri("/api/meeting-series/{s}/organizer", boSeries).cookie(ana),
                "{\"organizerId\": \"%s\"}".formatted(anaId)))
        .hasStatusOk();
    assertThat(get(ana, "/api/meetings/" + nextMeeting))
        .bodyJson()
        .extractingPath("$.participants[*].name")
        .isEqualTo(List.of("Ana Costa", "Di Moreau"));
  }

  @Test
  void aMemberRemovedDuringAMeetingStaysOnItsRecordButIsNotEmailedItsMinutes() {
    assertThat(post(bo, "/api/meetings/" + nextMeeting + "/start", "")).hasStatusOk();

    assertThat(
            remove(
                ana,
                boId,
                handover(
                    List.of(reassign(budgetTask, anaId), removal(hiringTask)),
                    List.of(organizer(boSeries, diId)))))
        .hasStatus(HttpStatus.NO_CONTENT);

    // Di now runs the meeting, so Di attends it and can take on a task in it.
    assertThat(get(ana, "/api/meetings/" + nextMeeting))
        .bodyJson()
        .extractingPath("$.participants[*].name")
        .isEqualTo(List.of("Ana Costa", "Bo Lind", "Di Moreau"));
    String item =
        idOf(post(di, "/api/meetings/" + nextMeeting + "/agenda-items", "{\"title\": \"Next\"}"));
    assertThat(
            post(
                di,
                "/api/agenda-items/" + item + "/tasks",
                "{\"description\": \"Book the venue\", \"ownerId\": \"%s\",".formatted(diId)
                    + " \"deadline\": \"2026-11-30\"}"))
        .hasStatus(HttpStatus.CREATED);
    assertThat(post(di, "/api/meetings/" + nextMeeting + "/close", "{\"timeZone\": \"UTC\"}"))
        .hasStatusOk();

    var minutes = JsonPath.parse(body(get(ana, "/api/meetings/" + nextMeeting + "/minutes")));
    assertThat(minutes.<List<String>>read("$.participants[*].name"))
        .containsExactly("Ana Costa", "Bo Lind", "Di Moreau");
    assertThat(minutes.<Integer>read("$.emailRecipients")).isEqualTo(2);
    await().until(() -> !emails.to(emailOf("Di Moreau"), "Minutes: ").isEmpty());
    await().until(() -> !emails.to(emailOf("Ana Costa"), "Minutes: ").isEmpty());
    // Bo got the minutes of the meeting held while still in the team, but not of this one.
    assertThat(emails.to(emailOf("Bo Lind"), "Minutes: ")).hasSize(1);
  }

  @Test
  void removingAMemberCancelsTheInvitationsTheySent() {
    assertThat(changeRole(ana, boId, "ADMIN")).hasStatusOk();
    invite(ana, "fay@example.com");
    invite(bo, "gus@example.com");

    assertThat(
            remove(
                ana,
                boId,
                handover(
                    List.of(removal(budgetTask), removal(hiringTask)),
                    List.of(organizer(boSeries, anaId)))))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(get(ana, "/api/teams/" + teamId + "/invitations"))
        .bodyJson()
        .extractingPath("$[*].email")
        .isEqualTo(List.of("fay@example.com"));
  }

  @Test
  void aTaskRemovedAsTheMeetingClosesIsNotRemovedInThatMeeting() throws Exception {
    assertThat(post(bo, "/api/meetings/" + nextMeeting + "/start", "")).hasStatusOk();
    var removing = new AtomicReference<CompletableFuture<MvcTestResult>>();

    // The meeting closes, as closing does, holding its row; the removal must wait for it to commit.
    transaction.executeWithoutResult(
        status -> {
          jdbc.queryForList("SELECT id FROM meeting WHERE id = ?::uuid FOR UPDATE", nextMeeting);
          jdbc.update(
              "UPDATE meeting SET status = 'CLOSED', closed_at = now(), closed_by = ?::uuid"
                  + " WHERE id = ?::uuid",
              boId,
              nextMeeting);
          var other =
              CompletableFuture.supplyAsync(
                  () ->
                      remove(
                          ana,
                          boId,
                          handover(
                              List.of(reassign(budgetTask, anaId), removal(hiringTask)),
                              List.of(organizer(boSeries, anaId)))),
                  otherThreads);
          removing.set(other);
          assertWaitsForALock(other);
        });

    assertThat(requireNonNull(removing.get()).get(PATIENCE.toSeconds(), TimeUnit.SECONDS))
        .hasStatus(HttpStatus.NO_CONTENT);
    var latest = JsonPath.parse(body(get(ana, "/api/tasks/" + hiringTask + "/changes")));
    assertThat(latest.<Boolean>read("$[0].deleted")).isTrue();
    assertThat(latest.<Boolean>read("$[0].duringMeeting")).isFalse();
  }

  @Test
  void aMemberRemovedDuringAMeetingCantBeGivenANewTaskInIt() {
    assertThat(post(bo, "/api/meetings/" + nextMeeting + "/start", "")).hasStatusOk();
    assertThat(
            remove(
                ana,
                boId,
                handover(
                    List.of(reassign(budgetTask, anaId), removal(hiringTask)),
                    List.of(organizer(boSeries, anaId)))))
        .hasStatus(HttpStatus.NO_CONTENT);
    String item =
        idOf(post(ana, "/api/meetings/" + nextMeeting + "/agenda-items", "{\"title\": \"Next\"}"));

    // Bo is still on the meeting's record, but is no longer in the team.
    assertThat(post(ana, "/api/agenda-items/" + item + "/tasks", newTask(boId)))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(post(ana, "/api/agenda-items/" + item + "/tasks", newTask(anaId)))
        .hasStatus(HttpStatus.CREATED);
  }

  @Test
  void aRemovedMembersDoneTaskIsReassignedBeforeItCanBeReopened() {
    assertThat(
            remove(
                ana,
                boId,
                handover(
                    List.of(removal(budgetTask), removal(hiringTask)),
                    List.of(organizer(boSeries, anaId)))))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(setStatus(ana, doneTask, "OPEN"))
        .hasStatus(HttpStatus.CONFLICT)
        .bodyJson()
        .extractingPath("$.detail")
        .isEqualTo("This task's owner is no longer in the team. Reassign it first.");
    assertThat(changeOwner(ana, doneTask, diId)).hasStatusOk();
    assertThat(setStatus(ana, doneTask, "OPEN")).hasStatusOk();
  }

  @Test
  void aTaskGivenToAMemberBeingRemovedWaitsAndIsRefused() throws Exception {
    assertThat(whileDiIsBeingRemoved(() -> changeOwner(bo, budgetTask, diId)))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(get(ana, "/api/tasks/" + budgetTask))
        .bodyJson()
        .extractingPath("$.ownerName")
        .isEqualTo("Bo Lind");
  }

  @Test
  void aSeriesGivenToAMemberBeingRemovedWaitsAndIsRefused() throws Exception {
    assertThat(
            whileDiIsBeingRemoved(
                () ->
                    send(
                        mvc.put().uri("/api/meeting-series/{s}/organizer", boSeries).cookie(ana),
                        "{\"organizerId\": \"%s\"}".formatted(diId))))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void aMemberBeingRemovedCantBeAddedToAMeetingMeanwhile() throws Exception {
    assertThat(
            whileDiIsBeingRemoved(
                () ->
                    send(
                        mvc.put().uri("/api/meetings/{m}", nextMeeting).cookie(bo),
                        "{\"scheduledAt\": \"2026-11-02T09:00:00Z\","
                            + " \"participantIds\": [\"%s\", \"%s\"]}".formatted(anaId, diId))))
        .hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(get(ana, "/api/meetings/" + nextMeeting))
        .bodyJson()
        .extractingPath("$.participants[*].name")
        .isEqualTo(List.of("Ana Costa", "Bo Lind"));
  }

  @Test
  void aMemberBeingRemovedCantCreateASeriesMeanwhile() throws Exception {
    assertThat(
            whileDiIsBeingRemoved(
                () -> post(di, "/api/teams/" + teamId + "/meeting-series", "{\"name\": \"Di's\"}")))
        .hasStatus(HttpStatus.NOT_FOUND);
    assertThat(get(ana, "/api/teams/" + teamId + "/meeting-series"))
        .bodyJson()
        .extractingPath("$[*].name")
        .isEqualTo(List.of("Bo's sync"));
  }

  /**
   * Runs {@code change} on another thread while Di's removal holds the team, as a removal does, and
   * has taken Di's membership, but hasn't committed. The change must wait for it, then see Di gone.
   */
  private MvcTestResult whileDiIsBeingRemoved(Supplier<MvcTestResult> change) throws Exception {
    var started = new AtomicReference<CompletableFuture<MvcTestResult>>();
    transaction.executeWithoutResult(
        status -> {
          jdbc.queryForList("SELECT id FROM team WHERE id = ?::uuid FOR UPDATE", teamId);
          jdbc.update(
              "DELETE FROM team_membership WHERE team_id = ?::uuid AND user_id = ?::uuid",
              teamId,
              diId);
          var other = CompletableFuture.supplyAsync(change, otherThreads);
          started.set(other);
          assertWaitsForALock(other);
        });
    return requireNonNull(started.get()).get(PATIENCE.toSeconds(), TimeUnit.SECONDS);
  }

  /**
   * How many statements Hibernate sends during the action, once the emails of this class' closed
   * meetings, which go out in the background, can't be counted with them. Other classes' are left
   * out: those that make delivery fail on purpose leave theirs unfinished.
   */
  private long statementsDuring(Runnable action) {
    await()
        .atMost(PATIENCE)
        .until(
            () ->
                Long.valueOf(0)
                    .equals(
                        jdbc.queryForObject(
                            "SELECT count(*) FROM event_publication"
                                + " WHERE completion_date IS NULL AND publication_date >= ?",
                            Long.class,
                            Timestamp.from(classStarted))));
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    statistics.setStatisticsEnabled(true);
    try {
      action.run();
      return statistics.getPrepareStatementCount();
    } finally {
      statistics.setStatisticsEnabled(false);
    }
  }

  private static String newTask(String ownerId) {
    return "{\"description\": \"Book the venue\", \"ownerId\": \"%s\", \"deadline\": \"2026-11-30\"}"
        .formatted(ownerId);
  }

  private MvcTestResult changeOwner(Cookie session, String taskId, String ownerId) {
    return send(
        mvc.put().uri("/api/tasks/{id}/owner", taskId).cookie(session),
        "{\"ownerId\": \"" + ownerId + "\"}");
  }

  /** pg_locks is live, unlike pg_stat_activity, which is frozen for the rest of a transaction. */
  private void assertWaitsForALock(CompletableFuture<?> other) {
    var deadline = Instant.now().plus(PATIENCE);
    while (Instant.now().isBefore(deadline)) {
      assertThat(other).as("it went ahead without waiting for the lock").isNotDone();
      Long waiting =
          jdbc.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Long.class);
      if (requireNonNull(waiting) > 0) {
        return;
      }
    }
    throw new AssertionError("it neither finished nor waited for a lock");
  }

  private void invite(Cookie session, String email) {
    assertThat(
            post(
                session, "/api/teams/" + teamId + "/invitations", "{\"email\": \"" + email + "\"}"))
        .hasStatus(HttpStatus.CREATED);
  }

  private String emailOf(String name) {
    return requireNonNull(emailByName.get(name), name);
  }

  private static String reassign(String taskId, String newOwnerId) {
    return "{\"taskId\": \"%s\", \"newOwnerId\": \"%s\"}".formatted(taskId, newOwnerId);
  }

  private static String removal(String taskId) {
    return "{\"taskId\": \"%s\", \"newOwnerId\": null}".formatted(taskId);
  }

  private static String organizer(String seriesId, String organizerId) {
    return "{\"seriesId\": \"%s\", \"organizerId\": \"%s\"}".formatted(seriesId, organizerId);
  }

  private static String handover(List<String> tasks, List<String> series) {
    return "{\"tasks\": [%s], \"series\": [%s]}"
        .formatted(String.join(", ", tasks), String.join(", ", series));
  }

  private MvcTestResult changeRole(Cookie session, String memberId, String role) {
    return send(
        mvc.put().uri("/api/teams/{t}/members/{m}/role", teamId, memberId).cookie(session),
        "{\"role\": \"" + role + "\"}");
  }

  private MvcTestResult remove(Cookie session, String memberId, String json) {
    return send(
        mvc.post().uri("/api/teams/{t}/members/{m}/removal", teamId, memberId).cookie(session),
        json);
  }

  private MvcTestResult setStatus(Cookie session, String taskId, String status) {
    return send(
        mvc.put().uri("/api/tasks/{id}/status", taskId).cookie(session),
        "{\"status\": \"" + status + "\"}");
  }

  private String scheduleMeeting(String scheduledAt) {
    return idOf(
        post(
            bo,
            "/api/meeting-series/" + boSeries + "/meetings",
            "{\"scheduledAt\": \"%s\", \"participantIds\": [\"%s\", \"%s\"]}"
                .formatted(scheduledAt, anaId, boId)));
  }

  private String addTask(String itemId, String description, String deadline) {
    return idOf(
        post(
            bo,
            "/api/agenda-items/" + itemId + "/tasks",
            "{\"description\": \"%s\", \"ownerId\": \"%s\", \"deadline\": \"%s\"}"
                .formatted(description, boId, deadline)));
  }

  private void join(String userId) {
    join(teamId, userId);
  }

  private void join(String team, String userId) {
    jdbc.update(
        "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
            + " VALUES (?::uuid, ?::uuid, 'MEMBER', now())",
        team,
        userId);
  }

  private MvcTestResult get(Cookie session, String path) {
    return mvc.get().uri(path).cookie(session).exchange();
  }

  private MvcTestResult post(Cookie session, String path, String json) {
    return send(mvc.post().uri(path).cookie(session), json);
  }

  // The way the React app sends changes: JSON body, CSRF cookie echoed in the header.
  private MvcTestResult send(MockMvcRequestBuilder request, String json) {
    return request
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .cookie(csrfToken)
        .header("X-XSRF-TOKEN", csrfToken.getValue())
        .exchange();
  }

  private static String idOf(MvcTestResult result) {
    return JsonPath.read(body(result), "$.id");
  }

  /** Signs up a new user and returns their session cookie. */
  private Cookie signUp(String name) {
    var email = "user." + UUID.randomUUID() + "@example.com";
    emailByName.put(name, email);
    var json =
        """
        {"name": "%s", "email": "%s", "password": "correct horse battery"}"""
            .formatted(name, email);
    var response = send(mvc.post().uri("/api/users"), json).getResponse();
    return requireNonNull(response.getCookie("SESSION"), "sign-up failed");
  }

  private String userId(Cookie session) {
    return JsonPath.read(body(get(session, "/api/users/me")), "$.id");
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private Cookie csrfCookie() {
    var response = mvc.get().uri("/api/health").exchange().getResponse();
    return requireNonNull(response.getCookie("XSRF-TOKEN"), "no XSRF-TOKEN cookie");
  }
}
