package io.akka.cobalt.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RedditActionTest {

  @Test
  void gifPostShortCircuitsBeforeVideoCheck() {
    var post = new RedditSource.Post(Optional.of("https://i.redd.it/x.gif"), Optional.empty(), false);
    var result = RedditSource.extract(post, "s_1");
    assertInstanceOf(RedditSource.ExtractResult.Gif.class, result);
    assertEquals("reddit_s_1.gif", ((RedditSource.ExtractResult.Gif) result).filename());
  }

  @Test
  void noVideoNoGifErrorsFetchEmpty() {
    var post = new RedditSource.Post(Optional.empty(), Optional.empty(), false);
    var result = RedditSource.extract(post, "s_1");
    assertEquals("fetch.empty", ((RedditSource.ExtractResult.Error) result).code());
  }

  @Test
  void videoWithNoDistinctAudioTrackRedirectsToVideoOnly() {
    var post = new RedditSource.Post(Optional.empty(), Optional.of("https://v.redd.it/x/DASH_720.mp4"), false);
    var result = RedditSource.extract(post, "s_1");
    assertInstanceOf(RedditSource.ExtractResult.VideoOnly.class, result);

    var action = RedditSource.planVideoAction(result);
    assertEquals(ResponseType.REDIRECT, action.responseType());
  }

  @Test
  void videoWithDistinctAudioTrackMerges() {
    var post = new RedditSource.Post(Optional.empty(), Optional.of("https://v.redd.it/x/DASH_720.mp4"), true);
    var result = RedditSource.extract(post, "s_1");
    assertInstanceOf(RedditSource.ExtractResult.Merged.class, result);

    var action = RedditSource.planVideoAction(result);
    assertEquals(ResponseType.TUNNEL, action.responseType());
    assertEquals("merge", action.type().orElseThrow());
  }

  @Test
  void audioRequestAgainstNoAudioTrackResultIsRefused() {
    var post = new RedditSource.Post(Optional.empty(), Optional.of("https://v.redd.it/x/DASH_720.mp4"), false);
    var result = RedditSource.extract(post, "s_1");

    var action = RedditSource.planAudioAction(result);
    assertEquals(ResponseType.REFUSED, action.responseType());
    assertEquals("service.audio_not_supported", action.errorCode().orElseThrow());
  }

  @Test
  void audioRequestAgainstAudioTrackResultIsServed() {
    var post = new RedditSource.Post(Optional.empty(), Optional.of("https://v.redd.it/x/DASH_720.mp4"), true);
    var result = RedditSource.extract(post, "s_1");

    var action = RedditSource.planAudioAction(result);
    assertEquals(ResponseType.TUNNEL, action.responseType());
    assertEquals("audio", action.type().orElseThrow());
  }
}
