package dev.nothingitis.villagedoctor.neoforge;

import dev.nothingitis.villagedoctor.VillageDoctor;
import dev.nothingitis.villagedoctor.VillageDoctorActions;
import dev.nothingitis.villagedoctor.command.VillageDoctorCommands;
import dev.nothingitis.villagedoctor.network.ClientCapability;
import dev.nothingitis.villagedoctor.network.ClientHelloPayload;
import dev.nothingitis.villagedoctor.network.OutlineDataPayload;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(VillageDoctor.MOD_ID)
public final class VillageDoctorNeoForge {
    public VillageDoctorNeoForge(IEventBus modBus) {
        VillageDoctor.init(FMLPaths.CONFIGDIR.get());

        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            PayloadRegistrar registrar = event.registrar("1").optional(); // .optional() or vanilla clients get DISCONNECTED
            registrar.playToClient(OutlineDataPayload.TYPE, OutlineDataPayload.STREAM_CODEC);
            registrar.playToServer(ClientHelloPayload.TYPE, ClientHelloPayload.STREAM_CODEC,
                    (payload, context) -> ClientCapability.markCapable(context.player().getUUID()));
        });

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
        // the modded-client zoom block lives in the common client mixin
        // (MultiPlayerGameModeMixin) — loader use-item events fired too late/unreliably
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
            ClientCapability.clear(event.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) OutlineService.markStale(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) OutlineService.markStale(player);
        });
    }
}
