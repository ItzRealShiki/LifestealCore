package de.leon.survivalclient.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accesses the normal client-side use cooldown; server-side rules remain authoritative. */
@Mixin(Minecraft.class)
public interface MinecraftFastPlaceAccessor {
    @Accessor("rightClickDelay")
    void survivalclient$setRightClickDelay(int ticks);
}
