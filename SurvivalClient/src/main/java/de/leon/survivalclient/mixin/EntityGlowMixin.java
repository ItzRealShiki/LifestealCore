package de.leon.survivalclient.mixin;

import de.leon.survivalclient.client.EspState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void survivalclient$glowPlayers(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player
                && EspState.isActive()
                && player != Minecraft.getInstance().player) {
            cir.setReturnValue(true);
        }
    }
}
