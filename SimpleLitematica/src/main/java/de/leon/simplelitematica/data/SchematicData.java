package de.leon.simplelitematica.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public record SchematicData(int sizeX, int sizeY, int sizeZ, List<BlockEntry> blocks) {
    public SchematicData {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("Schematic dimensions must be positive.");
        }
        blocks = List.copyOf(blocks);
    }

    public record BlockEntry(int x, int y, int z, BlockState state) {
        public BlockPos relativePos() {
            return new BlockPos(x, y, z);
        }
    }
}
