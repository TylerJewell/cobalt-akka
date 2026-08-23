package io.akka.cobalt.api;

import akka.NotUsed;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import io.akka.cobalt.application.QueueItemEntity;
import io.akka.cobalt.domain.QueueItem;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The queue item card's data source — gui/index.html.
 *
 * <p>RENDERING.md R1: the view shows state the server (this entity) owns, so it is pushed
 * rather than polled. Progress percentages for in-flight workers are not tracked by this
 * port (SPEC-001 §1 puts worker execution out of scope), so they always read 0 here; R5's
 * comparison against the original is limited to the state pill and progress-bar-count shape
 * for that reason — see docs/review-findings.md.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/items")
public class QueueStreamEndpoint {

  private static final Duration TICK = Duration.ofMillis(500);

  private final ComponentClient componentClient;

  public QueueStreamEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record WorkerView(String workerId, String kind) {}

  public record Payload(String id, String state, String filename, List<WorkerView> pipeline, Map<String, Double> workerProgress) {}

  @Get("/{itemId}/stream")
  public HttpResponse stream(String itemId) {
    Source<Payload, NotUsed> source =
        Source.tick(Duration.ZERO, TICK, "")
            .map(ignored -> payloadFor(itemId))
            .statefulMapConcat(
                () -> {
                  var previous = new Payload[1];
                  return payload -> {
                    if (payload.equals(previous[0])) {
                      return List.of();
                    }
                    previous[0] = payload;
                    return List.of(payload);
                  };
                })
            .mapMaterializedValue(ignored -> NotUsed.getInstance());

    return HttpResponses.serverSentEvents(source);
  }

  private Payload payloadFor(String itemId) {
    QueueItem item;
    try {
      item = componentClient.forEventSourcedEntity(itemId).method(QueueItemEntity::get).invoke();
    } catch (RuntimeException e) {
      return new Payload(itemId, "waiting", itemId, List.of(), Map.of());
    }

    var pipeline = item.pipeline().stream().map(w -> new WorkerView(w.workerId(), w.kind())).toList();
    // No in-flight percentage is tracked by this slice, so every worker reports 0 until it
    // has a recorded result, matching Progress.of's own default for that case.
    var workerProgress = item.pipeline().stream().collect(Collectors.toMap(w -> w.workerId(), w -> 0.0));

    return new Payload(item.id(), item.state().name().toLowerCase(), item.id(), pipeline, workerProgress);
  }
}
