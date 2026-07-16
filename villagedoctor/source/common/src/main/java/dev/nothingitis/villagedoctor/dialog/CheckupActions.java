package dev.nothingitis.villagedoctor.dialog;

import dev.nothingitis.villagedoctor.VillageDoctor;
import dev.nothingitis.villagedoctor.outline.OutlineService;
import dev.nothingitis.villagedoctor.report.CheckupUi;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Server-side handler for the checkup dialog's custom button clicks
 * (ServerboundCustomClickActionPacket via ServerCommonPacketListenerImplMixin —
 * vanilla's own handler is an empty stub and no loader event exists).
 */
public final class CheckupActions {

    /** One id for all of this mod's dialog callbacks; buttons differ by the NBT payload. */
    public static final Identifier ACTION_ID = Identifier.fromNamespaceAndPath(VillageDoctor.MOD_ID, "action");

    private CheckupActions() {
    }

    public static void handle(ServerPlayer player, ServerboundCustomClickActionPacket packet) {
        if (!ACTION_ID.equals(packet.id())) return;
        // The vanilla method runs twice (netty thread, then re-dispatched on the server
        // thread) — only act on the main-thread pass.
        if (!(player.level() instanceof ServerLevel playerLevel)) return;
        MinecraftServer server = playerLevel.getServer();
        if (!server.isSameThread()) return;
        if (!(packet.payload().orElse(null) instanceof CompoundTag tag)) return;

        VillageDoctor.guarded("dialog action", player, () -> {
            switch (tag.getStringOr("action", "")) {
                case "close" -> {
                    CheckupUi.closed(player); // stop the live refresh (button OR Escape)
                    player.connection.send(ClientboundClearDialogPacket.INSTANCE);
                }
                case "post_chat" -> withVillager(player, tag, (level, villager) ->
                        CheckupUi.postChat(player, level, villager));
                case "toggle_outline" -> withVillager(player, tag, (level, villager) ->
                        OutlineService.toggle(player, level, villager));
                default -> { }
            }
        });
    }

    private static void withVillager(ServerPlayer player, CompoundTag tag,
                                     BiConsumer<ServerLevel, Villager> action) {
        UUID uuid;
        try {
            uuid = UUID.fromString(tag.getStringOr("villager", ""));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) return;
        Entity entity = level.getEntityInAnyDimension(uuid);
        if (entity instanceof Villager villager && villager.isAlive()
                && villager.level() instanceof ServerLevel villagerLevel) {
            action.accept(villagerLevel, villager);
        } else {
            player.sendSystemMessage(Component.literal("That villager is gone.")
                    .withStyle(ChatFormatting.RED));
        }
    }
}
