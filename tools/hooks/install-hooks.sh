#!/bin/bash
# install-hooks.sh — installs VibeVoiceBoard git hooks from tools/hooks/
# Run once after cloning: bash tools/hooks/install-hooks.sh

set -e

REPO_ROOT="$(git rev-parse --show-toplevel)"
HOOKS_SRC="$REPO_ROOT/tools/hooks"
# git rev-parse, not "$REPO_ROOT/.git": in a worktree .git is a file, and the hooks live in the
# main checkout's git directory.
HOOKS_DEST="$(git rev-parse --git-common-dir)/hooks"

# Remove old post-commit hook if present
rm -f "$HOOKS_DEST/post-commit"

HOOKS=("pre-commit" "post-merge" "pre-push")

for HOOK in "${HOOKS[@]}"; do
  cp "$HOOKS_SRC/$HOOK" "$HOOKS_DEST/$HOOK"
  chmod +x "$HOOKS_DEST/$HOOK"
  echo "Installed: .git/hooks/$HOOK"
done

echo ""
echo "All hooks installed. Release $(cat "$REPO_ROOT/VERSION"), version code $(cat "$REPO_ROOT/VERSION_CODE")"
echo "  pre-commit  -> bumps VERSION_CODE on every commit (included in same commit)"
echo "  post-merge  -> no-op (kept so an old minor-bump hook gets replaced)"
echo "  pre-push    -> builds APK and uploads to Nextcloud in background on every push (skip with SKIP_APK_BUILD=1)"
