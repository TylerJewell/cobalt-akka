package io.akka.cobalt.domain;

import java.util.Map;

/** R5, ported from {@code getProgress} in cobalt's {@code web/src/lib/task-manager/queue.ts}. */
public final class Progress {

  private Progress() {}

  /**
   * @param workerPercentages fractional (0-100) progress for workers with no result yet; a
   *     worker with a result already counts as 100 regardless of what's in this map.
   */
  public static double of(QueueItem item, Map<String, Double> workerPercentages) {
    if (item.state() == QueueItemState.DONE || item.state() == QueueItemState.ERROR) {
      return 1.0;
    }
    if (item.state() == QueueItemState.WAITING) {
      return 0.0;
    }

    double sum = 0.0;
    for (Worker worker : item.pipeline()) {
      if (item.results().containsKey(worker.workerId())) {
        sum += 1.0;
      } else {
        sum += workerPercentages.getOrDefault(worker.workerId(), 0.0) / 100.0;
      }
    }
    return item.pipeline().isEmpty() ? 0.0 : sum / item.pipeline().size();
  }
}
