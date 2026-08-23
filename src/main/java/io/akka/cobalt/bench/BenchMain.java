package io.akka.cobalt.bench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.akka.cobalt.domain.ActionDecision;
import io.akka.cobalt.domain.LocalProcessing;
import io.akka.cobalt.domain.OkSource;
import io.akka.cobalt.domain.ResponseType;
import io.akka.cobalt.domain.RedditSource;
import io.akka.cobalt.domain.StreamableSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * bench/REPORT.md §1 — runs the SAME workloads.json through the port's own domain
 * classes (not the source's), so the answers can be diffed against
 * bench/run_source.mjs's output of the same file.
 *
 * <p>Usage: {@code java -cp <classes> io.akka.cobalt.bench.BenchMain <workloads.json>
 * <output.json> [iterations]}
 */
public final class BenchMain {

  private sealed interface Case permits StreamableCase, OkCase, RedditCase, OverrideCase {
    String name();
  }

  private record StreamableCase(String name, StreamableSource.Video video, String id, boolean isAudioOnly, String quality)
      implements Case {}

  private record OkCase(String name, OkSource.VideoData data, String requestedQuality, long durationLimit) implements Case {}

  private record RedditCase(String name, RedditSource.Post post, String sourceId) implements Case {}

  private record OverrideCase(String name, ActionDecision decision, boolean alwaysProxy, LocalProcessing localProcessing)
      implements Case {}

  public static void main(String[] args) throws IOException {
    Path workloadsPath = Path.of(args[0]);
    Path outputPath = Path.of(args[1]);
    int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 1;

    ObjectMapper mapper = new ObjectMapper();
    JsonNode workloads = mapper.readTree(Files.readString(workloadsPath));

    List<Case> cases = new ArrayList<>();
    for (JsonNode w : workloads) {
      cases.add(
          switch (w.get("source").asText()) {
            case "streamable" -> parseStreamable(w);
            case "ok" -> parseOk(w);
            case "reddit" -> parseReddit(w);
            case "override" -> parseOverride(w);
            default -> throw new IllegalArgumentException("unknown source: " + w.get("source"));
          });
    }

    // Answers file: built once, not inside the timed loop, so the timing below measures
    // only the decision logic — the same thing bench/run_source.mjs's loop measures,
    // and not JSON serialization, which the source's loop never does either.
    ArrayNode out = mapper.createArrayNode();
    for (Case c : cases) out.add(answerOf(mapper, c));
    Files.writeString(outputPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(out));

    // Warmup, same shape as run_source.mjs's single warmup pass.
    runAll(cases);

    long startNanos = System.nanoTime();
    for (int i = 0; i < iterations; i++) {
      runAll(cases);
    }
    long elapsedNanos = System.nanoTime() - startNanos;

    System.out.println(
        "iterations=" + iterations + " total_cases=" + cases.size() + " elapsed_ns=" + elapsedNanos
            + " ns_per_case=" + (elapsedNanos / (double) (iterations * cases.size())));
  }

  private static void runAll(List<Case> cases) {
    for (Case c : cases) {
      switch (c) {
        case StreamableCase s -> StreamableSource.extract(s.video(), s.id(), s.isAudioOnly(), s.quality());
        case OkCase o -> OkSource.extract(o.data(), o.requestedQuality(), o.durationLimit());
        case RedditCase r -> RedditSource.extract(r.post(), r.sourceId());
        case OverrideCase v -> v.decision().withOverrides(v.alwaysProxy(), v.localProcessing());
      }
    }
  }

  private static StreamableCase parseStreamable(JsonNode w) {
    Map<String, StreamableSource.File> files = new LinkedHashMap<>();
    w.get("files")
        .properties()
        .forEach(
            e -> {
              JsonNode f = e.getValue();
              files.put(e.getKey(), new StreamableSource.File(f.get("url").asText(), f.get("width").asInt(), f.get("height").asInt()));
            });
    return new StreamableCase(
        w.get("name").asText(),
        new StreamableSource.Video(files),
        w.get("id").asText(),
        w.get("isAudioOnly").asBoolean(),
        w.get("quality").asText());
  }

  private static OkCase parseOk(JsonNode w) {
    List<OkSource.Video> videos = new ArrayList<>();
    for (JsonNode v : w.get("videos")) {
      videos.add(new OkSource.Video(v.get("name").asText(), v.get("url").asText(), v.get("disallowed").asBoolean()));
    }
    var data =
        new OkSource.VideoData(w.get("provider").asText(), w.get("isLive").asBoolean(), w.get("durationSeconds").asLong(), videos);
    return new OkCase(w.get("name").asText(), data, w.get("requestedQuality").asText(), w.get("durationLimit").asLong());
  }

  private static RedditCase parseReddit(JsonNode w) {
    Optional<String> gifUrl = w.get("gifUrl").isNull() ? Optional.empty() : Optional.of(w.get("gifUrl").asText());
    Optional<String> videoUrl = w.get("videoUrl").isNull() ? Optional.empty() : Optional.of(w.get("videoUrl").asText());
    return new RedditCase(
        w.get("name").asText(),
        new RedditSource.Post(gifUrl, videoUrl, w.get("distinctAudioTrackExists").asBoolean()),
        w.get("sourceId").asText());
  }

  private static OverrideCase parseOverride(JsonNode w) {
    ResponseType responseType = ResponseType.valueOf(w.get("responseType").asText().toUpperCase().replace('-', '_'));
    Optional<String> type = w.get("type").isNull() ? Optional.empty() : Optional.of(w.get("type").asText());
    var decision = new ActionDecision(responseType, type, Optional.empty());
    LocalProcessing localProcessing = LocalProcessing.valueOf(w.get("localProcessing").asText().toUpperCase());
    return new OverrideCase(w.get("name").asText(), decision, w.get("alwaysProxy").asBoolean(), localProcessing);
  }

  private static ObjectNode answerOf(ObjectMapper mapper, Case c) {
    ObjectNode node = mapper.createObjectNode();
    node.put("name", c.name());
    ObjectNode r = node.putObject("result");

    switch (c) {
      case StreamableCase s -> {
        var result = StreamableSource.extract(s.video(), s.id(), s.isAudioOnly(), s.quality());
        if (result instanceof StreamableSource.ExtractResult.Found found) {
          r.put("kind", "found");
          r.put("url", found.url());
          r.put("filename", found.filename());
        } else {
          r.put("kind", "error");
          r.put("code", ((StreamableSource.ExtractResult.Error) result).code());
        }
      }
      case OkCase o -> {
        var result = OkSource.extract(o.data(), o.requestedQuality(), o.durationLimit());
        if (result instanceof OkSource.ExtractResult.Found found) {
          r.put("kind", "found");
          r.put("url", found.url());
          r.put("resolution", found.resolution());
        } else {
          r.put("kind", "error");
          r.put("code", ((OkSource.ExtractResult.Error) result).code());
        }
      }
      case RedditCase red -> {
        var result = RedditSource.extract(red.post(), red.sourceId());
        if (result instanceof RedditSource.ExtractResult.Gif gif) {
          r.put("kind", "gif");
          r.put("url", gif.url());
          r.put("filename", gif.filename());
        } else if (result instanceof RedditSource.ExtractResult.VideoOnly videoOnly) {
          r.put("kind", "video-only");
          r.put("url", videoOnly.url());
        } else if (result instanceof RedditSource.ExtractResult.Merged merged) {
          r.put("kind", "merged");
          r.put("videoUrl", merged.videoUrl());
          r.put("audioUrl", merged.audioUrl());
          r.put("filename", merged.filename());
          r.put("audioFilename", merged.audioFilename());
        } else {
          r.put("kind", "error");
          r.put("code", ((RedditSource.ExtractResult.Error) result).code());
        }
      }
      case OverrideCase v -> {
        var result = v.decision().withOverrides(v.alwaysProxy(), v.localProcessing());
        r.put("responseType", result.responseType().name().toLowerCase().replace('_', '-'));
        r.put("type", result.type().orElse(null));
      }
    }
    return node;
  }
}
