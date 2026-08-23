package io.akka.cobalt.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchedulerTest {

  @Test
  void emptyPipelineNeverStarts() {
    QueueItem item = QueueItem.waiting("a", List.of());
    assertFalse(Scheduler.canStart(item));
  }

  @Test
  void nonEmptyWaitingPipelineCanStart() {
    QueueItem item = QueueItem.waiting("a", List.of(Worker.of("w1", "fetch")));
    assertTrue(Scheduler.canStart(item));
  }

  @Test
  void independentWorkersStartTogether() {
    QueueItem item =
        new QueueItem(
            "a",
            QueueItemState.RUNNING,
            List.of(
                Worker.of("w1", "fetch"),
                Worker.of("w2", "fetch"),
                Worker.dependingOn("w3", "remux", List.of("w1", "w2"))),
            java.util.Map.of(),
            java.util.Optional.empty());

    assertEquals(List.of("w1", "w2"), Scheduler.workersToStartThisRound(item, Set.of()));
  }

  @Test
  void unmetDependencyBlocksLaterWorker() {
    QueueItem item =
        new QueueItem(
            "a",
            QueueItemState.RUNNING,
            List.of(
                Worker.of("w1", "fetch"),
                Worker.dependingOn("w2", "remux", List.of("w1")),
                Worker.of("w3", "fetch")),
            java.util.Map.of(),
            java.util.Optional.empty());

    assertEquals(List.of("w1"), Scheduler.workersToStartThisRound(item, Set.of()));
  }

  @Test
  void alreadyOngoingOrFinishedWorkersAreSkippedWithoutStoppingTheScan() {
    QueueItem item =
        new QueueItem(
            "a",
            QueueItemState.RUNNING,
            List.of(Worker.of("w1", "fetch"), Worker.of("w2", "fetch"), Worker.of("w3", "fetch")),
            java.util.Map.of("w1", "result"),
            java.util.Optional.empty());

    assertEquals(List.of("w2", "w3"), Scheduler.workersToStartThisRound(item, Set.of()));
  }

  @Test
  void lastWorkerBecomesFinalResult() {
    QueueItem item =
        new QueueItem(
            "a",
            QueueItemState.RUNNING,
            List.of(Worker.of("w1", "fetch"), Worker.of("w2", "remux")),
            java.util.Map.of("w1", "intermediate", "w2", "FILE_A"),
            java.util.Optional.empty());

    Scheduler.Outcome outcome = Scheduler.tryComplete(item);
    assertInstanceOf(Scheduler.Outcome.Done.class, outcome);
    assertEquals("FILE_A", ((Scheduler.Outcome.Done) outcome).resultRef());
  }

  @Test
  void missingFinalResultErrors() {
    QueueItem item =
        new QueueItem(
            "a",
            QueueItemState.RUNNING,
            List.of(Worker.of("w1", "fetch")),
            java.util.Map.of("w1", ""),
            java.util.Optional.empty());

    Scheduler.Outcome outcome = Scheduler.tryComplete(item);
    assertInstanceOf(Scheduler.Outcome.Error.class, outcome);
    assertEquals("no_final_file", ((Scheduler.Outcome.Error) outcome).code());
  }

  @Test
  void notYetCompleteWhileSomeWorkersMissingResults() {
    QueueItem item =
        new QueueItem(
            "a",
            QueueItemState.RUNNING,
            List.of(Worker.of("w1", "fetch"), Worker.of("w2", "remux")),
            java.util.Map.of("w1", "intermediate"),
            java.util.Optional.empty());

    assertInstanceOf(Scheduler.Outcome.NotYet.class, Scheduler.tryComplete(item));
  }
}
