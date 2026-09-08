# marketing/email

Copy and images for outbound email, kept next to the store assets because they share the raw
captures, the typography and the blob background.

| | |
|---|---|
| `tester-invite.en.md` | The closed-test invitation: subject, body, and notes for whoever builds the HTML |
| `card.html` | Landscape card template, authored at 700 × 640 and shot at 2× |
| `cards.json` | Which capture, which words, and any crop or zoom |
| `build_email.sh` | Chrome headless → `images/*.png` |
| `images/` | The rendered cards, 1400 × 1280 |

```bash
./build_email.sh
```

Needs Chrome and the captures in `marketing/raw/`. Anything missing is skipped rather than faked.

The store images live one directory over in `marketing/overlays/` and are portrait 9:16, because
that is the shape Play displays. These are landscape for the same reason inverted: an email is read
in a column about 600 px wide, where a 9:16 image is a thousand pixels tall and nobody scrolls it.
The two templates share their type, colours and background and nothing else.
