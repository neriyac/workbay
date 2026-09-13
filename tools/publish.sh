#!/usr/bin/env bash
# Publish the jar to GitHub Releases, CurseForge and Modrinth in one go.
#
#   ./tools/publish.sh            dry run: builds, writes what WOULD be uploaded under
#                                 build/publishMods/, uploads nothing
#   ./tools/publish.sh --real     the upload. Needs all three tokens, and asks first.
#
# Tokens come from ONE file outside the repository, never from the tree:
#   $WORKBAY_PUBLISH_ENV, default ~/.config/workbay/publish.env, holding
#     CURSEFORGE_TOKEN=...   (curseforge.com -> account -> API tokens)
#     MODRINTH_TOKEN=...     (modrinth.com/settings/pats: create/read/write versions)
#     GITHUB_TOKEN=...       (a fine-grained PAT with Contents: read and write on the repo)
# A missing token makes the plugin dry-run regardless of --real, so a half-filled file
# cannot half-publish. Project ids are in gradle.properties; they are public.
set -eu
cd "$(dirname "$0")/.."

env_file="${WORKBAY_PUBLISH_ENV:-$HOME/.config/workbay/publish.env}"
case "$env_file" in "$PWD"/*) echo "refusing: $env_file is inside the repository"; exit 1;; esac
if [ -f "$env_file" ]; then
  set -a; . "$env_file"; set +a
fi

if [ "${1:-}" = "--real" ]; then
  for t in CURSEFORGE_TOKEN MODRINTH_TOKEN GITHUB_TOKEN; do
    [ -n "${!t:-}" ] || { echo "missing $t in $env_file"; exit 1; }
  done
  [ -z "$(git status --porcelain)" ] || { echo "working tree is dirty; commit first"; exit 1; }
  ver=$(sed -n 's/^mod_version=//p' gradle.properties)
  echo "About to publish Workbay $ver to GitHub (tag v$ver), CurseForge and Modrinth."
  read -r -p "Type the version to confirm: " ok
  [ "$ok" = "$ver" ] || { echo "aborted"; exit 1; }
  ./gradlew build publishMods --console=plain
else
  echo "DRY RUN (add --real to upload)"
  ./gradlew build publishMods -Pworkbay.dryRun --console=plain
  echo; echo "what would have gone up:"; find build/publishMods -type f | sed 's/^/  /'
fi
