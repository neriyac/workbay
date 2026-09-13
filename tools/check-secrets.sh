#!/usr/bin/env bash
# Refuses a commit that carries a publishing token. Runs from the pre-commit hook on the
# staged diff only. The tokens tools/publish.sh reads live outside the repository; this is
# the guard for the day one is pasted into a file by mistake.
cd "$(dirname "$0")/.." || exit 1
# GitHub: ghp_/gho_/ghs_/ghr_ + 36, or github_pat_. Modrinth PAT: mrp_ + 64. CurseForge: a bcrypt
# hash ($2a$10$...). Or any of the three env names being assigned a real value.
pat='(gh[posr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{20,}|mrp_[A-Za-z0-9]{40,}|\$2[aby]\$[0-9]{2}\$[A-Za-z0-9./]{20,}|(CURSEFORGE|MODRINTH|GITHUB)_TOKEN *= *[^ ."$'"'"'{<$][^ ]{8,})'
hits=$(git diff --cached -U0 | grep -E '^\+' | grep -vE '^\+\+\+ ' | grep -E "$pat")
if [ -n "$hits" ]; then
  echo "check-secrets: a publishing token is staged. Remove it; tokens live in ~/.config/workbay/publish.env."
  echo "$hits" | cut -c1-80 | sed 's/^/  /'
  exit 1
fi
exit 0
