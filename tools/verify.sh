#!/usr/bin/env bash
# Everything a CI server would run, run locally instead. Free, no minutes, no remote.
#
#   ./tools/verify.sh          build + gametests + doc caps
#   ./tools/verify.sh --fast   skip the gametest server (~15s faster)
#
# Also runs as the pre-push hook. Bypass a failing push with: git push --no-verify

set -u
cd "$(dirname "$0")/.." || exit 1

fast=0
[ "${1:-}" = "--fast" ] && fast=1

mkdir -p build
log="build/verify.log"
fails=""

# Quiet on success, full output on failure. Gradle's -q does not silence the game
# itself, so a run's output goes to a file and is shown only when it matters.
step() {
  name="$1"; shift
  printf '%-16s ' "$name"
  if "$@" > "$log" 2>&1; then
    printf 'PASS\n'
  else
    printf 'FAIL\n'
    fails="$fails $name"
    echo "---------------- last 30 lines ----------------"
    tail -30 "$log"
    echo "----------------------------------------------"
    echo "full output: $log"
  fi
}

# build compiles main *and* gametest, because check depends on compileGametestJava
step "build" ./gradlew build --console=plain

if [ "$fast" -eq 0 ]; then
  # runGameTestServer exits non-zero when a test fails, so its status is the signal
  step "gametests" ./gradlew runGameTestServer --console=plain
else
  printf '%-16s SKIPPED (--fast)\n' "gametests"
fi

step "doc caps" ./tools/check-docs.sh

echo "----------------------------------------------"
if [ -n "$fails" ]; then
  echo "FAILED:$fails"
  exit 1
fi
rm -f "$log"
echo "all checks passed"
