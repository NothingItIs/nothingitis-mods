package dev.nothingitis.villagedoctor;

import dev.nothingitis.villagedoctor.item.Stethoscope;
import dev.nothingitis.villagedoctor.outline.OutlineService;
import dev.nothingitis.villagedoctor.report.CheckupUi;
import dev.nothingitis.villagedoctor.report.OwnerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Click routing for the Stethoscope. Called from both loaders' interaction events:
 *   villager + right-click        -> checkup dialog
 *   villager + shift-right-click  -> outline toggle
 *   block    + shift-right-click  -> bed/workstation owner lookup
 * Returning SUCCESS consumes the interaction (no trade GUI, no block use, no spyglass
 * zoom server-side). Without permission the click behaves fully vanilla (PASS).
 */
public final class VillageDoctorActions {

    /**
     * ⛔ A VANILLA client sends TWO packets for one right-click on an entity — INTERACT_AT
     * followed by INTERACT — so the interaction handler runs twice for a single click. That
     * is harmless for anything idempotent (opening the checkup dialog just reopens it) but it
     * silently cancels a TOGGLE: the outline went on, then straight back off, and looked like
     * it never worked at all (reported live 2026-07-20 on Fabric 1.21.11).
     *
     * <p>It hid on modded clients because the client mixin suppresses the local prediction and
     * with it one of the two packets, and on the dialog's "Toggle outline" button because that
     * is a single custom-click packet. Only a vanilla client exposes it — on BOTH loaders.
     *
     * <p>Fixed on the server rather than per-loader, so it cannot regress if a loader changes
     * which paths it forwards: a second interaction with the same player on the same target in
     * the same tick is dropped.
     */
    private static final java.util.Map<java.util.UUID, long[]> LAST_INTERACT = new java.util.concurrent.ConcurrentHashMap<>();

    private VillageDoctorActions() {
    }

    /** False when this is vanilla's duplicate packet for a click we already handled this tick. */
    private static boolean firstThisTick(ServerPlayer player, int targetId, long gameTime) {
        long[] prev = LAST_INTERACT.get(player.getUUID());
        if (prev != null && prev[0] == targetId && prev[1] == gameTime) return false;
        LAST_INTERACT.put(player.getUUID(), new long[]{targetId, gameTime});
        if (LAST_INTERACT.size() > 256) LAST_INTERACT.keySet().removeIf(id -> !id.equals(player.getUUID()));
        return true;
    }

    /** Drop a player's debounce state on disconnect. */
    public static void clear(java.util.UUID playerId) {
        LAST_INTERACT.remove(playerId);
    }

    public static InteractionResult useEntity(Player player, Level level, InteractionHand hand, Entity target) {
        if (!(target instanceof Villager villager)) return InteractionResult.PASS;
        if (!Stethoscope.isStethoscope(player.getItemInHand(InteractionHand.MAIN_HAND))) return InteractionResult.PASS;
        // consume the OFF_HAND pass too — otherwise vanilla runs it (babies head-shake)
        if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        if (!permitted(sp)) return InteractionResult.PASS;
        // Vanilla sends INTERACT_AT and INTERACT for one click — see LAST_INTERACT. Consume
        // the duplicate (SUCCESS, not PASS) so vanilla does not open the trade GUI with it.
        if (!firstThisTick(sp, villager.getId(), sl.getGameTime())) return InteractionResult.SUCCESS;

        if (sp.isShiftKeyDown()) {
            VillageDoctor.guarded("outline toggle", sp, () -> OutlineService.toggle(sp, sl, villager));
        } else {
            VillageDoctor.guarded("checkup", sp, () -> CheckupUi.open(sp, sl, villager));
        }
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockPos pos) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!Stethoscope.isStethoscope(player.getItemInHand(InteractionHand.MAIN_HAND))) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        if (!permitted(sp)) return InteractionResult.PASS;

        VillageDoctor.guarded("owner lookup", sp, () -> OwnerLookup.report(sp, sl, pos));
        return InteractionResult.SUCCESS;
    }

    private static boolean permitted(ServerPlayer player) {
        int required = VillageDoctor.config().permissionLevel;
        if (required <= 0) return true;
        PermissionLevel[] levels = PermissionLevel.values();
        PermissionLevel level = levels[Math.min(required, levels.length - 1)];
        return player.permissions().hasPermission(new Permission.HasCommandLevel(level));
    }
}
