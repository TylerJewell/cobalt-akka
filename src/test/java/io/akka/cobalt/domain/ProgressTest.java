package io.akka.cobalt.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProgressTest {

  @Test
  void doneItemReportsFullProgressRegardlessOfPipeline() {
    QueueItem item =
        new QueueItem(
            "a", QueueItemState.DONE, List.of(Worker.of("w1", "fetch")), Map.of(), Optional.empty());
    assertEquals(1.0, Progress.of(item, Map.of()));
  }

  @Test
  void errorItemReportsFullProgress() {
    QueueItem item =
        new QueueItem(
            "a", QueueItemState.ERROR, List.of(Worker.of("w1", "fetch")), Map.of(), Optional.of("x"));
    assertEquals(1.0, Progress.of(item, Map.of()));
  }

  @Test
  void waitingItemReportsZeroProgress() {
    QueueItem item = QueueItem.waiting("a", List.of(Worker.of("w1", "fetch")));
    assertEquals(0.0, Progress.of(item, Map.of()));
  }

  @Test
  void runningItemAveragesCompletedAndInFlightWorkers() {
    QueueItem item =
        new QueueItem(
            "a",
            QueueItemState.RUNNING,
            List.of(Worker.of("w1", "fetch"), Worker.of("w2", "remux")),
            Map.of("w1", "done-file"),
            Optional.empty());

    assertEquals(0.75, Progress.of(item, Map.of("w2", 50.0)), 1e-9);
  }
}
