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

## The four blank rows — for the VibeVoice agent

Each needs an answer from the server code. The row is identified by its `Question ID`.

### 1. `PSL_DATA_USAGE_RESPONSES:PSL_AUDIO:PSL_DATA_USAGE_EPHEMERAL`
*Is audio processed ephemerally?* Play means: held in memory only, never written to storage, kept no
longer than the request.

Our understanding is **no** — the server writes a temporary file and unlinks it in a `finally`. If
that is still true, answer `FALSE`. Check it rather than repeat it: the difference is whether the
store listing may say the recording never touches disk.

### 2. `PSL_DATA_USAGE_RESPONSES:PSL_AUDIO:PSL_DATA_USAGE_COLLECTION_AND_SHARING` / `PSL_DATA_USAGE_ONLY_SHARED`
*Is audio shared with a third party?* Currently `FALSE`, which assumes transcription happens on
infrastructure we control.

**If any part of the audio path reaches an API we do not own, this must be `TRUE`** and the sharing
purposes below it must be filled in. This is the single most consequential row in the file: getting
it wrong is the kind of misdeclaration that gets an app pulled, not warned.

### 3. `PSL_ACCOUNT_DELETION_URL`
Public URL where a user can request deletion of the account **and** its data, reachable without
signing in. Does not exist yet — it is task 3a in the handoff.

### 4. `PSL_DATA_DELETION_URL`
May be the same page as 3, or a separate one for data-only deletion.

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
