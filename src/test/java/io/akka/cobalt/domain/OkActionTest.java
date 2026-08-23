package io.akka.cobalt.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class OkActionTest {

  @Test
  void picksExactRequestedResolution() {
    var data =
        new OkSource.VideoData(
            "UPLOADED_ODKL",
            false,
            10,
            List.of(new OkSource.Video("sd", "u480", false), new OkSource.Video("hd", "u720", false)));

    var result = OkSource.extract(data, "720", 100);
    assertInstanceOf(OkSource.ExtractResult.Found.class, result);
    assertEquals("u720", ((OkSource.ExtractResult.Found) result).url());
    assertEquals("720p", ((OkSource.ExtractResult.Found) result).resolution());
  }

  @Test
  void fallsBackToLastNonDisallowedVideoWhenQualityAbsent() {
    var data =
        new OkSource.VideoData(
            "UPLOADED_ODKL",
            false,
            10,
            List.of(
                new OkSource.Video("lowest", "u240", false),
                new OkSource.Video("sd", "u480", false),
                new OkSource.Video("hd", "u720", true)));

    var result = OkSource.extract(data, "720", 100);
    assertEquals(
        "u480", ((OkSource.ExtractResult.Found) result).url(), "must fall back to the LAST allowed stream, not the first");
  }

  @Test
  void rejectsLiveVideoBeforeCheckingDuration() {
    var data = new OkSource.VideoData("UPLOADED_ODKL", true, 999_999, List.of());
    var result = OkSource.extract(data, "720", 100);
    assertEquals("content.video.live", ((OkSource.ExtractResult.Error) result).code());
  }

  @Test
  void rejectsOverDurationVideo() {
    var data = new OkSource.VideoData("UPLOADED_ODKL", false, 999, List.of());
    var result = OkSource.extract(data, "720", 100);
    assertEquals("content.too_long", ((OkSource.ExtractResult.Error) result).code());
  }

  @Test
  void rejectsUnsupportedProviderBeforeAnyOtherCheck() {
    var data = new OkSource.VideoData("SOMETHING_ELSE", true, 999_999, List.of());
    var result = OkSource.extract(data, "720", 100);
    assertEquals("link.unsupported", ((OkSource.ExtractResult.Error) result).code());
  }

  @Test
  void videoRequestAlwaysPlansToTunnelProxy() {
    var data = new OkSource.VideoData("UPLOADED_ODKL", false, 10, List.of(new OkSource.Video("sd", "u", false)));
    var action = OkSource.planVideoAction(OkSource.extract(data, "480", 100));
    assertEquals(ResponseType.TUNNEL, action.responseType());
    assertEquals("proxy", action.type().orElseThrow());
  }

  @Test
  void audioRequestIsAlwaysRefused() {
    var action = OkSource.planAudioAction();
    assertEquals(ResponseType.REFUSED, action.responseType());
    assertEquals("service.audio_not_supported", action.errorCode().orElseThrow());
  }
}
