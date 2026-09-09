# Data safety — what is filled in, and what is still open

`data_safety.csv` is the Play Console export, filled in. Import it back under
**App content → Data safety → Import from CSV**.

Every choice row carries an explicit `TRUE` or `FALSE`; nothing is left implicit, so a question that
was never considered cannot pass for a "no". **Four rows are deliberately blank** because they depend
on what the server does, not on what the app does — see below.

## What was declared, and why

Verified against the app source, not assumed.

| Declared | Where it comes from |
|---|---|
| **Voice or sound recordings** — collected, not shared, required, app functionality | `VibeVoiceClient` streams 16 kHz PCM to `wss://vibevoice.net/stream` while a session runs |
| **Diagnostics** — collected, not shared, *optional*, app functionality | `VibeVoiceBugReporter` sends app version, OS, device model, memory stats and `client_logs` — but only when the user files a bug report |
| **Device or other IDs** — collected, not shared, required, app functionality + fraud prevention | `install_id` goes to `/api/oauth/device/code` so the one-off free trial cannot be claimed twice |
| Encrypted in transit: **yes** | WSS and HTTPS throughout |
| Account creation methods: **none** | Accounts are made on vibevoice.net in a browser. The app links an existing one with an OAuth 2.0 device grant and stores only the resulting API key. Declared instead under "accounts created outside the app". |
| Users can request deletion: **yes** | URL still to come, see below |

Everything else is `FALSE`. Worth stating explicitly, because two of them look like they should be true:

* **Email address — not declared.** The app never sees one. Linking returns an API key; there is no
  email field anywhere in `helium314.keyboard.latin.vibevoice`. The address is collected by
  vibevoice.net at signup, in a browser, which is not this app's collection. Declaring it would put a
  claim on the store listing that the app does not support.
* **Contacts — not declared.** `READ_CONTACTS` is inherited from HeliBoard and
  `SettingsValues.readUseContactsEnabled` now returns `false` unconditionally.

## The four rows that were blank — answered

All four are filled in. The answers came from the VibeVoice agent, who read them out of the server
rather than out of memory; two of them corrected what this file used to claim.

### `PSL_DATA_DELETION_URL` → `https://vibevoice.net/delete-account`

**`PSL_ACCOUNT_DELETION_URL` stays empty**, and it has to. It is a follow-up to "which account
creation methods does your app support", and we answer that with `PSL_ACM_NONE`: accounts are made on
vibevoice.net in a browser, the app only links one. Play therefore never asks the question, and an
import that answers it anyway is refused with *"You cannot answer PSL_ACCOUNT_DELETION_URL"*.

The URL is not lost — it hangs off "can users request that their data is deleted", which is `yes`.
One page answers what is asked. Public, no login, English. It lists what deletion actually removes — read out
of `delete_account_cascade()`, including the keyboard's trial `install_id` — and names the billing
records that stay de-identified under German retention law. It also states the consequence nobody
expects: if a paid subscription is running it is cancelled first, and if that fails, nothing is
deleted at all.

> **The page is built but not deployed.** It answers only on the admin-only preview host until the
> frontend deploy is released. **Do not import this CSV into the Console before the URL responds
> publicly** — a data-deletion URL that 404s is worse than a missing one.

### `…:PSL_AUDIO:PSL_DATA_USAGE_EPHEMERAL` → `FALSE`

The answer is right and the reason this file used to give was wrong, which is worth keeping written
down because the wrong reason was plausible.

"Temporary file, unlinked in a `finally`" belongs to the **batch upload path** (`job_processor.py`),
which the keyboard never touches. The keyboard's path is `/stream` → `transcribe_array()`: mono
float32 numpy handed straight to recognition, no filesystem at all; `streaming_ws.py` writes nothing
to disk. On that evidence alone the honest answer would have been `TRUE`.

`FALSE` holds for a different reason. Production runs with `TRAINING_CAPTURE_USER_ID=1` — confirmed
in `/etc/vibevoice.env` and in the running process. For that one account, the operator's own, the
full session is written to the NAS as a WAV. "Memory only" therefore does not hold without exception,
and `TRUE` would be a misdeclaration. `DEBUG_SAVE_AUDIO_USER_ID` is not set.

**Anyone who wants this row to say `TRUE` has to turn the training capture off.** That is a decision
worth making deliberately; its only prize is one sentence on the store listing.

### `…:PSL_AUDIO:…COLLECTION_AND_SHARING` / `PSL_DATA_USAGE_ONLY_SHARED` → `FALSE`

Previously this assumed transcription runs on our own infrastructure. It now rests on a search:
`server_side/` and `config/` were swept for OpenAI, Deepgram, AssemblyAI, Azure, Google Speech,
Speechmatics, Groq, Replicate and HF Inference, and outside tests there is nothing. Recognition runs
on our own GPU.

One qualification that had to be checked rather than assumed: `vibevoice.net` has been orange-clouded
since 2026-08-09, and that includes the WebSocket stream, so the dictation audio does pass through
Cloudflare with TLS terminated there. `FALSE` still holds, because Google's definition of sharing
excludes a service provider processing on our behalf, and Cloudflare is named as a processor in the
privacy policy and the DPA annex.

## Two judgement calls worth a second opinion

* **Diagnostics purpose = App functionality.** Bug reports exist to fix the app. "Analytics" would
  also be defensible; the listing wording differs slightly. Either survives review.
* **Device ID marked required rather than optional.** The install ID is only sent when claiming the
  trial, which a user could skip by linking an account first — so "optional" is arguable. Required is
  the more conservative reading and needs no explaining.

## After the answers arrive

1. Fill the four rows in `data_safety.csv`
2. Play Console → App content → Data safety → **Import from CSV**
3. Walk the preview once; the summary is what appears on the store listing
