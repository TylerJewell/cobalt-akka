package io.akka.cobalt.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One download item and its pipeline. SPEC-001 §2.
 *
 * <p>{@code results} distinguishes "no result yet" (absent key) from "the worker finished but
 * produced no usable output" (key present, value ""), because R4 treats those two cases
 * differently: the first means the worker hasn't run, the second is a terminal error.
 */
public record QueueItem(
    String id, QueueItemState state, List<Worker> pipeline, Map<String, String> results, Optional<String> errorCode) {

  public static QueueItem waiting(String id, List<Worker> pipeline) {
    return new QueueItem(id, QueueItemState.WAITING, pipeline, Map.of(), Optional.empty());
  }

  /** Entity-not-yet-created placeholder; distinct from a real WAITING item with no pipeline. */
  public static QueueItem empty(String id) {
    return new QueueItem(id, QueueItemState.WAITING, List.of(), Map.of(), Optional.empty());
  }

  public boolean exists() {
    return !pipeline.isEmpty() || state != QueueItemState.WAITING || !results.isEmpty();
  }

  public QueueItem apply(QueueEvent event) {
    return switch (event) {
      case QueueEvent.ItemAdded e -> QueueItem.waiting(id, e.pipeline());
      case QueueEvent.ItemStartedRunning ignored -> asRunning();
      case QueueEvent.WorkerResultRecorded e -> withResult(e.workerId(), e.resultRef());
      case QueueEvent.ItemCompleted ignored -> asDone();
      case QueueEvent.ItemFailed e -> asError(e.errorCode());
    };
  }

  public QueueItem withResult(String workerId, String resultRef) {
    var next = new java.util.HashMap<>(results);
    next.put(workerId, resultRef == null ? "" : resultRef);
    return new QueueItem(id, QueueItemState.RUNNING, pipeline, Map.copyOf(next), Optional.empty());
  }

  public QueueItem asRunning() {
    return new QueueItem(id, QueueItemState.RUNNING, pipeline, results, Optional.empty());
  }

  public QueueItem asDone() {
    return new QueueItem(id, QueueItemState.DONE, pipeline, results, Optional.empty());
  }

  public QueueItem asError(String code) {
    return new QueueItem(id, QueueItemState.ERROR, pipeline, results, Optional.of(code));
  }
}
