#!/usr/bin/env python3
"""Medians out of the profiler dumps ./gradlew runBenchmark leaves in run-gametest/perf/.

    tools/perf.py                 every scenario, its ten dearest sections
    tools/perf.py workbay hopper  only sections whose path contains one of these

One file is one recording window. Each scenario has five windows with the fixture built ("on")
and five with the same world and nothing of ours in it ("off"), and what is reported is the
difference: the gametest world is full of other tests' leftovers and they are in both halves.

The game writes percentages, not milliseconds, so the absolute figure is rebuilt here:
globalPercentage * nanoDuration / tickDuration. Medians across the five windows, with the
on-half's spread beside them, so a reader can see when a number should not be trusted.
"""
import re
import statistics
import sys
from pathlib import Path

PERF = Path(__file__).resolve().parent.parent / "run-gametest" / "perf"
LINE = re.compile(r"^\[(\d+)\] (?:\|   )*(.+?)\((\d+)/\d+\) - [\d.]+%/([\d.]+)%$")


def read(path):
    """One window: its spans, what it moved, and every section as path -> (calls, ms/tick)."""
    ticks = nanos = moved = 0
    stack, out = [], {}
    for line in path.read_text().splitlines():
        if line.startswith("# ticks "):
            ticks = int(line.split()[2])
        elif line.startswith("# nanos "):
            nanos = int(line.split()[2])
        elif line.startswith("# moved "):
            moved = int(line.split()[2])
        match = LINE.match(line)
        if not match:
            continue
        depth, name, count, share = match.groups()
        del stack[int(depth):]
        stack.append(name)
        if ticks:
            out["/".join(stack)] = (int(count) / ticks,
                                    float(share) / 100 * nanos / 1e6 / ticks)
    return ticks, nanos, moved, out


def medians(runs, index):
    """path -> median across the windows it appears in all of. Absent means not measurable."""
    paths = {p for *_, sections in runs for p in sections}
    out = {}
    for path in paths:
        samples = [s[path][index] for *_, s in runs if path in s]
        if len(samples) == len(runs):
            out[path] = (statistics.median(samples), min(samples), max(samples))
    return out


def main():
    wanted = [a.lower() for a in sys.argv[1:]]
    windows = {}
    for file in sorted(PERF.glob("*.txt")):
        label, _, repeat = file.stem.rpartition(".")
        if repeat.isdigit():
            windows.setdefault(label, []).append(read(file))

    for label, runs in sorted(windows.items()):
        if not label.endswith(".on"):
            continue
        off = windows.get(label[:-3] + ".off", [])
        ms, calls = medians(runs, 1), medians(runs, 0)
        offms, offcalls = medians(off, 1), medians(off, 0)
        moved = statistics.median(r[2] for r in runs)
        print(f"\n=== {label[:-3]}   {len(runs)} on / {len(off)} off windows, "
              f"{statistics.median(r[0] for r in runs):.0f} ticks each, "
              f"{moved:.0f} items moved per window")
        rows = [(ms[p][0] - offms.get(p, (0,))[0], ms[p], offms.get(p, (0, 0, 0))[0],
                 calls[p][0] - offcalls.get(p, (0,))[0], p) for p in ms]
        rows.sort(reverse=True)
        rows = [r for r in rows if not wanted or any(w in r[4].lower() for w in wanted)]
        print(f"{'delta':>9} {'on':>8} {'off':>8} {'on min':>8} {'on max':>8} "
              f"{'calls/t':>8}  section")
        for delta, (on, lo, hi), offv, dcalls, path in rows[:24 if wanted else 12]:
            print(f"{delta:9.4f} {on:8.4f} {offv:8.4f} {lo:8.4f} {hi:8.4f} "
                  f"{dcalls:8.1f}  {short(path)}")
        if moved:
            top = max(r[0] for r in rows) if rows else 0
            print(f"    per 1000 items moved: {top / moved * 1000 * 400:.3f} ms "
                  f"(the dearest section above, over a {400}-tick window)")


def short(path):
    """The tree is deep and the top of it is the same every time; the tail is the answer."""
    parts = path.split("/")
    return "/".join(parts[-4:]) if len(parts) > 4 else path


if __name__ == "__main__":
    main()
