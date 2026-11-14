package de.leon.survivalclient.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Clears only the short client delay between completed block breaks. */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeFastBreakAccessor {
    @Accessor("destroyDelay")
    void survivalclient$setDestroyDelay(int ticks);
}
