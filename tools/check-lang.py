#!/usr/bin/env python3
"""A key with no translation, made impossible to ship rather than seen on a screenshot.

`gui.workbay.links.type.chemical` shipped. Every other resource had a line and the fourth did not,
because a chemical only exists when Mekanism is installed and nothing here runs without it by
accident. What the player gets is the key itself, in bold, as the title of a tooltip.

Two halves, because keys are built two ways.

* The literal ones -- `gui("bay.empty")` -- are read straight out of the source.
* The rest are a prefix plus an enum: `gui("links.type." + resource.getSerializedName())`. Those
  prefixes are listed in BUILT below with the enum that feeds them, and the enum's constants are
  read out of its own source file.

**The enums are read, never retyped.** A first draft of this listed the suffixes by hand, got
RoomColour wrong -- it reported six colours missing that all exist -- and then passed a deliberately
broken lang file, because its greps assumed one constant per line at four spaces and every enum
that matters here is nested at eight with three constants to a line. A check that cannot fail is
worse than no check, so this one is run against a broken file every time it changes.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LANG = ROOT / "src/generated/resources/assets/workbay/lang/en_us.json"
SRC = ROOT / "src/main/java/com/neryos/workbay"

# prefix -> (file, enum name, "name" for name().toLowerCase() or "serial" for getSerializedName())
BUILT = {
    "gui.workbay.links.type.": ("bus/BusConfig.java", "Resource", "serial"),
    "gui.workbay.links.mode.": ("bus/BusConfig.java", "Mode", "serial"),
    "gui.workbay.status.": ("bus/BusRunner.java", "BusStatus", "name"),
    "gui.workbay.status.short.": ("bus/BusRunner.java", "BusStatus", "name"),
    "gui.workbay.bay.": ("menu/WorkbaySnapshot.java", "State", "name"),
    "gui.workbay.bay.short.": ("menu/WorkbaySnapshot.java", "State", "name"),
    "gui.workbay.redstone.": ("world/RedstoneMode.java", "RedstoneMode", "serial"),
    "gui.workbay.redstone.short.": ("world/RedstoneMode.java", "RedstoneMode", "serial"),
    "gui.workbay.colour.": ("content/room/RoomColour.java", "RoomColour", "serial"),
}

# Direction is vanilla's, and "any" is this mod's word for a face nobody pinned.
BUILT_LITERAL = {
    "gui.workbay.links.face.": "up down north south east west any".split(),
}


def constants(rel, enum, style):
    """One enum's constants, however they are laid out.

    The body is everything between `enum <Name>` and the first `;` that is not inside a string,
    which is where a Java enum's constant list has to end. Inside it a constant is an
    ALL_CAPS identifier, optionally followed by its serialized name in brackets.
    """
    text = (SRC / rel).read_text(encoding="utf-8")
    at = re.search(r"\benum\s+%s\b" % enum, text)
    if not at:
        sys.exit("check-lang: no enum %s in %s" % (enum, rel))
    body = text[text.index("{", at.end()) + 1:]
    # Comments go first, and the order is not a detail. A javadoc between two constants otherwise
    # contributes OPEN_ISSUES as a constant with no serialized name, and the `}` closing a
    # {@link #step} otherwise ends the constant list two constants early.
    body = re.sub(r"/\*.*?\*/", " ", body, flags=re.S)
    body = re.sub(r"//.*", " ", body)
    # The list ends at the first `;` -- or at the closing `}` when there is nothing after it and
    # Java lets the semicolon go, which is what WorkbaySnapshot.State does. Reading past the end
    # picked CODEC, INT and STRING out of the stream codecs below it.
    body = body[:min(x for x in (body.find(";"), body.find("}")) if x >= 0)]
    out = []
    for name, serial in re.findall(r"\b([A-Z][A-Z_0-9]{2,})\b(?:\(\s*\"([a-z_]+)\")?", body):
        out.append(serial if style == "serial" else name.lower())
    if not out:
        sys.exit("check-lang: read no constants out of %s.%s" % (rel, enum))
    if style == "serial" and not all(out):
        sys.exit("check-lang: %s.%s has a constant with no serialized name" % (rel, enum))
    return out


def wanted():
    keys = set()
    literal = re.compile(r'\b(gui|tooltip|message|status|info)\("([A-Za-z0-9_.]+)"\)')
    for java in SRC.rglob("*.java"):
        for kind, path in literal.findall(java.read_text(encoding="utf-8")):
            keys.add("%s.workbay.%s" % (kind, path))
    for prefix, (rel, enum, style) in BUILT.items():
        keys.update(prefix + suffix for suffix in constants(rel, enum, style))
    for prefix, suffixes in BUILT_LITERAL.items():
        keys.update(prefix + suffix for suffix in suffixes)
    return keys


def main():
    if not LANG.exists():
        sys.exit("No generated lang file. Run ./gradlew runData.")
    have = set(re.findall(r'"([a-z]+\.workbay\.[A-Za-z0-9_.]+)"\s*:', LANG.read_text(encoding="utf-8")))
    missing = sorted(wanted() - have)
    if missing:
        print("Keys with no line in %s:\n" % LANG.relative_to(ROOT))
        for key in missing:
            print("  " + key)
        print("\nAdd them in WBLanguageProvider and re-run ./gradlew runData. A key with no")
        print("translation is drawn as the key: the player reads gui.workbay.links.type.chemical.")
        print("\nTo commit anyway: git commit --no-verify")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
