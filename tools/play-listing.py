#!/usr/bin/env python3
"""Compare (default) or push (--apply) the Play store listing text and phone screenshots.

Source of truth: fastlane/metadata/android/<locale>/full_description.txt and the store set of
marketing/render/captions.json (images read from marketing/shots/<motif>/tall.store.<lang>.png).
Without --apply nothing changes. Never prints credentials. Needs play-service-account.json in the main
checkout and the venv from CLAUDE.md:
  tools/.play-venv/bin/python tools/play-listing.py [--apply]
Run it from a worktree with WT=$PWD so it reads that worktree's files.
"""
import hashlib, json, os, sys
import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

HERE = os.path.dirname(os.path.abspath(__file__))
WT = os.environ.get("WT", os.path.dirname(HERE))
MAIN = os.environ.get("MAIN", WT)
KEY = os.path.join(MAIN, "play-service-account.json")
PKG = "org.vibevoice.board"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + PKG
UP = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/" + PKG
LANGS = {"en-US": "en", "de-DE": "de"}
APPLY = "--apply" in sys.argv

creds = service_account.Credentials.from_service_account_file(
    KEY, scopes=["https://www.googleapis.com/auth/androidpublisher"])
creds.refresh(Request())
s = requests.Session()
s.headers["Authorization"] = "Bearer " + creds.token


def ok(r, what):
    if r.status_code >= 300:
        sys.exit(f"{what}: HTTP {r.status_code} {r.text[:400]}")
    return r.json() if r.content else {}


edit = ok(s.post(f"{API}/edits", json={}), "insert edit")["id"]
captions = json.load(open(f"{WT}/marketing/render/captions.json"))
order = [m["id"] for m in captions["sets"]["store"]]

for play, short in LANGS.items():
    cur = ok(s.get(f"{API}/edits/{edit}/listings/{play}"), f"get listing {play}")
    new_text = open(f"{WT}/fastlane/metadata/android/{play}/full_description.txt").read().rstrip("\n")
    same = cur.get("fullDescription", "").strip() == new_text.strip()
    import difflib
    for l in difflib.unified_diff(cur.get("fullDescription", "").strip().splitlines(), new_text.strip().splitlines(), "live", "repo", lineterm="", n=0):
        print("    " + l[:150])
    wer = [l for l in cur.get("fullDescription", "").splitlines() if "rror rate" in l or "ehlerrate" in l]
    print(f"[{play}] live WER line: {wer}")
    print(f"[{play}] description identical to repo: {same}")

    imgs = ok(s.get(f"{API}/edits/{edit}/listings/{play}/phoneScreenshots"), f"list images {play}").get("images", [])
    live = [i["sha256"] for i in imgs]
    local = []
    for m in order:
        p = f"{WT}/marketing/shots/{m}/tall.store.{short}.png"
        if not os.path.exists(p):
            print(f"    (skipped {m}: no capture taken yet)")
            continue
        local.append((m, hashlib.sha256(open(p, "rb").read()).hexdigest(), p))
    print(f"[{play}] live screenshots: {len(live)}; local store set: {[m for m, _, _ in local]}")
    for m, h, _ in local:
        print(f"    {m}: {'in store' if h in live else 'DIFFERS from store'}")
    if APPLY:
        if not same:
            body = dict(cur)
            body["fullDescription"] = new_text
            ok(s.put(f"{API}/edits/{edit}/listings/{play}", json=body), f"update listing {play}")
            print(f"[{play}] description updated")
        if [h for _, h, _ in local] != live:
            ok(s.delete(f"{API}/edits/{edit}/listings/{play}/phoneScreenshots"), f"delete images {play}")
            for m, h, p in local:
                r = s.post(f"{UP}/edits/{edit}/listings/{play}/phoneScreenshots",
                           params={"uploadType": "media"}, data=open(p, "rb").read(),
                           headers={"Content-Type": "image/png"})
                ok(r, f"upload {m} {play}")
            print(f"[{play}] screenshots replaced in order")

if APPLY:
    ok(s.post(f"{API}/edits/{edit}:commit"), "commit edit")
    print("committed")
else:
    ok(s.delete(f"{API}/edits/{edit}"), "discard edit")
    print("read-only: edit discarded, nothing changed")
