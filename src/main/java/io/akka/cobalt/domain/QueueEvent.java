package io.akka.cobalt.domain;

import akka.javasdk.annotations.TypeName;
import java.util.List;

public sealed interface QueueEvent {

  @TypeName("item-added")
  record ItemAdded(List<Worker> pipeline) implements QueueEvent {}

  @TypeName("item-started-running")
  record ItemStartedRunning() implements QueueEvent {}

  @TypeName("worker-result-recorded")
  record WorkerResultRecorded(String workerId, String resultRef) implements QueueEvent {}

  @TypeName("item-completed")
  record ItemCompleted() implements QueueEvent {}

  @TypeName("item-failed")
  record ItemFailed(String errorCode) implements QueueEvent {}
}
