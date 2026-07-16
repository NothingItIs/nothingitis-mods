package dev.nothingitis.villagedoctor.neoforge.client;

import dev.nothingitis.villagedoctor.VillageDoctor;
import dev.nothingitis.villagedoctor.client.OutlineGizmos;
import dev.nothingitis.villagedoctor.network.ClientHelloPayload;
import dev.nothingitis.villagedoctor.network.ClientOutlineStore;
import dev.nothingitis.villagedoctor.network.OutlineDataPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * v1.1 client entrypoint (NeoForge, Dist.CLIENT — never constructed on servers):
 * hello handshake, outline store, gizmo submission. The zoom-flash and baby-wobble
 * predictions are already suppressed here by the common interaction handlers (they
 * fire client-side on NeoForge) plus the shared client Villager mixin.
 */
@Mod(value = VillageDoctor.MOD_ID, dist = Dist.CLIENT)
public final class VillageDoctorNeoForgeClient {
    public VillageDoctorNeoForgeClient(IEventBus modBus) {
        modBus.addListener((RegisterClientPayloadHandlersEvent event) ->
                event.register(OutlineDataPayload.TYPE, (payload, context) ->
                        context.enqueueWork(() -> ClientOutlineStore.apply(payload))));

        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            ClientOutlineStore.clear();
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new ServerboundCustomPayloadPacket(new ClientHelloPayload()));
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
                ClientOutlineStore.clear());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> OutlineGizmos.submit());
    }
}
