package de.leon.fastplace.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FastBreakStatePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<FastBreakStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("fastplace", "fastbreak_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FastBreakStatePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    FastBreakStatePayload::enabled,
                    FastBreakStatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
