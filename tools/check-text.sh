#!/usr/bin/env bash
# Text that does not fit its box, and text that arrives in silence: both made impossible to ship
# rather than found again later.
#
# The first was fixed twice and came back twice, because both fixes audited the call sites.
# Auditing a list closes the list; it does not close the hole the list came out of. The hole is
# GuiGraphics#drawString: it takes a position and no width, so every call site is free to be wrong
# on its own and nothing anywhere can tell.
#
# So Draw#text is the only place allowed to call it, and it cannot be called without a width. This
# is what stops the next call site opting out. Runs in the pre-commit hook and in verify.sh.
#
# The second is the same shape one layer up. SPEC.md §6 puts a rejection on the action bar, which
# is one grey line above the hotbar that a player looking at the screen they just clicked does not
# see -- forty of them shipped that way. WorkbaySounds#refuse writes the line and makes the sound
# in one call, so the two cannot come apart, and displayClientMessage(x, true) is the call that
# can be wrong.

set -u
cd "$(dirname "$0")/.." || exit 1

fail=0

ALLOWED='src/main/java/com/neryos/workbay/client/screen/Draw.java'

hits=$(grep -rn --include='*.java' -E '\.(drawString|drawCenteredString|drawWordWrap|plainSubstrByWidth)\(' \
  src/main/java src/gametest/java src/datagen/java 2>/dev/null | grep -v "^$ALLOWED:")

if [ -n "$hits" ]; then
  cat <<MSG
Text drawn without a width:

$hits

Draw#text is the only place in this mod that may call drawString, and it takes the
width the string has. Use it -- WorkbayPage#text / textRight / textCentre / wrapped
from a page, Draw#text directly from anything that is not one.
MSG
  fail=1
fi

SOUNDS='src/main/java/com/neryos/workbay/WorkbaySounds.java'

# The whole call, not the ", true" that names the action bar: most of these wrap, and the flag can
# sit three lines below the call -- which is how one of the forty survived a rewrite that matched
# on it, and how a first draft of this check matched nothing at all and passed. A comment naming
# the method is not a call, so lines that open with * or // are let through.
quiet=$(grep -rn --include='*.java' -e '\.displayClientMessage(' src/main/java 2>/dev/null \
  | grep -v "^$SOUNDS:" | grep -vE '^[^:]+:[0-9]+: *(\*|//)')

if [ -n "$quiet" ]; then
  cat <<MSG
Action-bar text with no sound:

$quiet

WorkbaySounds#refuse and #confirm are the only places in this mod that may put a
line on the action bar, and each makes the sound that goes with it. A rejection
nobody hears is one nobody reads.
MSG
  fail=1
fi

# Third one, same shape. Vanilla has no bold face: Font#renderChar draws the glyph a second time
# one pixel to the right. The bitmap font's thinnest stroke is a whole pixel so the copy overlaps
# and reads as weight; this mod's font is an antialiased TTF at 9.5px, where a comma and a slash
# ARE one pixel, so the copy lands beside the original and a power tooltip reads
# "1,,598,,000 // 1,600,000". OPEN_ISSUES #78. Emphasis on these screens is colour, never weight.
bold=$(grep -rn --include='*.java' -e 'withBold(' -e 'ChatFormatting.BOLD' src/main/java 2>/dev/null   | grep -vE '^[^:]+:[0-9]+: *(\*|//)')

if [ -n "$bold" ]; then
  cat <<MSG
Bold asked for in this mod's own font:

$bold

There is no bold face -- vanilla draws the glyph twice, one pixel apart, which
splits every one-pixel glyph in this mod's TTF into two. Use Draw.TEXT over
Draw.TEXT_DIM for emphasis.
MSG
  fail=1
fi

if [ "$fail" -eq 1 ]; then
  echo
  echo "To commit anyway: git commit --no-verify"
  exit 1
fi

exit 0
