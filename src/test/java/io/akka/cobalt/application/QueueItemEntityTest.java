package io.akka.cobalt.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.akka.cobalt.domain.QueueEvent;
import io.akka.cobalt.domain.QueueItem;
import io.akka.cobalt.domain.QueueItemState;
import io.akka.cobalt.domain.Worker;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What {@code SchedulerTest} cannot check: that the entity actually persists and reacts through
 * {@code ComponentClient}-shaped commands, not just the pure domain layer.
 */
public class QueueItemEntityTest {

  private EventSourcedTestKit<QueueItem, QueueEvent, QueueItemEntity> item() {
    return EventSourcedTestKit.of("item-1", QueueItemEntity::new);
  }

  @Test
  public void addingAnItemWithASingleWorkerStartsThatWorker() {
    var kit = item();
    var result =
        kit.method(QueueItemEntity::addItem).invoke(new QueueItemEntity.AddItem(List.of(Worker.of("w1", "fetch"))));

    assertThat(result.getReply()).containsExactly("w1");
    assertThat(kit.getState().state()).isEqualTo(QueueItemState.RUNNING);
  }

  @Test
  public void addingAnItemTwiceIsRefused() {
    var kit = item();
    kit.method(QueueItemEntity::addItem).invoke(new QueueItemEntity.AddItem(List.of(Worker.of("w1", "fetch"))));

    var result =
        kit.method(QueueItemEntity::addItem).invoke(new QueueItemEntity.AddItem(List.of(Worker.of("w2", "fetch"))));
    assertThat(result.isError()).isTrue();
  }

  @Test
  public void recordingTheOnlyWorkersResultCompletesTheItem() {
    var kit = item();
    kit.method(QueueItemEntity::addItem).invoke(new QueueItemEntity.AddItem(List.of(Worker.of("w1", "fetch"))));

    kit.method(QueueItemEntity::recordWorkerResult)
        .invoke(new QueueItemEntity.RecordWorkerResult("w1", "s3://result"));

    assertThat(kit.getState().state()).isEqualTo(QueueItemState.DONE);
  }

  @Test
  public void aMultiWorkerPipelineStartsIndependentWorkersTogetherThenTheDependentOne() {
    var kit = item();
    var addResult =
        kit.method(QueueItemEntity::addItem)
            .invoke(
                new QueueItemEntity.AddItem(
                    List.of(
                        Worker.of("w1", "fetch"),
                        Worker.of("w2", "fetch"),
                        Worker.dependingOn("w3", "remux", List.of("w1", "w2")))));
    assertThat(addResult.getReply()).containsExactly("w1", "w2");

    kit.method(QueueItemEntity::recordWorkerResult).invoke(new QueueItemEntity.RecordWorkerResult("w1", "a"));
    var afterFirst = kit.method(QueueItemEntity::recordWorkerResult)
        .invoke(new QueueItemEntity.RecordWorkerResult("w2", "b"));
    assertThat(afterFirst.getReply()).containsExactly("w3");

    kit.method(QueueItemEntity::recordWorkerResult).invoke(new QueueItemEntity.RecordWorkerResult("w3", "final"));
    assertThat(kit.getState().state()).isEqualTo(QueueItemState.DONE);
  }
}
