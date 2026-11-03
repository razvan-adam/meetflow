package com.meetflow.tasks;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.meetflow.IntegrationTest;
import com.meetflow.meetings.exception.OpenMeetingExistsException;
import com.meetflow.meetings.service.AgendaService;
import com.meetflow.meetings.service.MeetingSeriesService;
import com.meetflow.meetings.service.MeetingService;
import com.meetflow.tasks.domain.TaskStatus;
import com.meetflow.tasks.dto.TaskResponse;
import com.meetflow.tasks.service.TaskService;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MF-19: scheduling a meeting while a task enters or leaves "done", in real parallel transactions.
 * Without the series lock, each change misses the other's uncommitted work and the agenda ends up
 * wrong. Most tests force the bad timing: they keep the first change uncommitted, start the second
 * on another thread, and check in PostgreSQL that it is waiting for a lock. The rest fire both at
 * once. They call the services directly, because that is how a test can hold a transaction open.
 */
@IntegrationTest
class CarryOverConcurrencyTests {

  private static final Duration PATIENCE = Duration.ofSeconds(10);

  @Autowired JdbcTemplate jdbc;

  @Autowired TransactionTemplate transaction;

  @Autowired MeetingService meetings;

  @Autowired MeetingSeriesService series;

  @Autowired AgendaService agenda;

  @Autowired TaskService tasks;

  @Autowired EntityManager entityManager;

  final ExecutorService otherThreads = Executors.newCachedThreadPool();

  UUID ana;

  UUID seriesId;

  UUID firstMeeting;

  UUID item;

  UUID task;

  @BeforeEach
  void setUp() {
    ana = UUID.randomUUID();
    UUID teamId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO app_user (id, email, name, password_hash, created_at)"
            + " VALUES (?, ?, 'Ana', 'hash', now())",
        ana,
        ana + "@example.com");
    jdbc.update(
        "INSERT INTO team (id, name, created_by, created_at) VALUES (?, 'Ops', ?, now())",
        teamId,
        ana);
    jdbc.update(
        "INSERT INTO team_membership (team_id, user_id, role, joined_at)"
            + " VALUES (?, ?, 'ADMIN', now())",
        teamId,
        ana);
    seriesId = series.create(ana, teamId, "Weekly ops sync").id();
    firstMeeting = schedule();
    meetings.start(ana, firstMeeting);
    item = agenda.add(ana, firstMeeting, "Budget").id();
    task = addTask(item);
  }

  @AfterEach
  void tearDown() {
    otherThreads.shutdownNow();
  }

  @Test
  void aTaskReopenedWhileAMeetingIsBeingScheduledJoinsIt() throws Exception {
    setStatus(TaskStatus.DONE);
    meetings.close(ana, firstMeeting, null);
    var next = new AtomicReference<UUID>();

    secondWaitsForFirst(() -> next.set(schedule()), () -> setStatus(TaskStatus.OPEN));

    assertThat(carriedOver(next)).containsExactly(task);
  }

  @Test
  void aMeetingScheduledWhileATaskIsBeingReopenedIncludesIt() throws Exception {
    setStatus(TaskStatus.DONE);
    meetings.close(ana, firstMeeting, null);

    UUID next = secondWaitsForFirst(() -> setStatus(TaskStatus.OPEN), this::schedule);

    assertThat(carriedOver(next)).containsExactly(task);
  }

  @Test
  void aTaskDoneWhileAMeetingIsBeingScheduledLeavesIt() throws Exception {
    meetings.close(ana, firstMeeting, null);
    var next = new AtomicReference<UUID>();

    secondWaitsForFirst(() -> next.set(schedule()), () -> setStatus(TaskStatus.DONE));

    assertThat(carriedOver(next)).isEmpty();
  }

  @Test
  void aMeetingScheduledWhileATaskIsBeingMarkedDoneLeavesItOut() throws Exception {
    meetings.close(ana, firstMeeting, null);

    UUID next = secondWaitsForFirst(() -> setStatus(TaskStatus.DONE), this::schedule);

    assertThat(carriedOver(next)).isEmpty();
  }

  @Test
  void aReopenedTaskIsNeverLostWhenBothHappenAtOnce() throws Exception {
    UUID running = firstMeeting;
    for (int round = 0; round < 20; round++) {
      setStatus(TaskStatus.DONE);
      meetings.close(ana, running, null);
      var barrier = new CyclicBarrier(2);
      var scheduling = together(barrier, this::schedule);
      var reopening = together(barrier, () -> setStatus(TaskStatus.OPEN));

      UUID next = scheduling.get(PATIENCE.toSeconds(), TimeUnit.SECONDS);
      reopening.get(PATIENCE.toSeconds(), TimeUnit.SECONDS);

      assertThat(carriedOver(next)).as("round %d", round).containsExactly(task);
      meetings.start(ana, next);
      running = next;
    }
  }

  @Test
  void twoMeetingsScheduledAtOnceGiveOneMeetingWithOneCopyOfEverything() throws Exception {
    meetings.close(ana, firstMeeting, null);
    var barrier = new CyclicBarrier(2);
    var first = together(barrier, this::scheduleOrEmpty);
    var second = together(barrier, this::scheduleOrEmpty);

    var scheduled =
        Stream.of(
                first.get(PATIENCE.toSeconds(), TimeUnit.SECONDS),
                second.get(PATIENCE.toSeconds(), TimeUnit.SECONDS))
            .flatMap(Optional::stream)
            .toList();

    assertThat(scheduled).hasSize(1);
    UUID next = scheduled.getFirst();
    assertThat(carriedOver(next)).containsExactly(task);
    assertThat(agenda.list(ana, next)).hasSize(1);
  }

  @Test
  void aLockedSeriesDoesNotHoldUpCreatingATask() throws Exception {
    var created = new AtomicReference<CompletableFuture<UUID>>();

    transaction.executeWithoutResult(
        status -> {
          series.lock(seriesId);
          var creating = CompletableFuture.supplyAsync(() -> addTask(item), otherThreads);
          created.set(creating);
          // Finishes while the lock is held: a task's reference to its series must not wait for it.
          assertThat(creating).succeedsWithin(PATIENCE);
        });

    assertThat(requireNonNull(created.get())).isCompleted();
  }

  @Test
  void reopeningDoesNotDeadlockWithATaskBeingCreatedInTheRunningMeeting() throws Exception {
    setStatus(TaskStatus.DONE);
    meetings.close(ana, firstMeeting, null);
    UUID next = schedule();
    meetings.start(ana, next);
    UUID nextItem = agenda.list(ana, next).getFirst().id();
    var reopened = new AtomicReference<CompletableFuture<Void>>();

    // Creating the task holds the meeting and refers to the series; reopening holds the series and
    // refers to the meeting. If either reference had to wait, they would wait for each other.
    transaction.executeWithoutResult(
        status -> {
          addTask(nextItem);
          entityManager.flush();
          var reopening =
              CompletableFuture.runAsync(() -> setStatus(TaskStatus.OPEN), otherThreads);
          reopened.set(reopening);
          assertThat(reopening).succeedsWithin(PATIENCE);
        });

    assertThat(requireNonNull(reopened.get())).isCompleted();
    assertThat(carriedOver(next)).containsExactly(task);
  }

  /**
   * Runs {@code first} in a transaction and, before it commits, {@code second} on another thread,
   * which must wait for it. Returns what {@code second} returned.
   */
  private <T> T secondWaitsForFirst(Runnable first, Supplier<T> second) throws Exception {
    var started = new AtomicReference<CompletableFuture<T>>();
    transaction.executeWithoutResult(
        status -> {
          first.run();
          var other = CompletableFuture.supplyAsync(second, otherThreads);
          started.set(other);
          assertWaitsForALock(other);
        });
    return requireNonNull(started.get()).get(PATIENCE.toSeconds(), TimeUnit.SECONDS);
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

  private <T> CompletableFuture<T> together(CyclicBarrier barrier, Supplier<T> action) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            barrier.await(PATIENCE.toSeconds(), TimeUnit.SECONDS);
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
          return action.get();
        },
        otherThreads);
  }

  private UUID schedule() {
    return meetings.schedule(ana, seriesId, Instant.parse("2026-11-09T08:30:00Z"), List.of()).id();
  }

  /** The meeting's ID, or an empty answer if another one got there first. */
  private Optional<UUID> scheduleOrEmpty() {
    try {
      return Optional.of(schedule());
    } catch (OpenMeetingExistsException e) {
      return Optional.empty();
    }
  }

  private UUID addTask(UUID itemId) {
    return tasks
        .createFromAgendaItem(ana, itemId, "Sign the contract", ana, LocalDate.of(2026, 11, 20))
        .id();
  }

  private TaskResponse setStatus(TaskStatus status) {
    return tasks.changeStatus(ana, task, status);
  }

  private List<UUID> carriedOver(AtomicReference<UUID> meeting) {
    return carriedOver(requireNonNull(meeting.get()));
  }

  private List<UUID> carriedOver(UUID meeting) {
    return tasks.listCarriedOver(ana, meeting).stream().map(TaskResponse::id).toList();
  }
}
