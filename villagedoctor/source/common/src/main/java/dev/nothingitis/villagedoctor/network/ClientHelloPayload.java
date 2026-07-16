package dev.nothingitis.villagedoctor.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "I have the mod" — sent once right after joining. */
public record ClientHelloPayload() implements CustomPacketPayload {

    public static final Type<ClientHelloPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("villagedoctor", "client_hello"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientHelloPayload> STREAM_CODEC =
            StreamCodec.unit(new ClientHelloPayload());

    @Override
    public Type<ClientHelloPayload> type() {
        return TYPE;
    }
}
