package io.akka.cobalt.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Pure port of {@code schedule()} in cobalt's {@code web/src/lib/task-manager/scheduler.ts}.
 * SPEC-001 rules R1, R3, R4.
 *
 * <p>Cross-item single-flight (R2) is not this class's job — see {@link RunSlot}.
 */
public final class Scheduler {

  private Scheduler() {}

  /** R1: a waiting item with an empty pipeline never starts. */
  public static boolean canStart(QueueItem item) {
    return item.state() == QueueItemState.WAITING && !item.pipeline().isEmpty();
  }

  /**
   * R3: workers considered in pipeline order; a worker already finished or already started is
   * skipped without stopping the scan, but a worker with an unmet dependency stops the scan for
   * this round — later, independent workers do not start in the same round.
   */
  public static List<String> workersToStartThisRound(QueueItem item, Set<String> ongoingWorkerIds) {
    List<String> toStart = new ArrayList<>();
    for (Worker worker : item.pipeline()) {
      if (item.results().containsKey(worker.workerId()) || ongoingWorkerIds.contains(worker.workerId())) {
        continue;
      }
      boolean needsToWait = worker.dependsOn().stream().anyMatch(id -> !item.results().containsKey(id));
      if (needsToWait) {
        break;
      }
      toStart.add(worker.workerId());
    }
    return toStart;
  }

  public sealed interface Outcome permits Outcome.Done, Outcome.Error, Outcome.NotYet {
    record Done(String resultRef) implements Outcome {}

    record Error(String code) implements Outcome {}

    record NotYet() implements Outcome {}
  }

  /**
   * R4: once every worker has a result, the item finishes using the LAST pipeline worker's
   * result; a present-but-empty final result errors with {@code no_final_file} instead.
   */
  public static Outcome tryComplete(QueueItem item) {
    if (item.pipeline().isEmpty() || item.results().size() != item.pipeline().size()) {
      return new Outcome.NotYet();
    }
    String finalWorkerId = item.pipeline().get(item.pipeline().size() - 1).workerId();
    String finalResult = item.results().get(finalWorkerId);
    if (finalResult != null && !finalResult.isEmpty()) {
      return new Outcome.Done(finalResult);
    }
    return new Outcome.Error("no_final_file");
  }
}
