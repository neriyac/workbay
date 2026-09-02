#!/usr/bin/env bash
# Keeps the markdown context files from growing until they cost more to read than
# they save. Run manually, or via the pre-commit hook (see tools/install-hooks.sh).
#
# Caps are line counts because those are trivially checkable and correlate well
# enough with tokens (~10 tokens per line here). Raise a cap only when the file is
# genuinely worth its size to a reader, never to make a failing check pass.

set -u
cd "$(dirname "$0")/.." || exit 1

# file:max_lines:why
LIMITS="
HANDOFF.md:130:current state, not a log; trim finished work instead of appending
OPEN_ISSUES.md:75:sweep in batches; move fixed items to Resolved, then delete old ones
MOD_MAP.md:140:one row per real discovery, not per repo that exists
ENDERIO_MAP.md:140:rows earn their place by having been used
WORKPLAN.md:60:the plan, not its history
CLAUDE.md:90:read every session; every line here is paid for on every turn
TEMPLATE.md:80:setup steps only; explanation belongs in the file it describes
"

fail=0
total=0
printf '%-18s %6s %6s   %s\n' FILE LINES CAP STATUS
printf '%s\n' "-------------------------------------------------------"

while IFS=: read -r f cap why; do
  [ -z "$f" ] && continue
  if [ ! -f "$f" ]; then
    printf '%-18s %6s %6s   %s\n' "$f" - "$cap" "missing (ok if not created yet)"
    continue
  fi
  n=$(grep -c '' "$f")
  total=$((total + n))
  if [ "$n" -gt "$cap" ]; then
    printf '%-18s %6s %6s   OVER BY %s\n' "$f" "$n" "$cap" "$((n - cap))"
    printf '%-18s %6s %6s   -> %s\n' "" "" "" "$why"
    fail=1
  else
    printf '%-18s %6s %6s   ok\n' "$f" "$n" "$cap"
  fi
done <<< "$LIMITS"

printf '%s\n' "-------------------------------------------------------"
printf 'total %s lines, roughly %s tokens if every file is read.\n' "$total" "$((total * 10))"

if [ "$fail" -ne 0 ]; then
  cat <<'MSG'

Trim the file(s) above before committing.

Trimming means deleting what is no longer true or no longer load-bearing --
finished work, resolved issues, decisions nobody will revisit. It does not mean
moving text into a new file: a second file costs the same context as the first,
and splitting is how a doc set quietly doubles.

To commit anyway: git commit --no-verify
MSG
fi

exit "$fail"
