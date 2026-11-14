package de.leon.survivalclient.client.render;

import de.leon.survivalclient.client.gui.ProVisualsSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Local-world block highlighting. Intentionally disabled on multiplayer servers:
 * the renderer never scans or reveals blocks in a remote multiplayer world.
 */
public final class ProVisualsWorldRenderer {
    private static final int CHEST_RANGE = 16;
    private static final int ORE_RANGE = 20;
    private static final int CHEST_COLOR = 0xFFB77CFF;
    private static final int UPDATE_INTERVAL_TICKS = 10;

    private static List<Highlight> highlights = List.of();
    private static int ticksUntilScan;

    private record Highlight(BlockPos pos, int color) {}

    private ProVisualsWorldRenderer() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ProVisualsWorldRenderer::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(ProVisualsWorldRenderer::render);
    }

    private static void tick(Minecraft client) {
        if (!client.isSingleplayer() || client.level == null || client.player == null
                || (!ProVisualsSettings.chestEsp && !ProVisualsSettings.xray)) {
            highlights = List.of();
            ticksUntilScan = 0;
            return;
        }

        if (ticksUntilScan-- > 0) {
            return;
        }
        ticksUntilScan = UPDATE_INTERVAL_TICKS;

        int range = Math.max(ProVisualsSettings.chestEsp ? CHEST_RANGE : 0,
                ProVisualsSettings.xray ? ORE_RANGE : 0);
        BlockPos playerPos = client.player.blockPosition();
        List<Highlight> found = new ArrayList<>();

        for (int x = playerPos.getX() - range; x <= playerPos.getX() + range; x++) {
            for (int y = Math.max(client.level.getMinY(), playerPos.getY() - range);
                 y <= Math.min(client.level.getMaxY(), playerPos.getY() + range); y++) {
                for (int z = playerPos.getZ() - range; z <= playerPos.getZ() + range; z++) {
                    double dx = x + 0.5 - playerPos.getX();
                    double dy = y + 0.5 - playerPos.getY();
                    double dz = z + 0.5 - playerPos.getZ();
                    if (dx * dx + dy * dy + dz * dz > (double) range * range) {
                        continue;
                    }

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = client.level.getBlockState(pos);
                    int color = 0;
                    if (ProVisualsSettings.chestEsp && isContainer(state)) {
                        color = CHEST_COLOR;
                    } else if (ProVisualsSettings.xray) {
                        color = oreColor(state);
                    }
                    if (color != 0) {
                        found.add(new Highlight(pos, color));
                    }
                }
            }
        }
        highlights = List.copyOf(found);
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSingleplayer() || client.level == null || client.player == null
                || highlights.isEmpty()) {
            return;
        }

        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();
        for (Highlight highlight : highlights) {
            BlockState state = client.level.getBlockState(highlight.pos());
            if (state.isAir()) {
                continue;
            }
            VoxelShape shape = state.getShape(client.level, highlight.pos());
            if (shape.isEmpty()) {
                continue;
            }

            context.poseStack().pushPose();
            context.poseStack().translate(
                    highlight.pos().getX() - cameraPos.x,
                    highlight.pos().getY() - cameraPos.y,
                    highlight.pos().getZ() - cameraPos.z
            );
            context.submitNodeCollector().submitShapeOutline(
                    context.poseStack(), shape, RenderTypes.lines(), highlight.color(), 2.0f, true
            );
            context.poseStack().popPose();
        }
    }

    private static boolean isContainer(BlockState state) {
        return state.is(Blocks.CHEST)
                || state.is(Blocks.TRAPPED_CHEST)
                || state.is(Blocks.ENDER_CHEST)
                || state.is(Blocks.BARREL)
                || state.is(Blocks.SHULKER_BOX)
                || state.is(Blocks.WHITE_SHULKER_BOX)
                || state.is(Blocks.ORANGE_SHULKER_BOX)
                || state.is(Blocks.MAGENTA_SHULKER_BOX)
                || state.is(Blocks.LIGHT_BLUE_SHULKER_BOX)
                || state.is(Blocks.YELLOW_SHULKER_BOX)
                || state.is(Blocks.LIME_SHULKER_BOX)
                || state.is(Blocks.PINK_SHULKER_BOX)
                || state.is(Blocks.GRAY_SHULKER_BOX)
                || state.is(Blocks.LIGHT_GRAY_SHULKER_BOX)
                || state.is(Blocks.CYAN_SHULKER_BOX)
                || state.is(Blocks.PURPLE_SHULKER_BOX)
                || state.is(Blocks.BLUE_SHULKER_BOX)
                || state.is(Blocks.BROWN_SHULKER_BOX)
                || state.is(Blocks.GREEN_SHULKER_BOX)
                || state.is(Blocks.RED_SHULKER_BOX)
                || state.is(Blocks.BLACK_SHULKER_BOX)
                || state.is(Blocks.DROPPER)
                || state.is(Blocks.DISPENSER)
                || state.is(Blocks.HOPPER)
                || state.is(Blocks.CRAFTER)
                || state.is(Blocks.FURNACE)
                || state.is(Blocks.BLAST_FURNACE)
                || state.is(Blocks.SMOKER);
    }

    private static int oreColor(BlockState state) {
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) return 0xFF55FFFF;
        if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) return 0xFF55FF77;
        if (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE) || state.is(Blocks.NETHER_GOLD_ORE)) return 0xFFFFD34E;
        if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) return 0xFFFFA66B;
        if (state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)) return 0xFFFF8B55;
        if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) return 0xFF557DFF;
        if (state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) return 0xFFFF5555;
        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) return 0xFFAAAAAA;
        if (state.is(Blocks.ANCIENT_DEBRIS)) return 0xFFB77CFF;
        return 0;
    }
}
