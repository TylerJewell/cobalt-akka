package io.akka.cobalt.domain;

import java.util.Map;
import java.util.Optional;

/** Ported from {@code api/src/processing/services/streamable.js} and its match-action.js branch. */
public final class StreamableSource {

  private StreamableSource() {}

  public record File(String url, int width, int height) {}

  /** Already-fetched payload; the network call itself is out of scope (see SPEC-001 §1). */
  public record Video(Map<String, File> files) {}

  public sealed interface ExtractResult permits ExtractResult.Found, ExtractResult.Error {
    record Found(String url, String filename) implements ExtractResult {}

    record Error(String code) implements ExtractResult {}
  }

  /** R7: prefers the mobile file unless audio-only, "max" quality, or quality >= 720. */
  public static ExtractResult extract(Video video, String id, boolean isAudioOnly, String quality) {
    File best = video.files().get("mp4-mobile");
    File full = video.files().get("mp4");

    boolean wantsFull = isAudioOnly || "max".equals(quality) || isNumericAtLeast720(quality);
    if (full != null && wantsFull) {
      best = full;
    }

    if (best != null) {
      return new ExtractResult.Found(best.url(), "streamable_" + id + "_" + best.width() + "x" + best.height() + ".mp4");
    }
    return new ExtractResult.Error("fetch.fail");
  }

  private static boolean isNumericAtLeast720(String quality) {
    try {
      return Integer.parseInt(quality) >= 720;
    } catch (NumberFormatException | NullPointerException e) {
      return false;
    }
  }

  /** R7: video requests always plan to REDIRECT. */
  public static ActionDecision planVideoAction(ExtractResult result) {
    if (result instanceof ExtractResult.Error error) {
      return ActionDecision.refused(error.code());
    }
    return new ActionDecision(ResponseType.REDIRECT, Optional.empty(), Optional.empty());
  }
}
