package io.akka.cobalt.domain;

import java.util.Optional;

/**
 * R2: at most one item runs at a time. The source enforces this implicitly, since one browser
 * tab has one in-memory queue; this port makes it explicit as its own piece of state so that
 * multiple {@code QueueItemEntity} instances can still agree on which one, if any, holds the
 * run slot. See SPEC-001 §4, "one entity per queue item."
 */
public record RunSlot(Optional<String> holder) {

  public static RunSlot empty() {
    return new RunSlot(Optional.empty());
  }

  public Optional<RunSlot> tryAcquire(String itemId) {
    if (holder.isPresent()) {
      return Optional.empty();
    }
    return Optional.of(new RunSlot(Optional.of(itemId)));
  }

  public RunSlot release(String itemId) {
    if (holder.isPresent() && holder.get().equals(itemId)) {
      return RunSlot.empty();
    }
    return this;
  }
}
