package dev.nothingitis.villagedoctor.neoforge;

import dev.nothingitis.villagedoctor.VillageDoctor;
import dev.nothingitis.villagedoctor.VillageDoctorActions;
import dev.nothingitis.villagedoctor.command.VillageDoctorCommands;
import dev.nothingitis.villagedoctor.outline.OutlineService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(VillageDoctor.MOD_ID)
public final class VillageDoctorNeoForge {
    public VillageDoctorNeoForge() {
        VillageDoctor.init(FMLPaths.CONFIGDIR.get());

        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
            InteractionResult result = VillageDoctorActions.useEntity(
                    event.getEntity(), event.getLevel(), event.getHand(), event.getTarget());
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
        // the interactAt path fires separately on NeoForge — consume it too or vanilla reacts
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific event) -> {
            InteractionResult result = VillageDoctorActions.useEntity(
                    event.getEntity(), event.getLevel(), event.getHand(), event.getTarget());
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            InteractionResult result = VillageDoctorActions.useBlock(
                    event.getEntity(), event.getLevel(), event.getHand(), event.getPos());
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                VillageDoctorCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            OutlineService.tick(event.getServer());
            dev.nothingitis.villagedoctor.report.CheckupUi.tick(event.getServer());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            OutlineService.clear(event.getEntity().getUUID());
            dev.nothingitis.villagedoctor.report.CheckupUi.clear(event.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) OutlineService.markStale(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) OutlineService.markStale(player);
        });
    }
}
