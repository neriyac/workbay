#!/usr/bin/env bash
# Keeps the markdown context files from growing until they cost more to read than
# they save. Run manually, or via the pre-commit hook (see tools/install-hooks.sh).
#
# TWO budgets, because there are two kinds of document and one number conflated
# them. The always-read set is context paid for on every single turn, so it stays
# tight. Reference documents are opened on demand, at the section being worked on,
# so they get room -- and neither number has to move when a big one arrives or is
# absorbed.
#
# Membership is explicit and reference is the exception: a new *.md lands in the
# tight budget by default, which forces the decision rather than hiding it.
#
# A budget is per set, not per file, so splitting a file buys nothing -- which is
# the point: two files cost the same context as one and add a lookup step.
#
# Lines, because they are trivially checkable and correlate well enough with
# tokens (~10 tokens per line here). Raise a budget only when the set is
# genuinely worth its size to a reader, never to make a failing check pass.

set -u
cd "$(dirname "$0")/.." || exit 1

ALWAYS_BUDGET=600      # read at the start of every session
REFERENCE_BUDGET=1545  # looked up, never read whole

# 1440 -> 1545 when README.md arrived (105 lines): it is the store-page copy, written once per
# release and looked up when release text is written, never read by a coding session. It is also
# the only document a stranger reads, so it is the last one that should be squeezed to fit.
# 1300 -> 1440 when ART.md arrived (106 lines) and SPEC gained the filter section.
# ART.md is a work list for whoever draws the textures, looked up once per asset and
# never read by a coding session, which is the reference category exactly. Part of the
# raise was paid rather than printed: SPEC lost its stale filter-item screen spec and
# its list of QOL features that have since shipped.
is_reference() {
  case "$1" in
    SPEC.md|MOD_MAP.md|ENDERIO_MAP.md|ART.md|README.md) return 0 ;;
    *) return 1 ;;
  esac
}

always=0
reference=0
fail=0

report() {
  printf '\n%-18s %6s\n' "$1" LINES
  printf '%s\n' "-------------------------"
}

report "ALWAYS-READ"
while IFS= read -r f; do
  [ -f "$f" ] || continue
  is_reference "$f" && continue
  n=$(grep -c '' "$f")
  always=$((always + n))
  printf '%-18s %6s\n' "$f" "$n"
done < <(git ls-files '*.md')
printf '%s\n%-18s %6s / %s\n' "-------------------------" TOTAL "$always" "$ALWAYS_BUDGET"

report "REFERENCE"
while IFS= read -r f; do
  [ -f "$f" ] || continue
  is_reference "$f" || continue
  n=$(grep -c '' "$f")
  reference=$((reference + n))
  printf '%-18s %6s\n' "$f" "$n"
done < <(git ls-files '*.md')
printf '%s\n%-18s %6s / %s\n' "-------------------------" TOTAL "$reference" "$REFERENCE_BUDGET"

printf '\n(~%s tokens if every tracked doc is read)\n' "$(((always + reference) * 10))"

[ "$always"    -gt "$ALWAYS_BUDGET"    ] && { echo; echo "Always-read set is over budget by $((always - ALWAYS_BUDGET)) lines."; fail=1; }
[ "$reference" -gt "$REFERENCE_BUDGET" ] && { echo; echo "Reference set is over budget by $((reference - REFERENCE_BUDGET)) lines."; fail=1; }

if [ "$fail" -eq 1 ]; then
  cat <<MSG

Trim before committing. Trimming means deleting what is no longer true or no
longer load-bearing -- finished work, resolved issues, decisions nobody will
revisit. It does not mean moving text into a new file: every tracked *.md
counts toward one of these two budgets.

To commit anyway: git commit --no-verify
MSG
  exit 1
fi

exit 0
