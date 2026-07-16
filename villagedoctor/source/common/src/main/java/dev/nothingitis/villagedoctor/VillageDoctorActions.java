package dev.nothingitis.villagedoctor;

import dev.nothingitis.villagedoctor.item.Stethoscope;
import dev.nothingitis.villagedoctor.outline.OutlineService;
import dev.nothingitis.villagedoctor.report.CheckupUi;
import dev.nothingitis.villagedoctor.report.OwnerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
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

    private VillageDoctorActions() {
    }

    public static InteractionResult useEntity(Player player, Level level, InteractionHand hand, Entity target) {
        if (!(target instanceof Villager villager)) return InteractionResult.PASS;
        if (!Stethoscope.isStethoscope(player.getItemInHand(InteractionHand.MAIN_HAND))) return InteractionResult.PASS;
        // consume the OFF_HAND pass too — otherwise vanilla runs it (babies head-shake)
        if (hand != InteractionHand.MAIN_HAND || level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        if (!permitted(sp)) return InteractionResult.PASS;

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
