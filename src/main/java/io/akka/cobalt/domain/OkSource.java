package io.akka.cobalt.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Ported from {@code api/src/processing/services/ok.js} and its match-action.js branch. */
public final class OkSource {

  private OkSource() {}

  private static final Map<String, String> RESOLUTIONS =
      Map.ofEntries(
          Map.entry("ultra", "2160"), Map.entry("quad", "1440"), Map.entry("full", "1080"),
          Map.entry("hd", "720"), Map.entry("sd", "480"), Map.entry("low", "360"),
          Map.entry("lowest", "240"), Map.entry("mobile", "144"));

  public record Video(String name, String url, boolean disallowed) {}

  /** Already-fetched, already-parsed payload; scraping the HTML attribute is out of scope. */
  public record VideoData(String provider, boolean isLive, long durationSeconds, List<Video> videos) {}

  public sealed interface ExtractResult permits ExtractResult.Found, ExtractResult.Error {
    record Found(String url, String resolution) implements ExtractResult {}

    record Error(String code) implements ExtractResult {}
  }

  /**
   * R6: provider/live/duration guards run in this order before any stream is chosen; falling
   * back to the LAST non-disallowed stream (source order), not the highest resolution, when the
   * requested quality has no exact match.
   */
  public static ExtractResult extract(VideoData data, String requestedQuality, long durationLimit) {
    if (!"UPLOADED_ODKL".equals(data.provider())) {
      return new ExtractResult.Error("link.unsupported");
    }
    if (data.isLive()) {
      return new ExtractResult.Error("content.video.live");
    }
    if (data.durationSeconds() > durationLimit) {
      return new ExtractResult.Error("content.too_long");
    }

    List<Video> allowed = data.videos().stream().filter(v -> !v.disallowed()).toList();
    Optional<Video> exact =
        allowed.stream().filter(v -> requestedQuality.equals(RESOLUTIONS.get(v.name()))).findFirst();
    Video chosen = exact.orElseGet(() -> allowed.isEmpty() ? null : allowed.get(allowed.size() - 1));

    if (chosen == null) {
      return new ExtractResult.Error("fetch.empty");
    }
    return new ExtractResult.Found(chosen.url(), RESOLUTIONS.get(chosen.name()) + "p");
  }

  /** R6: video requests always plan to TUNNEL/proxy; ok is in {@code audioIgnore}. */
  public static ActionDecision planVideoAction(ExtractResult result) {
    if (result instanceof ExtractResult.Error error) {
      return ActionDecision.refused(error.code());
    }
    return ActionDecision.of(ResponseType.TUNNEL, "proxy");
  }

  public static ActionDecision planAudioAction() {
    return ActionDecision.refused("service.audio_not_supported");
  }
}
