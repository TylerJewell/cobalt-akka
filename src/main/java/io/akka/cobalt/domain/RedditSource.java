package io.akka.cobalt.domain;

import java.util.Optional;

/** Ported from {@code api/src/processing/services/reddit.js} and its match-action.js branch. */
public final class RedditSource {

  private RedditSource() {}

  /**
   * Already-fetched, already-parsed post payload. Network, cookie/OAuth refresh, and the HEAD
   * requests used to probe for a distinct audio file are out of scope (SPEC-001 §1); {@code
   * distinctAudioTrackExists} stands in for what those HEAD requests decide.
   */
  public record Post(Optional<String> gifUrl, Optional<String> videoUrl, boolean distinctAudioTrackExists) {}

  public sealed interface ExtractResult
      permits ExtractResult.Gif, ExtractResult.VideoOnly, ExtractResult.Merged, ExtractResult.Error {
    record Gif(String url, String filename) implements ExtractResult {}

    record VideoOnly(String url) implements ExtractResult {}

    record Merged(String videoUrl, String audioUrl, String filename, String audioFilename) implements ExtractResult {}

    record Error(String code) implements ExtractResult {}
  }

  /** R8: a .gif post short-circuits before the video/audio-track check ever runs. */
  public static ExtractResult extract(Post post, String sourceId) {
    if (post.gifUrl().isPresent()) {
      return new ExtractResult.Gif(post.gifUrl().get(), "reddit_" + sourceId + ".gif");
    }
    if (post.videoUrl().isEmpty()) {
      return new ExtractResult.Error("fetch.empty");
    }
    if (!post.distinctAudioTrackExists()) {
      return new ExtractResult.VideoOnly(post.videoUrl().get());
    }
    return new ExtractResult.Merged(
        post.videoUrl().get(), "AUDIO_URL", "reddit_" + sourceId + ".mp4", "reddit_" + sourceId + "_audio");
  }

  /** R8: gif and video-only-with-no-audio-track both plan to REDIRECT; merged plans to TUNNEL/merge. */
  public static ActionDecision planVideoAction(ExtractResult result) {
    return switch (result) {
      case ExtractResult.Error error -> ActionDecision.refused(error.code());
      case ExtractResult.Gif ignored -> new ActionDecision(ResponseType.REDIRECT, Optional.empty(), Optional.empty());
      case ExtractResult.VideoOnly ignored ->
          new ActionDecision(ResponseType.REDIRECT, Optional.empty(), Optional.empty());
      case ExtractResult.Merged ignored -> ActionDecision.of(ResponseType.TUNNEL, "merge");
    };
  }

  /** R8: an audio-only request against a no-distinct-audio-track result is refused, not proxied. */
  public static ActionDecision planAudioAction(ExtractResult result) {
    boolean hasNoAudioTrack = result instanceof ExtractResult.VideoOnly || result instanceof ExtractResult.Gif;
    if (hasNoAudioTrack) {
      return ActionDecision.refused("service.audio_not_supported");
    }
    if (result instanceof ExtractResult.Error error) {
      return ActionDecision.refused(error.code());
    }
    return ActionDecision.of(ResponseType.TUNNEL, "audio");
  }
}
