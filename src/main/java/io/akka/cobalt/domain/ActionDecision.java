package io.akka.cobalt.domain;

import java.util.Optional;

/** Output of per-source action planning (match-action.js's {@code responseType}/{@code type}). */
public record ActionDecision(ResponseType responseType, Optional<String> type, Optional<String> errorCode) {

  public static ActionDecision refused(String errorCode) {
    return new ActionDecision(ResponseType.REFUSED, Optional.empty(), Optional.of(errorCode));
  }

  public static ActionDecision of(ResponseType responseType, String type) {
    return new ActionDecision(responseType, Optional.ofNullable(type), Optional.empty());
  }

  /**
   * R9: {@code alwaysProxy} can rewrite a REDIRECT into TUNNEL/proxy; a {@code localProcessing}
   * of FORCED (or PREFERRED with a type that needs extra local processing) then rewrites the
   * result again to LOCAL_PROCESSING, running after and able to override the alwaysProxy rewrite.
   */
  public ActionDecision withOverrides(boolean alwaysProxy, LocalProcessing localProcessing) {
    ActionDecision current = this;
    if (current.errorCode.isPresent()) {
      return current;
    }

    if (alwaysProxy && current.responseType == ResponseType.REDIRECT) {
      current = ActionDecision.of(ResponseType.TUNNEL, "proxy");
    }

    boolean needsExtraProcessing =
        current.type.map(t -> java.util.Set.of("merge", "remux", "mute", "audio", "gif").contains(t)).orElse(false);
    boolean isPreferredWithExtra = localProcessing == LocalProcessing.PREFERRED && needsExtraProcessing;

    if (localProcessing == LocalProcessing.FORCED || isPreferredWithExtra) {
      current = new ActionDecision(ResponseType.LOCAL_PROCESSING, current.type, Optional.empty());
    }

    return current;
  }
}
