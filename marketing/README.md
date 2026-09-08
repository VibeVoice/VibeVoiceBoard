# marketing

Everything that leaves the repository as a picture or as copy.

```
raw/       the captures, straight off the phone, untouched
render/    one template, one caption file, one build script -> every image
out/       rendered images that do not belong in fastlane/
briefs/    what to photograph and how, one file per capture
assets/    hand-made artwork: the freed floating mark, the logo
tools/     one-off scripts (font subsetting, mark extraction)
email/     copy for outbound mail; the images come from render/
```

## Rendering

```bash
./render/build.sh          # every target
./render/build.sh store    # one target by name
```

Needs Chrome. A capture that has not been taken yet is skipped, never faked.

Output goes where `render/targets.json` says: the Play screenshots and the feature graphic straight
into `fastlane/metadata/android/`, everything else into `out/`.

## Why there is one renderer and not one per channel

There used to be two, split by destination -- a portrait template for the store, a landscape one for
email. Both carried the same caption logic, the same blob ground and the same device treatment, and
they drifted: the store centred its caption above the phone while the email set it beside, so the two
sets stopped looking like they came from the same place.

The channel is not what differs. The frame is. `frame.html` flips its layout on aspect ratio -- wider
than tall puts the caption beside the phone, taller than wide puts it above -- and a new format is a
line in `targets.json` rather than a copy of the template.

What genuinely differs by audience is the *words*, and that lives in `captions.json` as named sets:
`store` explains to strangers that punctuation is automatic; `insider` is for people who already
dictate with VibeVoice daily, to whom that is a premise rather than news.
