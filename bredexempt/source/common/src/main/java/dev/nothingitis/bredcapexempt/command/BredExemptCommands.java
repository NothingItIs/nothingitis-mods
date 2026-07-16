package dev.nothingitis.bredcapexempt.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.nothingitis.bredcapexempt.BredExempt;
import dev.nothingitis.bredcapexempt.BredExemptMarked;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * /bredexempt inspect — is the mob you're looking at marked, and does it still count?
 * /bredexempt cap     — passive-cap accounting for your dimension (vanilla's own numbers).
 * Output: Carpet-ish — terse colored lines, warnings only when something is off.
 */
public final class BredExemptCommands {

    private static final double INSPECT_RANGE = 16.0;
    /** Vanilla spawn-cap scaling constant: caps are per 17x17 chunks (NaturalSpawner.MAGIC_NUMBER). */
    private static final int SPAWN_AREA_CHUNKS = 17 * 17;

    private BredExemptCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bredexempt")
                .then(Commands.literal("inspect").executes(ctx -> inspect(ctx.getSource())))
                .then(Commands.literal("cap").executes(ctx -> cap(ctx.getSource()))));
    }

    private static Component text(String s, ChatFormatting... style) {
        return Component.literal(s).withStyle(style);
    }

    private static String pretty(Identifier id) {
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    /** Clicking anywhere on the report copies its plain text; hover shows the hint. */
    private static MutableComponent copyable(MutableComponent msg) {
        String plain = msg.getString();
        return msg.withStyle(style -> style
                .withClickEvent(new ClickEvent.CopyToClipboard(plain))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("Click to copy").withStyle(ChatFormatting.DARK_GRAY))));
    }

    // ---- /bredexempt inspect ----

    private static int inspect(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(text("Players only.", ChatFormatting.RED));
            return 0;
        }
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, e -> e instanceof Mob, INSPECT_RANGE);
        if (!(hit instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof Mob mob)) {
            source.sendFailure(text("Look at a mob (within " + (int) INSPECT_RANGE + " blocks).", ChatFormatting.RED));
            return 0;
        }

        EntityType<?> type = mob.getType();
        boolean persistent = mob.isPersistenceRequired() || mob.requiresCustomPersistence();
        boolean counted = !persistent && type.getCategory() != MobCategory.MISC;
        String reason = mob instanceof BredExemptMarked marked ? marked.bredexempt$getReason() : null;

        MutableComponent msg = text("• ", ChatFormatting.DARK_GRAY).copy()
                .append(text(pretty(EntityType.getKey(type)), ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(text(" (" + type.getCategory().getName() + ")", ChatFormatting.DARK_GRAY));

        msg.append(text("\n persistent: ", ChatFormatting.GRAY));
        if (reason != null) {
            msg.append(text("yes", ChatFormatting.GREEN))
               .append(text(" — ", ChatFormatting.DARK_GRAY))
               .append(text(reason, ChatFormatting.AQUA));
        } else if (persistent) {
            msg.append(text("yes", ChatFormatting.YELLOW))
               .append(text(" (not by BredExempt)", ChatFormatting.DARK_GRAY));
        } else {
            msg.append(text("no", ChatFormatting.GRAY));
        }

        msg.append(text("\n counts toward cap: ", ChatFormatting.GRAY))
           .append(counted ? text("yes", ChatFormatting.YELLOW) : text("no", ChatFormatting.GREEN));

        // warnings only when something is off
        if (!(mob instanceof Animal)) {
            msg.append(text("\n ⚠ Not an Animal type — outside BredExempt's scope", ChatFormatting.RED));
        }
        if (!BredExempt.eligible(type)) {
            msg.append(text("\n ⚠ Excluded by config/tags", ChatFormatting.RED));
        }

        MutableComponent report = copyable(msg);
        source.sendSuccess(() -> report, false);
        return 1;
    }

    // ---- /bredexempt cap ----

    private static int cap(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        NaturalSpawner.SpawnState state = level.getChunkSource().getLastSpawnState();
        if (state == null) {
            source.sendFailure(text("No spawn data yet — try again in a moment.", ChatFormatting.RED));
            return 0;
        }
        int chunks = state.getSpawnableChunkCount();
        int counted = state.getMobCategoryCounts().getInt(MobCategory.CREATURE);
        int cap = MobCategory.CREATURE.getMaxInstancesPerChunk() * chunks / SPAWN_AREA_CHUNKS;

        int loaded = 0, excluded = 0, ours = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity.getType().getCategory() != MobCategory.CREATURE || !(entity instanceof Mob mob)) continue;
            loaded++;
            if (mob.isPersistenceRequired() || mob.requiresCustomPersistence()) excluded++;
            if (mob instanceof BredExemptMarked marked && marked.bredexempt$getReason() != null) ours++;
        }

        ChatFormatting countColor = counted >= cap ? ChatFormatting.RED
                : counted * 4 >= cap * 3 ? ChatFormatting.YELLOW
                : ChatFormatting.GREEN;

        MutableComponent msg = text("• ", ChatFormatting.DARK_GRAY).copy()
                .append(text(pretty(level.dimension().identifier()) + " creatures", ChatFormatting.WHITE, ChatFormatting.BOLD));

        msg.append(text("\n ", ChatFormatting.RESET))
           .append(text(String.valueOf(counted), countColor, ChatFormatting.BOLD))
           .append(text(" / ", ChatFormatting.DARK_GRAY))
           .append(text(cap + " cap", ChatFormatting.GRAY))
           .append(text("  (" + chunks + " chunks)", ChatFormatting.DARK_GRAY));

        msg.append(text("\n ", ChatFormatting.RESET))
           .append(text("exempt ", ChatFormatting.GRAY)).append(text(String.valueOf(excluded), ChatFormatting.WHITE))
           .append(text(" (", ChatFormatting.DARK_GRAY))
           .append(text("BredExempt " + ours, ChatFormatting.AQUA, ChatFormatting.BOLD))
           .append(text(")", ChatFormatting.DARK_GRAY))
           .append(text(" · ", ChatFormatting.DARK_GRAY))
           .append(text("wild ", ChatFormatting.GRAY)).append(text(String.valueOf(loaded - excluded), countColor));

        MutableComponent report = copyable(msg);
        source.sendSuccess(() -> report, false);
        return 1;
    }
}
