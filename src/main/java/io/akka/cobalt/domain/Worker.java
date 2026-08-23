package io.akka.cobalt.domain;

import java.util.List;
import java.util.Set;

/** One stage of a queue item's pipeline. SPEC-001 §2. */
public record Worker(String workerId, String kind, Set<String> dependsOn) {

  public Worker {
    dependsOn = dependsOn == null ? Set.of() : Set.copyOf(dependsOn);
  }

  public static Worker of(String workerId, String kind) {
    return new Worker(workerId, kind, Set.of());
  }

  public static Worker dependingOn(String workerId, String kind, List<String> dependsOn) {
    return new Worker(workerId, kind, Set.copyOf(dependsOn));
  }
}
