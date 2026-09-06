package com.neryos.workbay.client.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Where the flow map's boxes go and how its arrows get there. No drawing, no Minecraft.
 *
 * <p>This is the <b>layered algorithm</b> — Sugiyama, Tagawa and Toda, and the same five phases
 * Eclipse ELK, Graphviz's {@code dot} and every flow editor run: <b>break cycles, assign layers,
 * minimise crossings, place nodes, route edges</b>. It replaces a force-directed relaxation, which
 * is a method for graphs that have no direction and was the wrong tool: it left arrows lying on top
 * of arrows, because nothing in it has any idea what an arrow is.
 *
 * <p>Two of those phases are what stop lines overlapping, and both were missing before.
 * <b>Ports</b>: several edges leaving or arriving at one box get their own point on that box's
 * side, spread along it and ordered by where the far end sits, so they neither share a pixel nor
 * cross each other on the way in. <b>Tracks</b>: the gap between two layers is a channel, every
 * vertical run in it gets its own track, and the channel widens to hold them. Long edges get the
 * standard dummy chain, so an edge crossing a layer is routed around that layer's boxes rather
 * than through them.
 */
final class FlowLayout {

    /** Every box is this tall; only the width is the caller's business. */
    static final int NODE_H = 18;
    /** Clear air between two boxes in the same layer, and between two tracks in a channel. */
    private static final int ROW_GAP = 8;
    private static final int TRACK_STEP = 6;
    private static final int CHANNEL_MIN = 26;
    /** A dummy is a point, so a long edge crossing a layer costs a sliver rather than a row. */
    private static final int DUMMY_H = 2;

    private final int nodeW;

    /** Per real node, in the caller's own order. */
    final int[] nodeX;
    final int[] nodeY;

    /** Per edge, the polyline it is drawn along, already running source to target. */
    final List<int[]> edgeXs = new ArrayList<>();
    final List<int[]> edgeYs = new ArrayList<>();

    final int width;
    final int height;

    // Vertices are the real nodes first, then one dummy per layer a long edge crosses.
    private final int[] layer;
    private final int[] vy;
    private final int[] height1;
    private final List<List<Integer>> layers = new ArrayList<>();

    /**
     * @param count how many real boxes there are
     * @param wires one {@code {from, to}} per edge, in the caller's own order
     */
    FlowLayout(int count, List<int[]> wires, int nodeW) {
        this.nodeW = nodeW;
        this.nodeX = new int[count];
        this.nodeY = new int[count];

        // ---------------------------------------------------------------- 1. break the cycles
        // Two bays feeding each other is a cycle, and every later phase needs a graph without one.
        // The standard answer is to reverse an edge rather than drop it: the layout is done on the
        // reversed version and the arrow is drawn pointing the way it really goes.
        boolean[] flipped = new boolean[wires.size()];
        breakCycles(count, wires, flipped);

        // ---------------------------------------------------------------- 2. assign the layers
        // Longest path: a box sits one layer after everything that feeds it.
        this.layer = new int[count];
        for (int pass = 0; pass < count; pass++) {
            boolean moved = false;
            for (int e = 0; e < wires.size(); e++) {
                int from = source(wires.get(e), flipped[e]);
                int to = target(wires.get(e), flipped[e]);
                if (layer[to] <= layer[from]) {
                    layer[to] = layer[from] + 1;
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        int layerCount = 1;
        for (int l : layer) {
            layerCount = Math.max(layerCount, l + 1);
        }

        // ------------------------------------------------- the dummy chains for long edges
        List<int[]> chains = new ArrayList<>();
        List<Integer> dummyLayer = new ArrayList<>();
        for (int e = 0; e < wires.size(); e++) {
            int from = source(wires.get(e), flipped[e]);
            int to = target(wires.get(e), flipped[e]);
            List<Integer> chain = new ArrayList<>();
            chain.add(from);
            for (int l = layer[from] + 1; l < layer[to]; l++) {
                chain.add(count + dummyLayer.size());
                dummyLayer.add(l);
            }
            chain.add(to);
            chains.add(chain.stream().mapToInt(Integer::intValue).toArray());
        }
        int total = count + dummyLayer.size();
        int[] vertexLayer = new int[total];
        System.arraycopy(layer, 0, vertexLayer, 0, count);
        for (int d = 0; d < dummyLayer.size(); d++) {
            vertexLayer[count + d] = dummyLayer.get(d);
        }
        this.height1 = new int[total];
        Arrays.fill(height1, DUMMY_H);
        Arrays.fill(height1, 0, count, NODE_H);

        for (int l = 0; l < layerCount; l++) {
            layers.add(new ArrayList<>());
        }
        for (int v = 0; v < total; v++) {
            layers.get(vertexLayer[v]).add(v);
        }

        // ---------------------------------------------------------- 3. minimise the crossings
        // The median heuristic, swept down and up four times and the best ordering kept. It is
        // what dot uses, and it is the phase that decides whether the picture is readable at all.
        List<List<int[]>> segments = new ArrayList<>();
        for (int l = 0; l < layerCount; l++) {
            segments.add(new ArrayList<>());
        }
        for (int[] chain : chains) {
            for (int i = 0; i + 1 < chain.length; i++) {
                segments.get(vertexLayer[chain[i]]).add(new int[] {chain[i], chain[i + 1]});
            }
        }
        reorder(segments, layerCount);

        // ------------------------------------------------------------------ 4. place the nodes
        this.vy = new int[total];
        place(segments, layerCount);

        // ------------------------------------------------------------------ 5. route the edges
        int[] layerX = new int[layerCount];
        int[][] ports = ports(segments, layerCount, total);
        int[] base = new int[layerCount];
        for (int l = 1; l < layerCount; l++) {
            base[l] = base[l - 1] + segments.get(l - 1).size();
        }
        int[] channel = new int[Math.max(1, layerCount - 1)];
        for (int l = 0; l + 1 < layerCount; l++) {
            // As wide as the number of vertical runs it has to hold, so no two share an x. A
            // segment whose two ports are already level needs no run and no track.
            int tracks = 0;
            for (int i = 0; i < segments.get(l).size(); i++) {
                if (ports[0][base[l] + i] != ports[1][base[l] + i]) {
                    tracks++;
                }
            }
            channel[l] = Math.max(CHANNEL_MIN, (tracks + 1) * TRACK_STEP);
        }
        for (int l = 1; l < layerCount; l++) {
            layerX[l] = layerX[l - 1] + nodeW + channel[l - 1];
        }
        for (int v = 0; v < count; v++) {
            nodeX[v] = layerX[layer[v]];
            nodeY[v] = vy[v];
        }
        route(chains, flipped, segments, layerX, channel, vertexLayer, ports, base);

        int right = 0;
        int bottom = 0;
        for (int v = 0; v < count; v++) {
            right = Math.max(right, nodeX[v] + nodeW);
            bottom = Math.max(bottom, nodeY[v] + NODE_H);
        }
        for (int[] xs : edgeXs) {
            for (int px : xs) {
                right = Math.max(right, px);
            }
        }
        for (int[] ys : edgeYs) {
            for (int py : ys) {
                bottom = Math.max(bottom, py);
            }
        }
        this.width = Math.max(1, right);
        this.height = Math.max(1, bottom);
    }

    // ------------------------------------------------------------------------------- phase 1

    private static void breakCycles(int count, List<int[]> wires, boolean[] flipped) {
        int[] state = new int[count];
        for (int start = 0; start < count; start++) {
            if (state[start] == 0) {
                visit(start, count, wires, flipped, state);
            }
        }
    }

    /** Depth-first, marking a back edge as one to reverse. Iterative would not be shorter. */
    private static void visit(int at, int count, List<int[]> wires, boolean[] flipped, int[] state) {
        state[at] = 1;
        for (int e = 0; e < wires.size(); e++) {
            if (flipped[e] || wires.get(e)[0] != at) {
                continue;
            }
            int to = wires.get(e)[1];
            if (state[to] == 1) {
                flipped[e] = true;
            } else if (state[to] == 0) {
                visit(to, count, wires, flipped, state);
            }
        }
        state[at] = 2;
    }

    private static int source(int[] wire, boolean flipped) {
        return flipped ? wire[1] : wire[0];
    }

    private static int target(int[] wire, boolean flipped) {
        return flipped ? wire[0] : wire[1];
    }

    // ------------------------------------------------------------------------------- phase 3

    private void reorder(List<List<int[]>> segments, int layerCount) {
        List<List<Integer>> best = copy();
        int fewest = crossings(segments, layerCount);
        for (int sweep = 0; sweep < 4; sweep++) {
            boolean down = sweep % 2 == 0;
            for (int step = 0; step < layerCount; step++) {
                int l = down ? step : layerCount - 1 - step;
                int from = down ? l - 1 : l + 1;
                if (from < 0 || from >= layerCount) {
                    continue;
                }
                double[] median = new double[layers.get(l).size()];
                List<Integer> here = layers.get(l);
                for (int i = 0; i < here.size(); i++) {
                    median[i] = median(here.get(i), down ? segments.get(from) : segments.get(l),
                        down, here, i);
                }
                Integer[] sorted = here.toArray(new Integer[0]);
                Double[] keys = new Double[sorted.length];
                for (int i = 0; i < sorted.length; i++) {
                    keys[i] = median[i];
                }
                Integer[] index = new Integer[sorted.length];
                for (int i = 0; i < index.length; i++) {
                    index[i] = i;
                }
                Arrays.sort(index, Comparator.comparingDouble(i -> keys[i]));
                List<Integer> next = new ArrayList<>();
                for (int i : index) {
                    next.add(sorted[i]);
                }
                layers.set(l, next);
            }
            int now = crossings(segments, layerCount);
            if (now < fewest) {
                fewest = now;
                best = copy();
            }
        }
        layers.clear();
        layers.addAll(best);
    }

    private List<List<Integer>> copy() {
        List<List<Integer>> out = new ArrayList<>();
        for (List<Integer> l : layers) {
            out.add(new ArrayList<>(l));
        }
        return out;
    }

    /**
     * The median position of a vertex's neighbours in the layer being swept from. A vertex with no
     * neighbours there keeps where it is, which is the rule that stops the heuristic shuffling
     * boxes it has no opinion about.
     */
    private double median(int vertex, List<int[]> across, boolean down, List<Integer> here,
        int at) {
        List<Integer> seen = new ArrayList<>();
        for (int[] seg : across) {
            if (down && seg[1] == vertex) {
                seen.add(indexIn(seg[0]));
            } else if (!down && seg[0] == vertex) {
                seen.add(indexIn(seg[1]));
            }
        }
        if (seen.isEmpty()) {
            return at;
        }
        seen.sort(Comparator.naturalOrder());
        return seen.size() % 2 == 1 ? seen.get(seen.size() / 2)
            : (seen.get(seen.size() / 2 - 1) + seen.get(seen.size() / 2)) / 2.0;
    }

    private int indexIn(int vertex) {
        for (List<Integer> l : layers) {
            int at = l.indexOf(vertex);
            if (at >= 0) {
                return at;
            }
        }
        return 0;
    }

    /** Every pair of segments between two layers that would cross, counted the obvious way. */
    private int crossings(List<List<int[]>> segments, int layerCount) {
        int total = 0;
        for (int l = 0; l + 1 < layerCount; l++) {
            List<int[]> here = segments.get(l);
            for (int a = 0; a < here.size(); a++) {
                for (int b = a + 1; b < here.size(); b++) {
                    int a0 = indexIn(here.get(a)[0]);
                    int a1 = indexIn(here.get(a)[1]);
                    int b0 = indexIn(here.get(b)[0]);
                    int b1 = indexIn(here.get(b)[1]);
                    if ((a0 - b0) * (a1 - b1) < 0) {
                        total++;
                    }
                }
            }
        }
        return total;
    }

    // ------------------------------------------------------------------------------- phase 4

    /**
     * Rows first, then four passes pulling every vertex towards the middle of what it is joined to
     * and pushing apart anything that then overlaps. Not Brandes and Koepf — that is the good
     * version — but the same idea, and on a graph of a dozen boxes the difference does not show.
     */
    private void place(List<List<int[]>> segments, int layerCount) {
        for (List<Integer> here : layers) {
            int at = 0;
            for (int v : here) {
                vy[v] = at;
                at += height1[v] + ROW_GAP;
            }
        }
        for (int pass = 0; pass < 4; pass++) {
            for (int l = 0; l < layerCount; l++) {
                for (int v : layers.get(l)) {
                    List<Integer> joined = new ArrayList<>();
                    for (int side = 0; side < layerCount; side++) {
                        for (int[] seg : segments.get(side)) {
                            if (seg[0] == v) {
                                joined.add(vy[seg[1]] + height1[seg[1]] / 2);
                            } else if (seg[1] == v) {
                                joined.add(vy[seg[0]] + height1[seg[0]] / 2);
                            }
                        }
                    }
                    if (joined.isEmpty()) {
                        continue;
                    }
                    joined.sort(Comparator.naturalOrder());
                    int mid = joined.size() % 2 == 1 ? joined.get(joined.size() / 2)
                        : (joined.get(joined.size() / 2 - 1) + joined.get(joined.size() / 2)) / 2;
                    vy[v] = mid - height1[v] / 2;
                }
                spread(layers.get(l));
            }
        }
        int top = Integer.MAX_VALUE;
        for (List<Integer> here : layers) {
            for (int v : here) {
                top = Math.min(top, vy[v]);
            }
        }
        for (List<Integer> here : layers) {
            for (int v : here) {
                vy[v] -= top;
            }
        }
    }

    /** Keeps a layer in its decided order and never closer than the gap. */
    private void spread(List<Integer> here) {
        for (int i = 1; i < here.size(); i++) {
            int above = here.get(i - 1);
            int below = here.get(i);
            int least = vy[above] + height1[above] + ROW_GAP;
            if (vy[below] < least) {
                vy[below] = least;
            }
        }
    }

    // ------------------------------------------------------------------------------- phase 5

    /**
     * A point per edge on each side of a box, spread down that side and ordered by where the other
     * end of the edge sits. This is what stops three arrows arriving at one box on top of each
     * other, and what stops the three of them crossing on the way in.
     */
    private int[][] ports(List<List<int[]>> segments, int layerCount, int total) {
        int count = 0;
        for (List<int[]> here : segments) {
            count += here.size();
        }
        int[] out = new int[count];
        int[] in = new int[count];
        int at = 0;
        for (int l = 0; l < layerCount; l++) {
            List<int[]> here = segments.get(l);
            for (int side = 0; side < 2; side++) {
                for (int v = 0; v < total; v++) {
                    List<Integer> mine = new ArrayList<>();
                    for (int i = 0; i < here.size(); i++) {
                        if (here.get(i)[side] == v) {
                            mine.add(i);
                        }
                    }
                    if (mine.isEmpty()) {
                        continue;
                    }
                    int far = 1 - side;
                    mine.sort(Comparator.comparingInt(i -> vy[here.get(i)[far]]));
                    for (int slot = 0; slot < mine.size(); slot++) {
                        int y = height1[v] <= DUMMY_H ? vy[v] + height1[v] / 2
                            : vy[v] + (slot + 1) * height1[v] / (mine.size() + 1);
                        (side == 0 ? out : in)[at + mine.get(slot)] = y;
                    }
                }
            }
            at += here.size();
        }
        return new int[][] {out, in};
    }

    /**
     * The polyline for every edge: out of its port, along its own track in the channel, in at the
     * far port. A track per vertical run, so two runs in one channel never share an x — which is
     * the other half of the overlapping-arrows fix, and the reason a channel is as wide as it is.
     */
    private void route(List<int[]> chains, boolean[] flipped, List<List<int[]>> segments,
        int[] layerX, int[] channel, int[] vertexLayer, int[][] ports, int[] base) {
        int[][] track = new int[segments.size()][];
        for (int l = 0; l < segments.size(); l++) {
            List<int[]> here = segments.get(l);
            Integer[] index = new Integer[here.size()];
            for (int i = 0; i < index.length; i++) {
                index[i] = i;
            }
            final int layerBase = base[l];
            // Ordered by where the run starts, which is the metro-line rule: two runs in a channel
            // that keep their entry order cannot cross inside it.
            Arrays.sort(index, Comparator.comparingInt(i -> ports[0][layerBase + i]));
            track[l] = new int[here.size()];
            int next = 0;
            for (int i : index) {
                track[l][i] = ports[0][layerBase + i] == ports[1][layerBase + i] ? -1 : next++;
            }
        }

        for (int e = 0; e < chains.size(); e++) {
            int[] chain = chains.get(e);
            List<Integer> xs = new ArrayList<>();
            List<Integer> ys = new ArrayList<>();
            for (int i = 0; i + 1 < chain.length; i++) {
                int l = vertexLayer[chain[i]];
                List<int[]> here = segments.get(l);
                int seg = -1;
                for (int s = 0; s < here.size(); s++) {
                    if (here.get(s)[0] == chain[i] && here.get(s)[1] == chain[i + 1]) {
                        seg = s;
                        break;
                    }
                }
                int startY = ports[0][base[l] + seg];
                int endY = ports[1][base[l] + seg];
                int startX = layerX[l] + nodeW;
                int endX = layerX[l + 1];
                if (i > 0) {
                    // Across the dummy's own layer, so a long edge passes between the boxes there
                    // instead of over them.
                    add(xs, ys, layerX[l], startY);
                }
                add(xs, ys, startX, startY);
                if (track[l][seg] >= 0) {
                    int slot = layerX[l] + nodeW
                        + (track[l][seg] + 1) * channel[l] / (tracksIn(track[l]) + 1);
                    add(xs, ys, slot, startY);
                    add(xs, ys, slot, endY);
                }
                add(xs, ys, endX, endY);
            }
            int[] px = xs.stream().mapToInt(Integer::intValue).toArray();
            int[] py = ys.stream().mapToInt(Integer::intValue).toArray();
            if (flipped[e]) {
                // Laid out backwards to break a cycle, so it is drawn backwards too and the head
                // lands where the goods actually go.
                reverse(px);
                reverse(py);
            }
            edgeXs.add(px);
            edgeYs.add(py);
        }
    }

    private static int tracksIn(int[] track) {
        int most = 0;
        for (int t : track) {
            most = Math.max(most, t + 1);
        }
        return most;
    }

    private static void add(List<Integer> xs, List<Integer> ys, int px, int py) {
        if (!xs.isEmpty() && xs.get(xs.size() - 1) == px && ys.get(ys.size() - 1) == py) {
            return;
        }
        xs.add(px);
        ys.add(py);
    }

    private static void reverse(int[] values) {
        for (int i = 0, j = values.length - 1; i < j; i++, j--) {
            int swap = values[i];
            values[i] = values[j];
            values[j] = swap;
        }
    }
}
