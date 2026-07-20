package dev.nothingitis.villagedoctor.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * 26.x variant — every point where the FABRIC API surface (not Minecraft's) differs between
 * the generations this mod ships for. These are compile-time symbols with no common form, so
 * a runtime shim cannot help; the sibling file is src/main/java-1.21.11/.../FabricApiCompat.java
 * and the two MUST expose the same methods.
 *
 * <p>For MINECRAFT API drift prefer a reflection shim instead — see DayTimeCompat and
 * TeamColorCompat. Only Fabric API belongs here.
 *
 * <p>Differences covered:
 * <ul>
 *   <li>{@code ServerEntityWorldChangeEvents} (1.21.x) renamed to
 *       {@code ServerEntityLevelChangeEvents} (26.x)</li>
 *   <li>{@code PayloadTypeRegistry.playS2C()/playC2S()} (1.21.x) renamed to
 *       {@code clientboundPlay()/serverboundPlay()} (26.x)</li>
 * </ul>
 */
final class FabricApiCompat {

    private FabricApiCompat() {
    }

    /** Fires after a player changes dimension, so outlines can be re-sent. */
    static void onPlayerChangeLevel(Consumer<ServerPlayer> action) {
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(
            (player, origin, destination) -> action.accept(player));
    }

    static PayloadTypeRegistry<RegistryFriendlyByteBuf> clientboundPlay() {
        return PayloadTypeRegistry.clientboundPlay();
    }

    static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverboundPlay() {
        return PayloadTypeRegistry.serverboundPlay();
    }
}
