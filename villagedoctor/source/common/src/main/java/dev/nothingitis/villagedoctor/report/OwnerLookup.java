package dev.nothingitis.villagedoctor.report;

import dev.nothingitis.villagedoctor.VillageDoctor;
import dev.nothingitis.villagedoctor.outline.OutlineService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * "Whose is this?" — shift-right-click a bed/workstation/bell with the Stethoscope.
 * Ownership lives in villager brains, not the block: search nearby villagers for a
 * brain memory claiming this position, report it as a copyable chat line, and pulse
 * the owner(s) so the player can physically find them.
 */
public final class OwnerLookup {

    private static final int PULSE_TICKS = 100; // ~5 seconds
    private static final int MAX_PULSED = 8;    // bells are shared; don't strobe a whole village

    private record Claim(Villager villager, String what) {
    }

    private OwnerLookup() {
    }

    public static void report(ServerPlayer player, ServerLevel level, BlockPos clickedPos) {
        BlockState state = level.getBlockState(clickedPos);
        BlockPos poiPos = bedHead(clickedPos, state);
        GlobalPos target = GlobalPos.of(level.dimension(), poiPos);

        int radius = VillageDoctor.config().ownerLookupRadius;
        AABB box = new AABB(poiPos).inflate(radius);
        List<Claim> claims = new ArrayList<>();
        for (Villager villager : level.getEntitiesOfClass(Villager.class, box)) {
            Brain<?> brain = villager.getBrain();
            if (claims(brain, MemoryModuleType.HOME, target)) {
                claims.add(new Claim(villager, "bed"));
            } else if (claims(brain, MemoryModuleType.JOB_SITE, target)) {
                claims.add(new Claim(villager, "workstation"));
            } else if (claims(brain, MemoryModuleType.POTENTIAL_JOB_SITE, target)) {
                claims.add(new Claim(villager, "workstation — still acquiring"));
            } else if (claims(brain, MemoryModuleType.MEETING_POINT, target)) {
                claims.add(new Claim(villager, "meeting point"));
            }
        }

        String blockName = state.getBlock().getName().getString().toLowerCase();
        if (claims.isEmpty()) {
            boolean isPoi = level.getPoiManager().getType(poiPos).isPresent();
            MutableComponent msg = header(blockName).append(isPoi
                    ? Component.literal("unclaimed — no villager within " + radius + " blocks owns it")
                            .withStyle(ChatFormatting.YELLOW)
                    : Component.literal("not a villager block (bed, workstation, or bell)")
                            .withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(copyable(msg));
            return;
        }

        if (claims.size() == 1) {
            Claim claim = claims.getFirst();
            int distance = (int) Math.round(player.distanceTo(claim.villager()));
            MutableComponent msg = header(blockName)
                    .append(Component.literal(name(claim.villager())).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal("'s " + claim.what() + " ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("(" + distance + " block" + (distance == 1 ? "" : "s") + " away)")
                            .withStyle(ChatFormatting.DARK_GRAY));
            player.sendSystemMessage(copyable(msg));
        } else {
            // realistically only meeting points (bells) are shared
            MutableComponent msg = header(blockName)
                    .append(Component.literal(claims.getFirst().what() + " of ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(claims.size() + " villagers").withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(copyable(msg));
        }
        claims.stream().limit(MAX_PULSED)
                .forEach(claim -> OutlineService.pulse(player, level, claim.villager(), PULSE_TICKS));
    }

    private static boolean claims(Brain<?> brain, MemoryModuleType<GlobalPos> memory, GlobalPos target) {
        return brain.getMemory(memory).map(target::equals).orElse(false);
    }

    /** Bed POIs sit on the head block — normalize a clicked foot half. */
    private static BlockPos bedHead(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            return pos.relative(state.getValue(BedBlock.FACING));
        }
        return pos;
    }

    private static MutableComponent header(String blockName) {
        return Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(blockName).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String name(Villager villager) {
        return VillagerReport.displayName(villager);
    }

    private static MutableComponent copyable(MutableComponent msg) {
        String plain = msg.getString();
        return msg.withStyle(style -> style
                .withClickEvent(new ClickEvent.CopyToClipboard(plain))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("Click to copy").withStyle(ChatFormatting.DARK_GRAY))));
    }
}
