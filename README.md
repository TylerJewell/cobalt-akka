# cobalt-akka

Decides, for a resolved download source and an already-fetched piece of media
information, what response shape a download item takes, then runs its multi-worker
pipeline to completion one item at a time, respecting worker dependencies.

A port of [imputnet/cobalt](https://github.com/imputnet/cobalt) onto **Akka**, built
with **Akka Specify**.

---

## Where it came from

cobalt is a media downloader: paste a link from one of over twenty sites, and it
resolves the link, decides how to serve the file back (redirect straight to the host,
tunnel it through cobalt's own server, or run it through local processing), and tracks
that work as an item in a queue with a progress bar. This port was chosen to test the
method against a system built as a request-response web service plus a client-side
state machine, rather than the durable-entity or crawl-queue shapes earlier ports
covered — and to see whether a decision procedure spread across a JavaScript switch
statement and a Svelte store translates cleanly into a typed domain model.

The specifications this port was generated from are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness)
under `cobalt-port/`.

---

## imputnet/cobalt → this port

📉 626 scope-matched lines → **472 lines**<br>
📁 5 source files → **16 files**<br>
⚡ 19.43 ns/case → **46.18 ns/case**, per-decision time (Node.js → Java, median of 3 runs)<br>
🎯 17/17 → **17/17** benchmark cases agree exactly

**Every line is `OLD → NEW`, in that order, and every number is measured** — see
[`bench/REPORT.md`](../cobalt-port/bench/REPORT.md) in the harness for the method and
what these numbers do and do not show.

---

## What it took to build

⏱️ **0.7 hours** from the first command to the published repository, **0.7** of them active<br>
💬 **440** exchanges with the model<br>
✍️ **234,846** tokens written by the model, **95,266,175** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **43** tests

```bash
python toolkit/tokens.py --port cobalt    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/akka-specify-harness/tree/main/port-log)
in the harness repository.

---

## What it does

From the specification:

- **At most one download item runs at a time.** A second item queued while one is
  already running waits; it is promoted only once the running item finishes or fails.
- **A worker with an unfinished dependency blocks every worker after it in that
  round, not just itself.** Two independent workers — a video fetch and an audio
  fetch with nothing depending on either — start together; a worker waiting on either
  of them does not start until both are done, and nothing past it in the pipeline
  jumps ahead of it either.
- **An item's result comes from its last pipeline worker, not its most recently
  finished one.** If that worker's own result is missing, the item fails outright
  rather than completing with nothing to show.
- **ok.ru's provider, live, and duration checks run in that fixed order before any
  stream is chosen**, and a quality that doesn't exist falls back to the last
  available stream, not the first or the best.
- **Reddit refuses an audio-only request against a post with no separate audio
  track**, rather than silently handing back the video.

Generated documentation lives at [`docs/index.html`](docs/index.html) — open it in a
browser for the entity diagram, the interaction path, and the component reference.

---

## Design decisions

**One entity per download item, not one entity holding a whole queue.** cobalt keeps
its queue as a single object in one browser tab's memory. Akka's unit of durable state
is the entity, and giving each item its own means the system works the same way
whether there are ten queued items or ten thousand, rather than inheriting a design
built around exactly one person's browser tab.

**The single-running-item rule lives in its own small piece of state, called a run
slot.** cobalt gets this rule for free, because a browser tab can only do one thing
at a time. Once items are separate entities, nothing enforces it automatically, so
this port names the rule directly instead of leaving it to be true by accident.

**A worker's "not finished yet" and "finished with nothing to show" are two different
states, not one.** The original tells these apart by whether a value in a lookup
table is present versus falsy. This port makes the same distinction with a proper
tag on each worker's result, so a reader doesn't have to remember which kind of
emptiness means what.

**Only three of over twenty download sources are ported.** Adding a fourth site's
rules would repeat a shape one of the first three already covers — always redirect,
always proxy, or branch on whether a separate audio track exists — so the extra work
would test the same thing again rather than teach anything new about the method.

**Progress percentages come from the caller, not from watching the network.** cobalt
reads progress off the browser's own fetch API as bytes arrive. This port has no
network layer of its own to watch (see "Where it differs from cobalt"), so a worker
reports its own percentage back in, the same way it reports a finished result.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/cobalt-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9069.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9069**.

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| — | — | This port has no configuration beyond the Akka runtime's own — every rule it decides takes its inputs as command parameters, not environment variables. |

---

## Where it differs from cobalt

Everything not listed here behaves the same way on purpose, including the parts that
look like mistakes.

- **Multiple download items can be in progress across the whole system at once, just
  not from the same run slot unless a caller wires one up.** cobalt's queue never
  faces this question, because one browser tab only ever holds one queue. This port
  makes each item its own entity so it can scale past a single tab's worth of work;
  enforcing "only one running at a time" across every item a caller creates is left to
  the caller, who has the `RunSlot` rule available to opt into it. Not checked against
  a real multi-caller scenario — `not checked`, only unit-tested in isolation.
- **The maximum allowed video duration is a request parameter here, not a
  process-wide setting.** cobalt reads one value from its own server's environment for
  every request; this port takes it as an input to each decision so a caller can vary
  it, which changes nothing about the order the ok.ru guards run in.
- **Worker execution itself — the actual network fetch, and the ffmpeg remux/encode
  step — is not implemented.** This port decides what a pipeline should look like and
  tracks it to completion once told a worker finished; running ffmpeg or fetching a
  URL is a capability this port never attempted, not a difference in how the two
  systems do the same job.
- **The queue's own screen is not the original's Svelte app, reduced to one static
  card.** cobalt's queue popover is part of a full browser application with local file
  storage and an ffmpeg-in-the-browser pipeline, none of which this port implements
  (see above). The screen shown here reuses that component's exact markup and colours
  and is fed by a genuine server-sent stream rather than polling, but it has no
  download button, no retry button, and no file storage behind it, because there is
  nothing on this port's side for those buttons to do.
- **Result files are referred to by a short string, not held as an in-memory file
  object.** The original holds a browser `File`/`Blob` in memory once a worker
  finishes; this port, running on a server rather than in a browser, holds a
  reference (a URL or storage key) instead.

---

## Licence

imputnet/cobalt is AGPL-3.0, © imputnet/cobalt's contributors. This port's decision
logic is derived from the source's behaviour (not copied source text — see
`ACKNOWLEDGEMENTS.md` in the harness for what overlaps and why), and is licensed
AGPL-3.0 in turn — see `LICENSE`.
