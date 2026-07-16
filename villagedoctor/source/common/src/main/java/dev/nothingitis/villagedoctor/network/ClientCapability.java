package dev.nothingitis.villagedoctor.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which connected players run the mod client-side (they sent {@link ClientHelloPayload}).
 * Capable players get wireframe outline payloads; everyone else keeps the v1.0
 * fake-packet system — the sacred fallback.
 */
public final class ClientCapability {

    private static final Set<UUID> CAPABLE = ConcurrentHashMap.newKeySet();

    private ClientCapability() {
    }

    public static void markCapable(UUID playerId) {
        CAPABLE.add(playerId);
    }

    public static boolean has(ServerPlayer player) {
        return CAPABLE.contains(player.getUUID());
    }

    public static void clear(UUID playerId) {
        CAPABLE.remove(playerId);
    }

    /**
     * Send one key's outline boxes (empty list = remove the key client-side).
     * Vanilla sending path — loader-independent; the payload codec is registered
     * by each loader at startup, and only hello-verified clients are targeted.
     */
    public static void sendOutline(ServerPlayer player, UUID key, int colorRgb, List<BlockPos> positions) {
        player.connection.send(new ClientboundCustomPayloadPacket(
                new OutlineDataPayload(key, colorRgb, positions)));
    }
}
