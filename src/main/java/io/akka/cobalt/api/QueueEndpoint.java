package io.akka.cobalt.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import io.akka.cobalt.application.QueueItemEntity;
import io.akka.cobalt.domain.QueueItem;
import java.util.List;

/** The download queue's own HTTP surface — add an item, feed back worker results, read its state. */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/items")
public class QueueEndpoint {

  private final ComponentClient componentClient;

  public QueueEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/{itemId}")
  public List<String> addItem(String itemId, QueueItemEntity.AddItem body) {
    return componentClient.forEventSourcedEntity(itemId).method(QueueItemEntity::addItem).invoke(body);
  }

  @Post("/{itemId}/worker-results")
  public List<String> recordWorkerResult(String itemId, QueueItemEntity.RecordWorkerResult body) {
    return componentClient.forEventSourcedEntity(itemId).method(QueueItemEntity::recordWorkerResult).invoke(body);
  }

  @Get("/{itemId}")
  public QueueItem get(String itemId) {
    return componentClient.forEventSourcedEntity(itemId).method(QueueItemEntity::get).invoke();
  }
}
