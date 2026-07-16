package dev.nothingitis.villagedoctor.fabric.client;

import dev.nothingitis.villagedoctor.client.OutlineGizmos;
import dev.nothingitis.villagedoctor.network.ClientHelloPayload;
import dev.nothingitis.villagedoctor.network.ClientOutlineStore;
import dev.nothingitis.villagedoctor.network.OutlineDataPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** v1.1 client entrypoint (Fabric): hello handshake, outline store, gizmo submission. */
public final class VillageDoctorFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OutlineDataPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientOutlineStore.apply(payload)));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientOutlineStore.clear();
            ClientPlayNetworking.send(new ClientHelloPayload());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientOutlineStore.clear());

        ClientTickEvents.END_CLIENT_TICK.register(client -> OutlineGizmos.submit());
    }
}
