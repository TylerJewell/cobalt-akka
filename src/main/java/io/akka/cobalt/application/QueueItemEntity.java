package io.akka.cobalt.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.akka.cobalt.domain.QueueEvent;
import io.akka.cobalt.domain.QueueItem;
import io.akka.cobalt.domain.Scheduler;
import io.akka.cobalt.domain.Worker;
import java.util.List;
import java.util.Set;

/**
 * One download item — SPEC-001. Entity id is the item id. All decision logic lives in {@code
 * io.akka.cobalt.domain} and is tested without a runtime; this class decides only what to
 * persist and re-runs the scheduler after every state change, the way the source calls {@code
 * schedule()} after every queue mutation.
 */
@Component(id = "queue-item")
public class QueueItemEntity extends EventSourcedEntity<QueueItem, QueueEvent> {

  private final String entityId;

  public QueueItemEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public QueueItem emptyState() {
    return QueueItem.empty(entityId);
  }

  public record AddItem(List<Worker> pipeline) {}

  public Effect<List<String>> addItem(AddItem cmd) {
    if (currentState().exists()) {
      return effects().error("item '" + commandContext().entityId() + "' already exists");
    }
    var added = currentState().apply(new QueueEvent.ItemAdded(cmd.pipeline()));
    return persistAndSchedule(new QueueEvent.ItemAdded(cmd.pipeline()), added);
  }

  public record RecordWorkerResult(String workerId, String resultRef) {}

  public Effect<List<String>> recordWorkerResult(RecordWorkerResult cmd) {
    var event = new QueueEvent.WorkerResultRecorded(cmd.workerId(), cmd.resultRef());
    var next = currentState().apply(event);
    return persistAndSchedule(event, next);
  }

  /** Starts every worker the scheduler says is ready this round, or resolves the item if done. */
  private Effect<List<String>> persistAndSchedule(QueueEvent triggeringEvent, QueueItem stateAfterEvent) {
    List<QueueEvent> toPersist = new java.util.ArrayList<>();
    toPersist.add(triggeringEvent);
    QueueItem state = stateAfterEvent;

    if (state.state() == io.akka.cobalt.domain.QueueItemState.WAITING && Scheduler.canStart(state)) {
      toPersist.add(new QueueEvent.ItemStartedRunning());
      state = state.apply(new QueueEvent.ItemStartedRunning());
    }

    List<String> workersToStart = List.of();
    if (state.state() == io.akka.cobalt.domain.QueueItemState.RUNNING) {
      var outcome = Scheduler.tryComplete(state);
      if (outcome instanceof Scheduler.Outcome.Done) {
        toPersist.add(new QueueEvent.ItemCompleted());
      } else if (outcome instanceof Scheduler.Outcome.Error error) {
        toPersist.add(new QueueEvent.ItemFailed(error.code()));
      } else {
        workersToStart = Scheduler.workersToStartThisRound(state, Set.of());
      }
    }

    final List<String> reply = workersToStart;
    return effects().persistAll(toPersist).thenReply(s -> reply);
  }

  public ReadOnlyEffect<QueueItem> get() {
    return effects().reply(currentState());
  }

  @Override
  public QueueItem applyEvent(QueueEvent event) {
    return currentState().apply(event);
  }
}
