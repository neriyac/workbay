package com.neryos.workbay.client.screen;

import com.neryos.workbay.bus.BusConfig;
import com.neryos.workbay.bus.BusRunner;
import com.neryos.workbay.menu.WorkbaySnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen 2 — the flow map. SPEC.md §4.
 *
 * <p><b>A canvas, not three columns.</b> Three fixed columns can draw one hop and no more: a chest
 * feeding a bay that feeds another bay that fills a barrel is four steps, and in three columns the
 * middle one has to be two of them at once. So the boxes are free objects relaxed into place, the
 * panel grows to whatever the flow needs, and a link that goes back the way it came is drawn
 * going back.
 *
 * <p>A <b>dashed blue</b> edge never leaves the Workbay; a solid one crosses the world boundary,
 * and stalled draws amber. Bay nodes carry the machine's own item, because an eighty-pixel node
 * cannot hold "Metallurgic Infuser" and the sprite is what a player recognises first.
 */
class FlowPage extends WorkbayPage {

    /** The panel is one size again. What used to grow is now the view onto something bigger. */
    private static final int WIDTH = 320;
    private static final int CANVAS_H = 190;

    private static final int MARGIN = 12;
    /** Room between two columns, which is all an arrow gets to say which way it points. */
    private static final int GAP = 22;
    private static final int NODE_H = 18;
    private static final int PITCH = 22;
    private static final int TOP_Y = 46;
    private static final int NODE_W = 100;

    /** One box: a bay of this Workbay, or a block somewhere that a link reaches. */
    private record Node(Component label, @Nullable ResourceLocation icon, boolean bay,
        int px, int py) {}

    /** One link, as the two boxes it joins. */
    private record Edge(int from, int to, int colour, int dash, boolean running) {}

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    private final int nodeW;
    private final int graphW;
    private final int graphH;

    /** What the view is doing right now: how far in, and where over the graph it is looking. */
    private float zoom = 1.0F;
    private float panX;
    private float panY;
    private boolean dragging;

    FlowPage(WorkbayScreen screen) {
        super(screen);
        WorkbaySnapshot snap = screen.snapshot();

        // ------------------------------------------------------------------ what is on the map
        // A key per box, so the same target reached by two links is one node with two edges into
        // it, which is the difference between a map and a list drawn sideways.
        Map<String, Integer> index = new HashMap<>();
        List<Component> labels = new ArrayList<>();
        List<ResourceLocation> icons = new ArrayList<>();
        List<Boolean> isBay = new ArrayList<>();
        List<int[]> wires = new ArrayList<>();
        List<int[]> style = new ArrayList<>();

        for (WorkbaySnapshot.Bay bay : snap.bays()) {
            if (bay.hosted().isPresent()) {
                index.put("bay:" + bay.index(), labels.size());
                labels.add(bayName(bay));
                icons.add(bay.hosted().orElse(null));
                isBay.add(true);
            }
        }
        for (WorkbaySnapshot.Link link : snap.links()) {
            Integer bayNode = index.get("bay:" + link.config().bay());
            if (bayNode == null) {
                continue;
            }
            boolean internal = isInternal(snap, link);
            // An internal link's far end is a bay, and it is the *same* bay node the middle column
            // already drew. That is what makes a chain a chain rather than the same machine drawn
            // twice under two different names.
            String key = internal ? "bay:" + link.targetBay().orElse(-1)
                : "at:" + link.config().target().pos().asLong();
            Integer far = index.get(key);
            if (far == null) {
                if (internal) {
                    continue;
                }
                far = labels.size();
                index.put(key, far);
                labels.add(targetName(link));
                // The target's own block, when this client happens to know it. A map of eight
                // boxes reading "Connector, Connector, Connector" is a map of one thing; the
                // sprite is what tells a barrel from a chest from a machine at a glance.
                icons.add(link.targetBlock()
                    .filter(id -> !id.equals(ResourceLocation.withDefaultNamespace("air")))
                    .orElse(null));
                isBay.add(false);
            }
            boolean insert = link.config().mode() == BusConfig.Mode.INSERT;
            wires.add(insert ? new int[] {bayNode, far} : new int[] {far, bayNode});
            style.add(new int[] {internal ? Draw.BLUE : colourFor(link.status()), internal ? 3 : 1,
                link.status() == BusRunner.BusStatus.RUNNING ? 1 : 0});
        }

        // ------------------------------------------------------------------ where each one goes
        // Free objects, not columns. A grid can only draw a flow exactly as long as the grid is
        // wide, and a Workbay's flows are not: a chest into a bay into another bay into a barrel is
        // four hops, and two bays feeding each other is a loop. So the boxes are relaxed into place
        // instead -- an edge pulls the two boxes it joins together, every box pushes every other
        // away, and one extra shove to the right along each edge keeps the map reading left to
        // right so the arrows still mean something.
        //
        // Seeded from a longest-path depth rather than from anything random, so the same Workbay
        // draws the same map every time it is opened. A screen that reshuffled itself on every
        // click would be worse than the grid it replaced.
        int n = labels.size();
        int[] depth = new int[n];
        for (int pass = 0; pass < n; pass++) {
            boolean moved = false;
            for (int[] wire : wires) {
                if (depth[wire[1]] <= depth[wire[0]]) {
                    depth[wire[1]] = depth[wire[0]] + 1;
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
        }
        this.nodeW = n > 12 ? 84 : NODE_W;
        double[] px = new double[n];
        double[] py = new double[n];
        int[] taken = new int[n + 1];
        for (int i = 0; i < n; i++) {
            px[i] = depth[i] * (nodeW + GAP);
            py[i] = taken[depth[i]]++ * PITCH;
        }
        relax(px, py, wires);
        separate(px, py);
        // Snapped to a row pitch afterwards, because an edge between two boxes on the same row is
        // a straight line and an edge between two boxes three pixels apart is a staircase. The
        // relaxation decides where things go; this decides that they line up when they nearly do.
        for (int i = 0; i < n; i++) {
            py[i] = Math.round(py[i] / PITCH) * (double) PITCH;
        }
        separate(px, py);
        // And then the empty rows are taken out. The relaxation leaves gaps wherever it pushed two
        // boxes apart and nothing settled in between, and every one of them is a band of panel that
        // costs height and says nothing.
        java.util.List<Long> used = new java.util.ArrayList<>(new java.util.TreeSet<>(
            java.util.Arrays.stream(py).mapToObj(Math::round).toList()));
        for (int i = 0; i < n; i++) {
            py[i] = used.indexOf(Math.round(py[i])) * (double) PITCH;
        }

        // ------------------------------------------------------------------ how big it all is
        double minX = 0;
        double minY = 0;
        double maxX = 0;
        double maxY = 0;
        for (int i = 0; i < n; i++) {
            minX = i == 0 ? px[i] : Math.min(minX, px[i]);
            minY = i == 0 ? py[i] : Math.min(minY, py[i]);
            maxX = i == 0 ? px[i] : Math.max(maxX, px[i]);
            maxY = i == 0 ? py[i] : Math.max(maxY, py[i]);
        }
        for (int i = 0; i < n; i++) {
            nodes.add(new Node(labels.get(i), icons.get(i), isBay.get(i),
                (int) Math.round(px[i] - minX), (int) Math.round(py[i] - minY)));
        }
        for (int i = 0; i < wires.size(); i++) {
            edges.add(new Edge(wires.get(i)[0], wires.get(i)[1], style.get(i)[0],
                style.get(i)[1], style.get(i)[2] == 1));
        }
        this.graphW = (int) Math.round(maxX - minX) + nodeW;
        this.graphH = (int) Math.round(maxY - minY) + NODE_H;

        // Opens showing all of it, however big it is, and never bigger than life size. Everything
        // after that is the player's: the wheel zooms about the pointer, a drag moves the view.
        // Eight pixels of air inside the frame, because a fit that touches the border reads as a
        // graph that has been cut off -- which is exactly what the first one was told it looked
        // like, and it was not even true.
        this.zoom = Math.clamp(Math.min((WIDTH - 2 * MARGIN - 8) / (float) Math.max(1, graphW),
            (CANVAS_H - 8) / (float) Math.max(1, graphH)), MIN_ZOOM, 1.0F);
        centre();
    }

    /**
     * Fruchterman-Reingold, two hundred passes with cooling, and one addition: every edge also
     * shoves its two ends apart along x, so the graph settles pointing the way it flows. Vertical
     * spread is damped because a screen has far less height to give than width.
     */
    private void relax(double[] px, double[] py, List<int[]> wires) {
        int n = px.length;
        double ideal = nodeW + GAP;
        for (int step = 0; step < 200; step++) {
            double heat = ideal * 0.12 * (1.0 - step / 200.0) + 0.5;
            double[] fx = new double[n];
            double[] fy = new double[n];
            for (int a = 0; a < n; a++) {
                for (int b = a + 1; b < n; b++) {
                    double dx = px[a] - px[b];
                    double dy = (py[a] - py[b]) * 2.0;
                    double d = Math.max(4.0, Math.sqrt(dx * dx + dy * dy));
                    double push = ideal * ideal / (d * d);
                    fx[a] += dx * push;
                    fy[a] += dy * push / 2.0;
                    fx[b] -= dx * push;
                    fy[b] -= dy * push / 2.0;
                }
            }
            for (int[] wire : wires) {
                double dx = px[wire[1]] - px[wire[0]];
                double dy = py[wire[1]] - py[wire[0]];
                double d = Math.max(4.0, Math.sqrt(dx * dx + dy * dy));
                double pull = d / ideal;
                fx[wire[0]] += dx * pull + ideal * 0.35;
                fy[wire[0]] += dy * pull;
                fx[wire[1]] -= dx * pull - ideal * 0.35;
                fy[wire[1]] -= dy * pull;
            }
            for (int i = 0; i < n; i++) {
                double d = Math.max(0.01, Math.sqrt(fx[i] * fx[i] + fy[i] * fy[i]));
                px[i] += fx[i] / d * Math.min(d, heat);
                py[i] += fy[i] / d * Math.min(d, heat);
            }
        }
    }

    /**
     * Whatever the relaxation left overlapping, prised apart along whichever axis needs it least.
     * Without this two boxes can settle on top of each other and the map has a name nobody can
     * read; with it the worst case is a map taller than it might have been.
     */
    private void separate(double[] px, double[] py) {
        int n = px.length;
        for (int pass = 0; pass < 60; pass++) {
            boolean clear = true;
            for (int a = 0; a < n; a++) {
                for (int b = a + 1; b < n; b++) {
                    double dx = px[b] - px[a];
                    double dy = py[b] - py[a];
                    double overX = nodeW + 8 - Math.abs(dx);
                    double overY = PITCH - Math.abs(dy);
                    if (overX <= 0 || overY <= 0) {
                        continue;
                    }
                    clear = false;
                    if (overY < overX * 0.25) {
                        double shift = (dy < 0 ? -overY : overY) / 2;
                        py[a] -= shift;
                        py[b] += shift;
                    } else {
                        double shift = (dx < 0 ? -overX : overX) / 2;
                        px[a] -= shift;
                        px[b] += shift;
                    }
                }
            }
            if (clear) {
                break;
            }
        }
    }

    /** Puts the whole graph in the middle of the view at the current zoom. */
    private void centre() {
        panX = ((WIDTH - 2 * MARGIN) / zoom - graphW) / 2;
        panY = (CANVAS_H / zoom - graphH) / 2;
    }

    private static final float MIN_ZOOM = 0.35F;
    private static final float MAX_ZOOM = 2.0F;

    @Override
    int width() {
        return WIDTH;
    }

    @Override
    int height() {
        // Canvas, then three legend rows, then the same bottom margin the top gets.
        return TOP_Y + CANVAS_H + 8 + 3 * 12 + 8;
    }

    private int viewLeft() {
        return x(MARGIN);
    }

    private int viewTop() {
        return y(TOP_Y);
    }

    private int viewW() {
        return WIDTH - 2 * MARGIN;
    }

    /** Graph x to screen x. Only the tooltips need it; everything drawn goes through the pose. */
    private int screenX(int gx) {
        return Math.round(viewLeft() + (gx + panX) * zoom);
    }

    private int screenY(int gy) {
        return Math.round(viewTop() + (gy + panY) * zoom);
    }

    private boolean inside(double mouseX, double mouseY) {
        return mouseX >= viewLeft() && mouseX < viewLeft() + viewW()
            && mouseY >= viewTop() && mouseY < viewTop() + CANVAS_H;
    }

    @Override
    void render(GuiGraphics g, int mouseX, int mouseY) {
        header(g, mouseX, mouseY, "FLOW");
        Draw.well(g, viewLeft(), viewTop(), viewW(), CANVAS_H);
        // Registered before the boxes, so a box hovered over wins the tooltip and the empty canvas
        // is what tells a player the view moves at all.
        screen.hit(viewLeft(), viewTop(), viewW(), CANVAS_H, () -> { },
            WorkbayScreen.gui("flow.canvas"), WorkbayScreen.gui("flow.canvas.tip"));
        if (nodes.isEmpty()) {
            text(g, WorkbayScreen.gui("flow.empty"), viewLeft() + 6, viewTop() + 8, viewW() - 12,
                Draw.TEXT_FAINT);
            legend(g);
            return;
        }

        // Clipped to the well, so a flow bigger than the view is cut off at its edge instead of
        // drawn over the legend and out across the world behind the screen.
        g.enableScissor(viewLeft() + 1, viewTop() + 1, viewLeft() + viewW() - 1,
            viewTop() + CANVAS_H - 1);
        g.pose().pushPose();
        g.pose().translate(viewLeft(), viewTop(), 0);
        g.pose().scale(zoom, zoom, 1.0F);
        g.pose().translate(panX, panY, 0);
        for (Edge edge : edges) {
            route(g, nodes.get(edge.from()), nodes.get(edge.to()), edge.colour(), edge.dash(),
                edge.running());
        }
        for (Node node : nodes) {
            node(g, node);
        }
        g.pose().popPose();
        g.disableScissor();

        // The tooltips live outside the pose, because the hit list is in screen pixels. Registered
        // only for what is actually on screen, or a box panned out of view would still answer.
        for (Node node : nodes) {
            int sx = screenX(node.px());
            int sy = screenY(node.py());
            int sw = Math.round(nodeW * zoom);
            int sh = Math.round(NODE_H * zoom);
            if (sx + sw > viewLeft() && sx < viewLeft() + viewW()
                && sy + sh > viewTop() && sy < viewTop() + CANVAS_H) {
                screen.hit(sx, sy, sw, sh, () -> { }, node.label());
            }
        }
        legend(g);
    }

    @Override
    boolean scrolled(double mouseX, double mouseY, double delta) {
        if (!inside(mouseX, mouseY)) {
            return false;
        }
        // About the pointer, not about the corner: zooming towards what you are looking at is the
        // whole difference between a canvas and a pair of sliders.
        float was = zoom;
        float now = Math.clamp(zoom * (delta > 0 ? 1.25F : 0.8F), MIN_ZOOM, MAX_ZOOM);
        if (now == was) {
            return true;
        }
        double overX = (mouseX - viewLeft()) / was - panX;
        double overY = (mouseY - viewTop()) / was - panY;
        zoom = now;
        panX = (float) ((mouseX - viewLeft()) / now - overX);
        panY = (float) ((mouseY - viewTop()) / now - overY);
        return true;
    }

    @Override
    boolean mousePressed(double mouseX, double mouseY, int button) {
        dragging = button == 0 && inside(mouseX, mouseY);
        return dragging;
    }

    @Override
    boolean mouseDragged(double dragX, double dragY) {
        if (!dragging) {
            return false;
        }
        panX += (float) (dragX / zoom);
        panY += (float) (dragY / zoom);
        // Never so far that the graph leaves the window entirely: a canvas you can lose is one a
        // player has to close and reopen to get back.
        panX = Math.clamp(panX, -graphW + 24 / zoom, viewW() / zoom - 24 / zoom);
        panY = Math.clamp(panY, -graphH + 12 / zoom, CANVAS_H / zoom - 12 / zoom);
        return true;
    }

    @Override
    boolean mouseReleased(double mouseX, double mouseY) {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    /**
     * One box. A bay is drawn in the panel's own light shade, with a blue cap on its left edge and
     * its machine's item; a block out in the world is drawn in a well with whatever item the client
     * knows it to be. The whole label goes in a tooltip, because at this width most machine names
     * are cut and the cut is where mod names usually differ.
     */
    private void node(GuiGraphics g, Node node) {
        int px = node.px();
        int py = node.py();
        rounded(g, px, py, nodeW, NODE_H, Draw.EDGE_DARK);
        rounded(g, px + 1, py + 1, nodeW - 2, NODE_H - 2,
            node.bay() ? Draw.PANEL_LIGHT : Draw.WELL);
        g.fill(px + 3, py + 1, px + nodeW - 3, py + 2, 0x22FFFFFF);
        g.fill(px + 3, py + NODE_H - 2, px + nodeW - 3, py + NODE_H - 1, 0x33000000);
        if (node.bay()) {
            g.fill(px + 1, py + 3, px + 3, py + NODE_H - 3, Draw.BLUE);
        }
        int textX = px + 5;
        if (node.icon() != null) {
            var item = BuiltInRegistries.ITEM.get(node.icon());
            if (item != Items.AIR) {
                g.pose().pushPose();
                g.pose().translate(px + (node.bay() ? 5 : 4), py + 3, 0);
                g.pose().scale(0.75F, 0.75F, 1.0F);
                g.renderItem(new ItemStack(item), 0, 0);
                g.pose().popPose();
                textX = px + (node.bay() ? 19 : 18);
            }
        }
        text(g, node.label(), textX, py + 5, px + nodeW - 4 - textX, Draw.TEXT_DIM);
    }

    /**
     * One edge, routed the way a flow editor routes one: out of a side of the box, along right
     * angles, into a side of the other, <b>with as few bends as the two positions allow</b>.
     *
     * <p>None when the boxes line up, one otherwise. The first attempt always drew two, turning
     * halfway and turning back, which put a kink in edges that had a clear straight run and gave a
     * box directly above another one a staircase. Bends are what a reader has to follow, so the
     * only ones worth drawing are the ones the geometry forces.
     *
     * <p>The single bend also does the converging: everything arriving at a box turns onto that
     * box's own centre line and comes in along it, so three chests feeding one bay share the last
     * leg and meet it at one point instead of at three angles.
     *
     * @param dash    1 for a flow that crosses the world boundary, 3 for one that never leaves
     * @param running true when the link moved something on its last turn, which is the only claim
     *                the animation makes: a still line is a link resting or stuck, and both of
     *                those already have their own colour.
     */
    private void route(GuiGraphics g, Node from, Node to, int colour, int dash, boolean running) {
        int fcx = from.px() + nodeW / 2;
        int fcy = from.py() + NODE_H / 2;
        int tcx = to.px() + nodeW / 2;
        int tcy = to.py() + NODE_H / 2;
        boolean rightwards = tcx > fcx;
        boolean downwards = tcy > fcy;
        int[] xs;
        int[] ys;
        if (fcy == tcy) {
            xs = new int[] {rightwards ? from.px() + nodeW : from.px(),
                rightwards ? to.px() : to.px() + nodeW};
            ys = new int[] {fcy, fcy};
        } else if (fcx == tcx) {
            xs = new int[] {fcx, fcx};
            ys = new int[] {downwards ? from.py() + NODE_H : from.py(),
                downwards ? to.py() : to.py() + NODE_H};
        } else if (Math.abs(tcx - fcx) >= Math.abs(tcy - fcy)) {
            xs = new int[] {rightwards ? from.px() + nodeW : from.px(), tcx, tcx};
            ys = new int[] {fcy, fcy, downwards ? to.py() : to.py() + NODE_H};
        } else {
            xs = new int[] {fcx, fcx, rightwards ? to.px() : to.px() + nodeW};
            ys = new int[] {downwards ? from.py() + NODE_H : from.py(), tcy, tcy};
        }
        for (int leg = 0; leg + 1 < xs.length; leg++) {
            line(g, xs[leg], ys[leg], xs[leg + 1], ys[leg + 1], colour, dash);
        }
        int last = xs.length - 1;
        head(g, xs[last], ys[last], Integer.signum(xs[last] - xs[last - 1]),
            Integer.signum(ys[last] - ys[last - 1]), colour);
        if (running) {
            travel(g, xs, ys);
        }
    }

    /**
     * A solid triangle, five long and five across at the base, pointing along one of the four
     * directions a right-angled route can arrive from. The first attempt drew a pair of stacked
     * rectangles at whatever angle the line ran, which at one pixel wide is a smudge.
     */
    private static void head(GuiGraphics g, int px, int py, int dx, int dy, int colour) {
        for (int step = 0; step < 5; step++) {
            int spread = (4 - step) / 2;
            if (dx > 0) {
                g.fill(px - 5 + step, py - spread, px - 4 + step, py + spread + 1, colour);
            } else if (dx < 0) {
                g.fill(px + 4 - step, py - spread, px + 5 - step, py + spread + 1, colour);
            } else if (dy > 0) {
                g.fill(px - spread, py - 5 + step, px + spread + 1, py - 4 + step, colour);
            } else {
                g.fill(px - spread, py + 4 - step, px + spread + 1, py + 5 - step, colour);
            }
        }
    }

    /** Three pips walking the route, so a link that is carrying something looks like it is. */
    private void travel(GuiGraphics g, int[] xs, int[] ys) {
        double total = 0;
        double[] leg = new double[xs.length - 1];
        for (int i = 0; i < leg.length; i++) {
            leg[i] = Math.abs(xs[i + 1] - xs[i]) + Math.abs(ys[i + 1] - ys[i]);
            total += leg[i];
        }
        if (total <= 0) {
            return;
        }
        long now = net.minecraft.Util.getMillis();
        for (int pip = 0; pip < 3; pip++) {
            double walked = (now / 9.0 + pip * total / 3) % total;
            for (int i = 0; i < leg.length; i++) {
                if (walked > leg[i]) {
                    walked -= leg[i];
                    continue;
                }
                double along = leg[i] == 0 ? 0 : walked / leg[i];
                int mx = (int) Math.round(xs[i] + (xs[i + 1] - xs[i]) * along);
                int my = (int) Math.round(ys[i] + (ys[i + 1] - ys[i]) * along);
                g.fill(mx - 1, my - 1, mx + 2, my + 2, Draw.GREEN);
                break;
            }
        }
    }

    /**
     * A rectangle with its four corner pixels taken off. Two pixels of radius is all a
     * seventeen-pixel-tall box can carry without the corner eating the text, and it is the whole
     * difference between a box drawn by a program and one drawn on purpose.
     */
    private static void rounded(GuiGraphics g, int px, int py, int w, int h, int colour) {
        g.fill(px + 2, py, px + w - 2, py + h, colour);
        g.fill(px, py + 2, px + 2, py + h - 2, colour);
        g.fill(px + w - 2, py + 2, px + w, py + h - 2, colour);
        g.fill(px + 1, py + 1, px + 2, py + 2, colour);
        g.fill(px + w - 2, py + 1, px + w - 1, py + 2, colour);
        g.fill(px + 1, py + h - 2, px + 2, py + h - 1, colour);
        g.fill(px + w - 2, py + h - 2, px + w - 1, py + h - 1, colour);
    }

    /**
     * @param dash 1 draws solid; anything higher skips two pixels in every {@code dash}, which is
     *             what tells a bay-to-bay flow apart from one that leaves the block
     */
    private void line(GuiGraphics g, int x1, int y1, int x2, int y2, int colour, int dash) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            if (dash > 1 && (i / 2) % dash != 0) {
                continue;
            }
            int px = x1 + (x2 - x1) * i / Math.max(1, steps);
            int py = y1 + (y2 - y1) * i / Math.max(1, steps);
            g.fill(px, py, px + 1, py + 1, colour);
        }
    }

    /**
     * Six keys in three rows: two for what a line's shape means and four for what its colour does.
     *
     * <p>The colours were the half that was missing. Half the lines on a resting base are grey, and
     * a grey line with nothing saying what grey is reads as a line the screen forgot to finish.
     */
    private void legend(GuiGraphics g) {
        int py = y(TOP_Y + CANVAS_H + 8);
        int right = WIDTH / 2 + 4;
        int leftRoom = right - MARGIN - 20 - 6;
        int rightRoom = WIDTH - right - 20 - MARGIN;

        rule(g, x(MARGIN), py, Draw.TEXT_DIM, 1);
        text(g, WorkbayScreen.gui("flow.legend.out"), x(MARGIN + 20), py, leftRoom, Draw.TEXT_DIM);
        rule(g, x(MARGIN), py + 12, Draw.BLUE, 3);
        text(g, WorkbayScreen.gui("flow.legend.internal"), x(MARGIN + 20), py + 12, leftRoom,
            Draw.TEXT_DIM);
        rule(g, x(MARGIN), py + 24, Draw.GREEN, 2);
        text(g, WorkbayScreen.gui("flow.legend.live"), x(MARGIN + 20), py + 24, leftRoom,
            Draw.TEXT_DIM);

        rule(g, x(right), py, Draw.EDGE_LIGHT, 1);
        text(g, WorkbayScreen.gui("flow.legend.resting"), x(right + 20), py, rightRoom,
            Draw.TEXT_DIM);
        rule(g, x(right), py + 12, Draw.AMBER, 1);
        text(g, WorkbayScreen.gui("flow.legend.stalled"), x(right + 20), py + 12, rightRoom,
            Draw.TEXT_DIM);
        rule(g, x(right), py + 24, Draw.RED, 1);
        text(g, WorkbayScreen.gui("flow.legend.broken"), x(right + 20), py + 24, rightRoom,
            Draw.TEXT_DIM);
    }

    /** A sixteen-pixel sample of a line, drawn the way the map draws that line. */
    private void rule(GuiGraphics g, int px, int py, int colour, int dash) {
        for (int i = 0; i < 16; i++) {
            if (dash > 1 && (i / 2) % dash != 0) {
                continue;
            }
            g.fill(px + i, py + 3, px + i + 1, py + 4, colour);
        }
    }

    /**
     * A link is internal when its target is in the Backshop — which in v1 can only be another bay
     * of this Workbay.
     */
    private static boolean isInternal(WorkbaySnapshot snap, WorkbaySnapshot.Link link) {
        return link.config().target().dimension().equals(
            com.neryos.workbay.world.WorkbayDimensions.BACKSHOP);
    }

    private static int colourFor(BusRunner.BusStatus status) {
        return switch (status) {
            case RUNNING -> Draw.GREEN;
            case IDLE -> Draw.EDGE_LIGHT;
            case DISABLED, HELD_BY_REDSTONE -> Draw.GREY;
            case TARGET_MISSING, CONNECTOR_GONE -> Draw.RED;
            case TARGET_NOT_LOADED, TARGET_NO_PORT, MACHINE_NO_PORT, MACHINE_NO_FACE -> Draw.AMBER;
        };
    }

    /**
     * What a box out in the world is called: the name the player gave the link, then the target
     * block's own name, then — when this client cannot know it — <b>where</b> it is. Five nodes all
     * reading "not loaded" is a map of one place.
     */
    private static Component targetName(WorkbaySnapshot.Link link) {
        if (link.label().isPresent()) {
            return Component.literal(link.label().get());
        }
        return link.targetBlock()
            .filter(id -> !id.equals(ResourceLocation.withDefaultNamespace("air")))
            .map(FlowPage::name)
            .orElseGet(() -> Component.literal(link.config().target().pos().getX() + " "
                + link.config().target().pos().getZ()));
    }

    /** The name the player gave the bay, or the machine's own. */
    private static Component bayName(WorkbaySnapshot.Bay bay) {
        return bay.name().isBlank() ? name(bay.hosted().orElse(null))
            : Component.literal(bay.name());
    }

    private static Component name(ResourceLocation id) {
        if (id == null) {
            return WorkbayScreen.gui("flow.unknown");
        }
        var block = BuiltInRegistries.BLOCK.get(id);
        if (block != net.minecraft.world.level.block.Blocks.AIR) {
            return block.getName();
        }
        var item = BuiltInRegistries.ITEM.get(id);
        return item != Items.AIR ? item.getDescription() : Component.literal(id.getPath());
    }
}
