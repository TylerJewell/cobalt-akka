package io.akka.cobalt.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ActionOverrideTest {

  @Test
  void alwaysProxyRewritesRedirectToTunnelProxy() {
    var decision = new ActionDecision(ResponseType.REDIRECT, java.util.Optional.empty(), java.util.Optional.empty());
    var result = decision.withOverrides(true, LocalProcessing.DISABLED);
    assertEquals(ResponseType.TUNNEL, result.responseType());
    assertEquals("proxy", result.type().orElseThrow());
  }

  @Test
  void forcedLocalProcessingWinsOverAlwaysProxyRewrite() {
    var decision = new ActionDecision(ResponseType.REDIRECT, java.util.Optional.empty(), java.util.Optional.empty());
    var result = decision.withOverrides(true, LocalProcessing.FORCED);
    assertEquals(ResponseType.LOCAL_PROCESSING, result.responseType());
    assertEquals("proxy", result.type().orElseThrow());
  }

  @Test
  void preferredLocalProcessingOnlyAppliesWhenTypeNeedsExtraProcessing() {
    var proxyDecision = ActionDecision.of(ResponseType.TUNNEL, "proxy");
    assertEquals(ResponseType.TUNNEL, proxyDecision.withOverrides(false, LocalProcessing.PREFERRED).responseType());

    var mergeDecision = ActionDecision.of(ResponseType.TUNNEL, "merge");
    assertEquals(
        ResponseType.LOCAL_PROCESSING, mergeDecision.withOverrides(false, LocalProcessing.PREFERRED).responseType());
  }

  @Test
  void refusedDecisionIsUntouchedByOverrides() {
    var refused = ActionDecision.refused("service.audio_not_supported");
    assertEquals(refused, refused.withOverrides(true, LocalProcessing.FORCED));
  }
}
