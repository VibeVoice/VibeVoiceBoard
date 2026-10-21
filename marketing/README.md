# marketing

Everything that leaves the repository as a picture or as copy.

```
shots/<motif>/   one directory per motif -- everything about it, side by side
  raw.jpg          the capture, straight off the phone, untouched
  tall.store.en.png    1440x2560   the Play screenshot
  tall.store.de.png
  wide.store.en.png    1400x1280   for a web page or an email
  wide.store.de.png
  wide.insider.en.png  the tester email's wording, where it has one
render/    frame.html · feature.html · captions.json · targets.json · build.sh
briefs/    what to photograph and how
assets/    hand-made artwork: the freed floating mark, the logo
tools/     one-off scripts (font subsetting, mark extraction)
email/     copy for outbound mail; the images come from shots/
```

## Why grouped by motif and not by format

Because that is how they are judged. "Is the WhatsApp one any good" is a question about a capture and
everything made from it at once -- both layouts, both languages, both wordings -- and answering it
used to mean opening four directories that shared nothing but a subject. Now it is one listing.

Nothing is written into `fastlane/` any more except the feature graphic. The Play screenshots are
read straight out of `shots/` at upload time, in the order the `store` set lists them in
`captions.json`. That order is the display order in the listing, and it lives in one place instead of
in filenames -- which also retires the numbered output that once let ten inherited HeliBoard
screenshots survive a shorter run and nearly ship inside our own listing.

## Rendering

```bash
./render/build.sh          # every target
./render/build.sh store    # one target by name
```

Needs Chrome. A capture that has not been taken yet is skipped, never faked.

Output goes beside the capture it came from, named `<layout>.<set>.<language>.png`.

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
