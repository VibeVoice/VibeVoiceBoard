#!/usr/bin/env python3
"""Turn targets.json + captions.json into one tab-separated job per line for build.sh.

Kept separate from the shell so no caption ever has to survive a round trip through word splitting:
the URL leaves here already encoded, and an empty lead -- which the hero has, deliberately -- cannot
shift the remaining fields the way it would if bash were reading them as columns.
"""
import json, os, sys, urllib.parse

HERE, ROOT, ONLY = sys.argv[1], sys.argv[2], (sys.argv[3] if len(sys.argv) > 3 else "")

targets  = json.load(open(f"{HERE}/targets.json"))
captions = json.load(open(f"{HERE}/captions.json"))["sets"]
shots    = f"{ROOT}/marketing/shots"

out = []
def emit(*fields):
    out.append("\t".join(str(f) for f in fields))

def url(template, **params):
    q = urllib.parse.urlencode({k: v for k, v in params.items() if v not in (None, "")})
    return f"file://{HERE}/{template}" + (f"?{q}" if q else "")

for t in targets["targets"]:
    if ONLY and t["name"] != ONLY:
        continue
    emit("HEAD", t["name"])
    for locale, short in t["locales"].items():
        for img in captions[t["set"]]:
            shot = f"{shots}/{img['id']}/raw.jpg"
            if not os.path.exists(shot):
                emit("SKIP", img["id"], f"shots/{img['id']}/raw.jpg")
                continue
            c = img.get(short, {})
            dest = f"{ROOT}/" + targets["out"].format(
                id=img["id"], layout=t["layout"], set=t["set"], locale=locale)
            emit("RENDER", t["width"], t["height"], dest,
                 url(t.get("template", "frame.html"),
                     shot=f"file://{shot}",
                     lead=c.get("lead", ""), rest=c.get("rest", ""), note=c.get("note", ""),
                     crop=img.get("crop"), zoom=img.get("zoom"), focusx=img.get("focusx")))

f = targets.get("feature")
if f and (not ONLY or ONLY == "feature"):
    emit("HEAD", "feature")
    for locale in f["locales"]:
        emit("RENDER", f["width"], f["height"],
             f"{ROOT}/" + f["out"].format(locale=locale), url(f["template"]))

print("\n".join(out))
