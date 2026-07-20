package dev.nothingitis.villagedoctor.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * 1.21.x variant — the Fabric API names used on the 1.21.x line. Verified against
 * fabric-api 0.141.5+1.21.11 (and the same names hold in 0.128.2+1.21.6).
 * Sibling: src/main/java-26/.../FabricApiCompat.java — keep the two in step.
 */
final class FabricApiCompat {

    private FabricApiCompat() {
    }

    /** Fires after a player changes dimension, so outlines can be re-sent. */
    static void onPlayerChangeLevel(Consumer<ServerPlayer> action) {
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
            (player, origin, destination) -> action.accept(player));
    }

    static PayloadTypeRegistry<RegistryFriendlyByteBuf> clientboundPlay() {
        return PayloadTypeRegistry.playS2C();
    }

    static PayloadTypeRegistry<RegistryFriendlyByteBuf> serverboundPlay() {
        return PayloadTypeRegistry.playC2S();
    }
}
