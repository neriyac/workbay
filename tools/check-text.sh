#!/usr/bin/env bash
# Text that does not fit its box, made impossible to ship rather than found again later.
#
# It was fixed twice and came back twice, because both fixes audited the call sites. Auditing a
# list closes the list; it does not close the hole the list came out of. The hole is
# GuiGraphics#drawString: it takes a position and no width, so every call site is free to be wrong
# on its own and nothing anywhere can tell.
#
# So Draw#text is the only place allowed to call it, and it cannot be called without a width. This
# is what stops the next call site opting out. Runs in the pre-commit hook and in verify.sh.

set -u
cd "$(dirname "$0")/.." || exit 1

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

To commit anyway: git commit --no-verify
MSG
  exit 1
fi

exit 0
