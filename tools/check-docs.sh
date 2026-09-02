#!/usr/bin/env bash
# Keeps the markdown context files from growing until they cost more to read than
# they save. Run manually, or via the pre-commit hook (see tools/install-hooks.sh).
#
# One budget for the whole doc set, not a cap per file. Per-file caps made every
# file sit a line under its ceiling, so any real addition forced an unrelated
# deletion in the same file. A shared budget lets one doc grow while another
# shrinks, and still fails when the set as a whole gets expensive.
#
# The set is every tracked *.md, so splitting a file buys nothing -- which is the
# point: two files cost the same context as one and add a lookup step.
#
# Lines, because they are trivially checkable and correlate well enough with
# tokens (~10 tokens per line here). Raise the budget only when the set is
# genuinely worth its size to a reader, never to make a failing check pass.

set -u
cd "$(dirname "$0")/.." || exit 1

BUDGET=900

total=0
printf '%-18s %6s\n' FILE LINES
printf '%s\n' "-------------------------"

while IFS= read -r f; do
  [ -f "$f" ] || continue
  n=$(grep -c '' "$f")
  total=$((total + n))
  printf '%-18s %6s\n' "$f" "$n"
done < <(git ls-files '*.md')

printf '%s\n' "-------------------------"
printf '%-18s %6s / %s   (~%s tokens if every file is read)\n' \
  TOTAL "$total" "$BUDGET" "$((total * 10))"

if [ "$total" -gt "$BUDGET" ]; then
  cat <<MSG

Doc set is over budget by $((total - BUDGET)) lines. Trim before committing.

Trimming means deleting what is no longer true or no longer load-bearing --
finished work, resolved issues, decisions nobody will revisit. It does not mean
moving text into a new file: every tracked *.md counts toward the same budget.

To commit anyway: git commit --no-verify
MSG
  exit 1
fi

exit 0
