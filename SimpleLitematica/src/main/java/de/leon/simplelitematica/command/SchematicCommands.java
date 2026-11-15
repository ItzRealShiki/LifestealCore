package de.leon.simplelitematica.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.leon.simplelitematica.data.SchematicData;
import de.leon.simplelitematica.data.SchematicFiles;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.util.*;

public final class SchematicCommands {
    private static final Map<UUID, BlockPos[]> SELECTIONS = new HashMap<>();
    private static final Deque<PrintBlock> PRINT_QUEUE = new ArrayDeque<>();
    private static int blocksPerTick = 4096;
    private static UUID printingPlayer;

    private SchematicCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("liteselect")
                    .then(Commands.literal("pos1").then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer())
                                    .then(Commands.argument("z", IntegerArgumentType.integer())
                                            .executes(context -> setCorner(context.getSource().getPlayerOrException(), 0,
                                                    new BlockPos(IntegerArgumentType.getInteger(context, "x"),
                                                            IntegerArgumentType.getInteger(context, "y"),
                                                            IntegerArgumentType.getInteger(context, "z"))))))))
                    .then(Commands.literal("pos2").then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("y", IntegerArgumentType.integer())
                                    .then(Commands.argument("z", IntegerArgumentType.integer())
                                            .executes(context -> setCorner(context.getSource().getPlayerOrException(), 1,
                                                    new BlockPos(IntegerArgumentType.getInteger(context, "x"),
                                                            IntegerArgumentType.getInteger(context, "y"),
                                                            IntegerArgumentType.getInteger(context, "z"))))))));
            dispatcher.register(Commands.literal("litesave")
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(context -> saveSelection(context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "name")))));
            dispatcher.register(Commands.literal("liteprint")
                    .executes(context -> startPrint(context.getSource().getPlayerOrException(), false))
                    .then(Commands.literal("slow")
                            .executes(context -> startPrint(context.getSource().getPlayerOrException(), true))));
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> tickPrint());
    }

    private static int setCorner(ServerPlayer player, int index, BlockPos position) {
        BlockPos[] corners = SELECTIONS.computeIfAbsent(player.getUUID(), ignored -> new BlockPos[2]);
        corners[index] = position;
        player.sendSystemMessage(Component.literal("SimpleLitematica: Pos" + (index + 1) + " = " + position.toShortString()));
        return 1;
    }

    private static int saveSelection(ServerPlayer player, String name) {
        BlockPos[] corners = SELECTIONS.get(player.getUUID());
        if (corners == null || corners[0] == null || corners[1] == null) {
            player.sendSystemMessage(Component.literal("Set both corners first with /liteselect pos1 and pos2."));
            return 0;
        }
        BlockPos min = new BlockPos(Math.min(corners[0].getX(), corners[1].getX()),
                Math.min(corners[0].getY(), corners[1].getY()), Math.min(corners[0].getZ(), corners[1].getZ()));
        BlockPos max = new BlockPos(Math.max(corners[0].getX(), corners[1].getX()),
                Math.max(corners[0].getY(), corners[1].getY()), Math.max(corners[0].getZ(), corners[1].getZ()));
        int sx = max.getX() - min.getX() + 1;
        int sy = max.getY() - min.getY() + 1;
        int sz = max.getZ() - min.getZ() + 1;
        long volume = (long) sx * sy * sz;
        if (volume > 100_000) {
            player.sendSystemMessage(Component.literal("Selection too large. Limit: 100,000 blocks."));
            return 0;
        }
        List<SchematicData.BlockEntry> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = player.level().getBlockState(pos);
            if (!state.isAir()) {
                blocks.add(new SchematicData.BlockEntry(pos.getX() - min.getX(), pos.getY() - min.getY(),
                        pos.getZ() - min.getZ(), state));
            }
        }
        try {
            SchematicFiles.save(name, new SchematicData(sx, sy, sz, blocks));
            player.sendSystemMessage(Component.literal("Saved " + blocks.size() + " blocks as " + name + ".slite"));
            return 1;
        } catch (IOException | RuntimeException exception) {
            player.sendSystemMessage(Component.literal("Could not save schematic: " + exception.getMessage()));
            return 0;
        }
    }

    private static int startPrint(ServerPlayer player, boolean slow) {
        if (!player.gameMode.isCreative()) {
            player.sendSystemMessage(Component.literal("Print mode is only available in Creative."));
            return 0;
        }
        if (!PRINT_QUEUE.isEmpty()) {
            player.sendSystemMessage(Component.literal("A schematic is already being printed."));
            return 0;
        }
        try {
            SchematicData schematic = SchematicFiles.loadActive();
            if (schematic.blocks().isEmpty()) {
                player.sendSystemMessage(Component.literal("The selected schematic contains no blocks."));
                return 0;
            }
            BlockPos origin = player.blockPosition();
            ServerLevel level = player.serverLevel();
            for (SchematicData.BlockEntry entry : schematic.blocks()) {
                PRINT_QUEUE.addLast(new PrintBlock(level, origin.offset(entry.x(), entry.y(), entry.z()), entry.state()));
            }
            blocksPerTick = slow ? 24 : 4096;
            printingPlayer = player.getUUID();
            player.sendSystemMessage(Component.literal("SimpleLitematica: Print started (" +
                    (slow ? "Slow" : "Instant") + ", " + PRINT_QUEUE.size() + " blocks)."));
            return 1;
        } catch (IOException | RuntimeException exception) {
            player.sendSystemMessage(Component.literal("Could not print schematic: " + exception.getMessage()));
            return 0;
        }
    }

    private static void tickPrint() {
        if (PRINT_QUEUE.isEmpty()) return;
        int count = Math.min(blocksPerTick, PRINT_QUEUE.size());
        for (int i = 0; i < count; i++) {
            PrintBlock block = PRINT_QUEUE.pollFirst();
            if (block == null) break;
            if (block.state().isAir()) continue;
            block.level().setBlock(block.position(), block.state(), 3);
        }
        if (PRINT_QUEUE.isEmpty()) printingPlayer = null;
    }

    private record PrintBlock(ServerLevel level, BlockPos position, BlockState state) {}
}
