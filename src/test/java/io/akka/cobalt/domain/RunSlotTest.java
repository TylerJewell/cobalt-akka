package io.akka.cobalt.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RunSlotTest {

  @Test
  void firstAcquireSucceeds() {
    RunSlot slot = RunSlot.empty();
    assertTrue(slot.tryAcquire("a").isPresent());
  }

  @Test
  void singleFlightAcrossItems() {
    RunSlot slot = RunSlot.empty().tryAcquire("a").orElseThrow();
    assertTrue(slot.tryAcquire("b").isEmpty(), "a second item must not acquire the slot while the first holds it");

    RunSlot released = slot.release("a");
    assertTrue(released.tryAcquire("b").isPresent(), "the slot is free again once its holder releases it");
  }

  @Test
  void releaseByNonHolderIsANoOp() {
    RunSlot slot = RunSlot.empty().tryAcquire("a").orElseThrow();
    RunSlot afterWrongRelease = slot.release("b");
    assertEquals(slot, afterWrongRelease);
  }
}
