package de.leon.fastplace.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record StatePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<StatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("fastplace", "state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    StatePayload::enabled,
                    StatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
