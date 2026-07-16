package dev.nothingitis.villagedoctor.fabric;

import dev.nothingitis.villagedoctor.VillageDoctor;
import dev.nothingitis.villagedoctor.VillageDoctorActions;
import dev.nothingitis.villagedoctor.command.VillageDoctorCommands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import dev.nothingitis.villagedoctor.outline.OutlineService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
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

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                VillageDoctorCommands.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(OutlineService::tick);
        ServerTickEvents.END_SERVER_TICK.register(dev.nothingitis.villagedoctor.report.CheckupUi::tick);
        ServerPlayerEvents.LEAVE.register(player -> {
            OutlineService.clear(player.getUUID());
            dev.nothingitis.villagedoctor.report.CheckupUi.clear(player.getUUID());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                OutlineService.markStale(newPlayer));
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                OutlineService.markStale(player));
    }
}
