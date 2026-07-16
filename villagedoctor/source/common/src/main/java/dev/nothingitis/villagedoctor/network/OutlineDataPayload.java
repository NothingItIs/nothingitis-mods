package dev.nothingitis.villagedoctor.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Server -> capable client: the outline boxes for one key (a villager's UUID, or a
 * position-derived UUID for village-complete bells). An EMPTY position list removes
 * the key client-side. Sent only to clients that said hello (mod installed).
 */
public record OutlineDataPayload(UUID key, int color, List<BlockPos> positions)
        implements CustomPacketPayload {

    public static final Type<OutlineDataPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("villagedoctor", "outline_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OutlineDataPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, OutlineDataPayload::key,
                    ByteBufCodecs.VAR_INT, OutlineDataPayload::color,
                    BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(16)), OutlineDataPayload::positions,
                    OutlineDataPayload::new);

    @Override
    public Type<OutlineDataPayload> type() {
        return TYPE;
    }
}
