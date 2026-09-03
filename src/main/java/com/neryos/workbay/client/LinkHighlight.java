package com.neryos.workbay.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.neryos.workbay.Workbay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The block a hovered LINKS row points at, outlined in the world. SPEC.md §7's first QOL item.
 *
 * <p>It is the only real answer to "which chest is this row?", which stops being a rhetorical
 * question at about the fourth link. LaserIO and XNet both do it.
 *
 * <p><b>Drawn without a depth test</b>, so a target behind the Workbay, a wall or the player's own
 * base is still findable — a highlight you can only see when you are already looking at the thing
 * is a highlight for the case that does not need one.
 *
 * <p>One static field rather than a per-screen listener: the screen sets it while it draws and the
 * world consumes it on the next frame, which is a frame of lag nobody can see and no plumbing.
 */
@EventBusSubscriber(modid = Workbay.MOD_ID, value = Dist.CLIENT)
public final class LinkHighlight {
    private LinkHighlight() {}

    @Nullable
    private static GlobalPos target;

    public static void set(GlobalPos at) {
        target = at;
    }

    /** Called every frame before the page draws, and when the screen closes. */
    public static void clear() {
        target = null;
    }

    @SubscribeEvent
    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        GlobalPos at = target;
        Minecraft minecraft = Minecraft.getInstance();
        // A link may point into another dimension once the Resonator is installed. There is
        // nothing to draw here then, and the row's own text already names where it is.
        if (at == null || minecraft.level == null
            || !minecraft.level.dimension().equals(at.dimension())) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        // Fractionally larger than the block, or the outline z-fights with the block's own faces.
        AABB box = new AABB(at.pos()).inflate(0.004);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);

        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(3.0F);
        BufferBuilder lines = Tesselator.getInstance()
            .begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        LevelRenderer.renderLineBox(pose, lines, box, 0.35F, 0.66F, 0.90F, 0.9F);
        BufferUploader.drawWithShader(lines.buildOrThrow());
        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();

        pose.popPose();
    }
}
