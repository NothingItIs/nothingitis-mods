package dev.nothingitis.villagedoctor.fabric;

import dev.nothingitis.villagedoctor.VillageDoctor;
import dev.nothingitis.villagedoctor.VillageDoctorActions;
import dev.nothingitis.villagedoctor.command.VillageDoctorCommands;
import dev.nothingitis.villagedoctor.network.ClientCapability;
import dev.nothingitis.villagedoctor.network.ClientHelloPayload;
import dev.nothingitis.villagedoctor.network.OutlineDataPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import dev.nothingitis.villagedoctor.outline.OutlineService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;

public final class VillageDoctorFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        VillageDoctor.init(FabricLoader.getInstance().getConfigDir());

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                VillageDoctorActions.useEntity(player, world, hand, entity));
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
                VillageDoctorActions.useBlock(player, world, hand, hitResult.getBlockPos()));
        // the modded-client zoom block lives in the common client mixin
        // (MultiPlayerGameModeMixin) — loader use-item events fired too late/unreliably

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                VillageDoctorCommands.register(dispatcher));

        FabricApiCompat.clientboundPlay().register(OutlineDataPayload.TYPE, OutlineDataPayload.STREAM_CODEC);
        FabricApiCompat.serverboundPlay().register(ClientHelloPayload.TYPE, ClientHelloPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ClientHelloPayload.TYPE, (payload, context) ->
                ClientCapability.markCapable(context.player().getUUID()));

        ServerTickEvents.END_SERVER_TICK.register(OutlineService::tick);
        ServerTickEvents.END_SERVER_TICK.register(dev.nothingitis.villagedoctor.report.CheckupUi::tick);
        ServerPlayerEvents.LEAVE.register(player -> {
            OutlineService.clear(player.getUUID());
            dev.nothingitis.villagedoctor.report.CheckupUi.clear(player.getUUID());
            ClientCapability.clear(player.getUUID());
            VillageDoctorActions.clear(player.getUUID());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                OutlineService.markStale(newPlayer));
        // Fabric API names differ per generation — see FabricApiCompat.
        FabricApiCompat.onPlayerChangeLevel(OutlineService::markStale);
    }
}
