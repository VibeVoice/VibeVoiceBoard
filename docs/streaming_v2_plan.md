# Streaming protocol v2 in the keyboard — plan

Status: plan, not started. The server side is live since 2026-10-30 at
`wss://vibevoice.net/api/v2/stream`; its contract is `codebase_docs/48-streaming-protocol-v2.md`
in the server repository, and where that document and `server_side/modules/stream_v2_wire.py`
disagree, the code is right. The keyboard will be the first production client of v2.

## Why

The v1 client has grown its own resilience around a protocol that tells it very little: it infers
acknowledgements from content frames, guesses how long to wait for a final after a stop, guesses
whether a session survived a reconnect, and learns of a dead link only from TCP or a 10 s ping.
Bug report #10866 is the shape of the cost: the server had transcribed the speech, but the link
was dead before it could deliver it, and the phone gave up without knowing.

v2 answers each of those guesses with a frame:

| v1 guess | v2 answer |
|---|---|
| how much audio the server holds | `progress.received_ms`, every second |
| up to where all text is delivered | `progress.ack_ms` / `segment.ack_ms` |
| is the link dead | no `progress` for 3 s |
| did the session survive a reconnect | `ready.resume.resumed` |
| is the dictation finished | the `end` frame, with `last_idx` and `billed_ms` |
| which text did I miss | replay of every segment after `last_idx`, `idx` continuing across a resume |
| is this error worth retrying | `error.code` + `retryable` + close code 4000–4029 |
| is the server going down | `restarting` with `reconnect_after_ms` |

## What stays ours

- Audio capture (mic recovery, zero-buffer detection, capture-policy silence).
- The 30 s rolling buffer: keep audio from `ack_ms` on.
- Keeping buffered audio through a slow or failed first connection (PR #13).
- Text insertion and session state in `LatinIME`.

## Design

### 1. Split capture from protocol (refactor, no behaviour change)

`VibeVoiceClient.kt` is 1500+ lines and mixes capture, buffer, v1 wire, account linking and
preferences. Extract, with v1 behaviour unchanged:

- `AudioCapture.kt` — `AudioRecord` lifecycle, recovery, level for the waves; delivers frames.
- `RollingBuffer.kt` — the 30 s ring with `totalRead` and `read(from)`; unit-tested.
- The companion object's key, trial and preference helpers stay where callers find them.

v1 then consumes `AudioCapture` + `RollingBuffer`. This step ships on its own and is tested on the
phone before any v2 code exists, so a regression is attributable.

### 2. `StreamV2Wire.kt` — frames, nothing else

Pure Kotlin, no Android: build `start`/`flush`/`end`/`ping`, parse `ready`/`segment`/`flushed`/
`progress`/`warning`/`error`/`restarting`/`end`/`pong` into sealed classes. Unknown fields ignored,
unknown `type` logged and ignored. Unit tests use the example frames from doc 48 and from
`stream_v2_wire.py`.

`start` sends `api_key`, `audio` (`pcm_s16le`, 16000, 1), `client` (`vibevoice-board`, version
name), `resume` when reconnecting with `ttl_s: 120`. `language` absent (account setting, then
detection). `vocabulary` not in the first version (see "Later").

### 3. `VibeVoiceStreamV2.kt` — the session

Same public surface as `VibeVoiceClient` (`startStreaming`, `stopStreaming`, `cancel`,
`currentLevel`, `isLinkDegraded`) and the same `VibeVoiceListener`, so `LatinIME` chooses one by
a factory and nothing else changes for it.

States: `CONNECTING` → `READY` (streaming) → `ENDING` (`end` sent) → `ENDED`, plus
`RECONNECTING` from any of the first three.

- **Positions.** One mapping from the stream's audio clock to capture bytes:
  `capturePos = streamOriginPos + ms * 32`. `streamOriginPos` is set when a new stream (not a
  resumed one) starts sending.
- **Link watchdog.** No `progress` for 3 s while `READY` or `ENDING` → drop the socket and
  reconnect. Before `ready`, the handshake rule from PR #13 applies: degraded after 8 s, give up
  only when the buffer or the 30 s window is exhausted.
- **Reconnect.** `start` with `resume {token, last_idx, ttl_s}`.
  - `resumed: true` → the server replays missed segments; send audio from `received_ms`.
  - `resumed: false` → new stream, clock 0: send from the last `ack_ms`, reset the applied-idx set,
    trim the overlap with the text already inserted (`trimLeadingOverlap`, existing).
  - Reconnect while `ENDING` → resume with **no audio and no `end`**; the server replays and ends.
  - Backoff 500/1000/2000/3000 ms as today, bounded by the buffer and the 30 s window.
- **Segments.** Apply each `idx` once (set of applied indices per stream). Every segment is final,
  so it is committed at once; there is no composing text in v2. Listener: a new
  `onSegment(text)` that commits, rather than bending `onPartial`/`onFinal`, whose prefix logic
  exists only for v1's growing partials.
- **End.** The `end` frame ends the session: `onClosed()`. No backstop grace heuristics. A session
  that closes after a stop without `end`, after a failed resume, reports `onTailMissing` as today.
- **Errors.** Non-retryable codes go to `onError` with the code; `invalid_api_key` → link prompt,
  `quota_exceeded` / `trial_exhausted` → the existing trial and quota UI (`ERR_TRIAL_EXHAUSTED`).
  Retryable codes reconnect after `retry_after_ms` / `reconnect_after_ms`. `restarting` → new
  stream after `reconnect_after_ms`, from the last `ack_ms`.
- **Stop.** `stopStreaming` sends `end` (or defers it until `ready`), keeps reading until `end`.
  Upper bound on waiting: 15 s after the last `progress`, then `onTailMissing`.

### 4. Choosing the protocol, and falling back

- Preference `vibevoice_stream_protocol` (`v1` / `v2`), shown in the debug settings only.
  Default `v2` in debug builds, `v1` in release builds until the beta has run.
- Automatic fallback to v1, for that session only and logged: the upgrade to
  `/api/v2/stream` fails with an HTTP status (404, 426), or the server answers `bad_start` /
  `unknown_message`. Network failures do not fall back; they are the same on both.
- Bug reports and the debug log name the protocol of each session.

### 5. Logging

Same markers as v1 where the meaning is the same (`WS Open`, `[EMPTY_RESULT]`, `[TAIL_MISSING]`,
`[RESEND_SHORT]`), plus `[V2]` lines for `ready` (session id, resumed, received_ms), each
`progress` gap over 1.5 s, `segment idx`, replayed segments, `end` (last_idx, billed_ms) and every
`error` code.

## Order of work

1. Refactor: `AudioCapture` + `RollingBuffer` out of `VibeVoiceClient`. Unit tests for the buffer.
   Phone test on v1. Own PR.
2. `StreamV2Wire` + unit tests. Own PR.
3. `VibeVoiceStreamV2` + `onSegment` in `LatinIME` + protocol preference + fallback. Debug build
   on v2 by default. Own PR, Conductor review.
4. Phone test matrix (below) on the debug build.
5. Alpha release with v2 still off by default, then a build with v2 on by default and v1 fallback.

## Test matrix (phone)

| Case | Expected |
|---|---|
| Normal dictation, short and long (> 60 s) | text per segment, nothing lost, `end` received |
| Wi-Fi off mid-dictation, back within 20 s | resumed, missed segments replayed once, no duplicates |
| Wi-Fi off, stop pressed, Wi-Fi back | replay-only resume, ending arrives, no notice |
| No network at start, back within 20 s | first stream sends from the first sample |
| Network switch Wi-Fi ↔ mobile mid-dictation (the #10866 case) | dead link noticed within 3 s, resumed |
| Server restart during dictation (staging) | `restarting`, new stream from `ack_ms`, overlap trimmed |
| Invalid key / exhausted trial | link prompt / trial UI, no retry loop |
| v2 endpoint unreachable by HTTP status | session falls back to v1 and works |

## Questions for the server side

- A staging URL the debug build can point at, and a way to trigger `restarting` there.
- How long dictation is segmented without `flush` (which P-111 preset applies by default), i.e.
  how long a user waits for the first text in a long dictation. If that is too long, the keyboard
  may send `flush` on a local pause; doc 48 warns that this changes the words around the cut.

## Later

- `vocabulary` from the user's personal dictionary.
- Upload to `POST /api/transcribe` when an outage exceeds the buffer.
- Removing v1 from the keyboard once v2 has been the default for a release cycle.
