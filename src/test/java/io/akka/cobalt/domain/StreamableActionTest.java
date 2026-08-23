package io.akka.cobalt.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StreamableActionTest {

  private static StreamableSource.Video video() {
    return new StreamableSource.Video(
        Map.of(
            "mp4-mobile", new StreamableSource.File("mob", 640, 360),
            "mp4", new StreamableSource.File("hi", 1920, 1080)));
  }

  @Test
  void defaultsToMobileFileBelow720() {
    var result = StreamableSource.extract(video(), "x", false, "480");
    assertInstanceOf(StreamableSource.ExtractResult.Found.class, result);
    assertEquals("mob", ((StreamableSource.ExtractResult.Found) result).url());
    assertEquals("streamable_x_640x360.mp4", ((StreamableSource.ExtractResult.Found) result).filename());
  }

  @Test
  void upgradesToFullFileAtQuality720OrAbove() {
    var result = StreamableSource.extract(video(), "x", false, "720");
    assertEquals("hi", ((StreamableSource.ExtractResult.Found) result).url());
  }

  @Test
  void upgradesToFullFileForAudioOnlyRegardlessOfQuality() {
    var result = StreamableSource.extract(video(), "x", true, "240");
    assertEquals("hi", ((StreamableSource.ExtractResult.Found) result).url());
  }

  @Test
  void upgradesToFullFileAtMaxQuality() {
    var result = StreamableSource.extract(video(), "x", false, "max");
    assertEquals("hi", ((StreamableSource.ExtractResult.Found) result).url());
  }

  @Test
  void noMatchingFileErrorsFetchFail() {
    var result = StreamableSource.extract(new StreamableSource.Video(Map.of()), "x", false, "480");
    assertInstanceOf(StreamableSource.ExtractResult.Error.class, result);
    assertEquals("fetch.fail", ((StreamableSource.ExtractResult.Error) result).code());
  }

  @Test
  void videoRequestAlwaysPlansToRedirect() {
    var action = StreamableSource.planVideoAction(StreamableSource.extract(video(), "x", false, "480"));
    assertEquals(ResponseType.REDIRECT, action.responseType());
    assertTrue(action.errorCode().isEmpty());
  }

  @Test
  void extractorErrorPropagatesAsRefusal() {
    var action =
        StreamableSource.planVideoAction(StreamableSource.extract(new StreamableSource.Video(Map.of()), "x", false, "480"));
    assertEquals(ResponseType.REFUSED, action.responseType());
    assertEquals("fetch.fail", action.errorCode().orElseThrow());
  }
}
