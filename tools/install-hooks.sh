#!/usr/bin/env bash
# One-time setup after cloning: make git use the hooks tracked in this repo.
set -e
cd "$(dirname "$0")/.."
git config core.hooksPath .githooks
echo "core.hooksPath = .githooks"
echo "pre-commit -> tools/check-docs.sh   (doc size caps)"
echo "pre-push   -> tools/verify.sh       (build + gametests + doc caps)"
