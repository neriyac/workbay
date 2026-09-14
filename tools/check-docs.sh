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
REFERENCE_BUDGET=1785  # looked up, never read whole

# 1760 -> 1785 when the gallery arrived (2026-09-14): MODPAGE.md carries the image order and the
# caption per picture, which is what whoever pastes the store page needs and nothing else records.
# 1690 -> 1760 when the release paperwork arrived (2026-09-14): MODPAGE.md (the store-page text,
# pasted once per release) and CHANGELOG.md (its top section is what tools/publish.sh posts).
# Paid in part: README.md shrank from 104 lines of store copy to 50 of repo front door.
# 1600 -> 1690 when WALKTHROUGH.md arrived (79 lines): the twenty-minute walk of the product,
# looked up before a release and after any change to a screen, never read by a coding session.
# It is the one document in this repo that would have prevented the night it came out of --
# Neriya found nineteen faults in one sitting because he *used* the mod, and every round before
# that had read the report instead. Part of the raise was paid rather than printed: SPEC lost
# §5's Bay View specification (28 lines), which stopped being true when the screen was deleted.
# 1580 -> 1600 when the room shell became a thing a player looks at: §8 gained the block, the
# colour, the light and the 2x2 door, and ART.md gained the five greyscale textures' own rules
# (why they are greyscale, why smooth took two tries, why the door is drawn shut). Most of it was
# paid rather than printed: §16's v2 order went, being a build order for finished work, and §15's
# four purpose-built test machines went with it -- they were the fallback for "what if no foreign
# mod can be tested against", and Mekanism loading into gameTestServer answered that.
# 1545 -> 1580 when SPEC gained the rooms design (79 lines: §0's four new closed decisions, §8's
# Rooms subsection, and §16's v2 order). Rooms are the biggest thing left and this is the only
# record of why they are shaped the way they are. Most of the raise was paid rather than printed:
# §15 lost its list of 105 shipped gametests and its answered cross-mod spike, and §6 lost its
# copies of strings that live in the generated lang file and had already drifted from them (it
# still said `status.workbay.*`; the built keys are `gui.workbay.status.*`).
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
    SPEC.md|MOD_MAP.md|ENDERIO_MAP.md|ART.md|README.md|WALKTHROUGH.md|MODPAGE.md|CHANGELOG.md) return 0 ;;
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
