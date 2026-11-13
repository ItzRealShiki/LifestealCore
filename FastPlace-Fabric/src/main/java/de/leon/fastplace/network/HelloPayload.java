package de.leon.fastplace.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HelloPayload() implements CustomPacketPayload {
    public static final Type<HelloPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("fastplace", "hello"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HelloPayload> CODEC =
            StreamCodec.unit(new HelloPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
