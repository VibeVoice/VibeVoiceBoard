#!/usr/bin/env python3
"""Uploads an app bundle to Google Play.

Manual means "when Florian says so", not "by hand". This does the clicking; the decision stays
where it was. Nothing here is a build side effect -- it runs only when it is run.

    tools/play-upload.py --list-tracks
    tools/play-upload.py <bundle.aab> --track alpha            # draft, nothing goes to testers
    tools/play-upload.py <bundle.aab> --track alpha --publish  # submits for review and rolls out

Credentials: play-service-account.json in the repository root (gitignored), the key for
claude@vibevoice-play.iam.gserviceaccount.com. It needs the "Release manager" role, or at least
release permission on the track being written.

Play takes .aab only -- an APK is rejected at upload, not at review.
"""

import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
KEY = os.path.join(ROOT, "play-service-account.json")
PACKAGE = "org.vibevoice.board"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"

try:
    import requests
    from google.auth.transport.requests import Request
    from google.oauth2 import service_account
except ImportError:
    sys.exit(
        "Missing dependencies. Once:\n"
        "  python3 -m venv tools/.play-venv && tools/.play-venv/bin/pip install google-auth requests\n"
        "then run this with tools/.play-venv/bin/python."
    )


def session():
    if not os.path.exists(KEY):
        sys.exit(f"No credentials at {KEY}")
    creds = service_account.Credentials.from_service_account_file(KEY, scopes=[SCOPE])
    creds.refresh(Request())
    s = requests.Session()
    s.headers["Authorization"] = f"Bearer {creds.token}"
    return s


def check(response, what):
    if response.status_code >= 300:
        sys.exit(f"{what} failed: HTTP {response.status_code}\n{response.text}")
    return response.json() if response.content else {}


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("bundle", nargs="?", help="path to the .aab")
    p.add_argument("--track", default="alpha", help="Play track (default: alpha)")
    p.add_argument("--list-tracks", action="store_true", help="print the tracks and their releases, change nothing")
    p.add_argument("--publish", action="store_true",
                   help="set the release live (goes to review, then to the track's testers). "
                        "Without it the release is left as a draft.")
    p.add_argument("--notes", default=None, help="release notes for en-US")
    p.add_argument("--notes-file", default=None,
                   help="JSON file: [{\"language\": \"en-US\", \"text\": \"...\"}, ...], for more than one locale")
    args = p.parse_args()

    s = session()
    edit = check(s.post(f"{API}/applications/{PACKAGE}/edits"), "Opening an edit")
    edit_id = edit["id"]

    if args.list_tracks:
        tracks = check(s.get(f"{API}/applications/{PACKAGE}/edits/{edit_id}/tracks"), "Listing tracks")
        print(json.dumps(tracks, indent=2))
        s.delete(f"{API}/applications/{PACKAGE}/edits/{edit_id}")
        return

    if not args.bundle:
        sys.exit("Which bundle?")
    if not args.bundle.endswith(".aab"):
        sys.exit("Play takes .aab only.")

    with open(args.bundle, "rb") as f:
        uploaded = check(
            s.post(
                f"{UPLOAD}/applications/{PACKAGE}/edits/{edit_id}/bundles?uploadType=media",
                data=f,
                headers={"Content-Type": "application/octet-stream"},
            ),
            "Uploading the bundle",
        )
    version_code = uploaded["versionCode"]
    print(f"uploaded versionCode {version_code}")

    release = {
        "versionCodes": [str(version_code)],
        "status": "completed" if args.publish else "draft",
    }
    if args.notes_file:
        with open(args.notes_file) as f:
            release["releaseNotes"] = json.load(f)
    elif args.notes:
        release["releaseNotes"] = [{"language": "en-US", "text": args.notes}]
    check(
        s.put(
            f"{API}/applications/{PACKAGE}/edits/{edit_id}/tracks/{args.track}",
            json={"track": args.track, "releases": [release]},
        ),
        f"Writing track {args.track}",
    )

    check(s.post(f"{API}/applications/{PACKAGE}/edits/{edit_id}:commit"), "Committing the edit")
    print(f"{args.track}: versionCode {version_code} is {release['status']}")
    if not args.publish:
        print("Draft. Nothing reaches a tester until it is published.")


if __name__ == "__main__":
    main()
