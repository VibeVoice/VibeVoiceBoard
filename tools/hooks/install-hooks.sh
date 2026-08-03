#!/bin/bash
# install-hooks.sh — installs VibeVoiceBoard git hooks from tools/hooks/
# Run once after cloning: bash tools/hooks/install-hooks.sh

set -e

REPO_ROOT="$(git rev-parse --show-toplevel)"
HOOKS_SRC="$REPO_ROOT/tools/hooks"
HOOKS_DEST="$REPO_ROOT/.git/hooks"

HOOKS=("post-commit" "post-merge" "post-push")

for HOOK in "${HOOKS[@]}"; do
  cp "$HOOKS_SRC/$HOOK" "$HOOKS_DEST/$HOOK"
  chmod +x "$HOOKS_DEST/$HOOK"
  echo "Installed: .git/hooks/$HOOK"
done

echo ""
echo "All hooks installed. Version is currently: $(cat "$REPO_ROOT/VERSION")"
echo "  post-commit -> bumps patch on every commit"
echo "  post-merge  -> bumps minor + resets patch on merges to main/master/feature-vibevoice"
echo "  post-push   -> builds APK and uploads to Nextcloud after every push (skip with SKIP_APK_BUILD=1)"
