package dev.nothingitis.villagedoctor.report;

import dev.nothingitis.villagedoctor.dialog.CheckupActions;
import dev.nothingitis.villagedoctor.network.ClientCapability;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checkup presentation: the dialog screen (vanilla server-driven dialogs, no client mod)
 * and the copyable chat report. Every info line is click-to-copy; afterAction is NONE so
 * copying doesn't close the screen (dialog-wide setting — see research doc).
 */
public final class CheckupUi {

    private static final int LINE_WIDTH = 300;
    private static final int BUTTON_WIDTH = 150;

    /**
     * Player -> villager whose checkup is on screen. Client-capable viewers get a live
     * re-send each second (their client preserves the scroll position across the reopen);
     * vanilla clients get ONE static snapshot — every reopen resets their scroll, so live
     * updates there were unusable in a small window (design decision, 2026-07-16). The map
     * still tracks vanilla viewers so the villager-gone cleanup reaches them.
     */
    private static final java.util.Map<java.util.UUID, java.util.UUID> OPEN = new java.util.HashMap<>();

    private CheckupUi() {
    }

    public static void open(ServerPlayer player, ServerLevel level, Villager villager) {
        OPEN.put(player.getUUID(), villager.getUUID());
        send(player, level, villager);
    }

    /** The dialog was dismissed (Close button or Escape via exitAction). */
    public static void closed(ServerPlayer player) {
        OPEN.remove(player.getUUID());
    }

    public static void clear(java.util.UUID playerId) {
        OPEN.remove(playerId);
    }

    /** Once a second: re-send open checkups (client-capable viewers only) and close any whose villager is gone. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (OPEN.isEmpty() || server.getTickCount() % 20 != 0) return;
        java.util.Iterator<java.util.Map.Entry<java.util.UUID, java.util.UUID>> it = OPEN.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<java.util.UUID, java.util.UUID> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            dev.nothingitis.villagedoctor.VillageDoctor.guarded("checkup refresh", null, () -> {
                if (!(player.level() instanceof ServerLevel level)) return;
                if (level.getEntityInAnyDimension(entry.getValue()) instanceof Villager villager
                        && villager.isAlive()) {
                    if (ClientCapability.has(player)) {
                        send(player, (ServerLevel) villager.level(), villager);
                    }
                } else {
                    player.connection.send(
                            net.minecraft.network.protocol.common.ClientboundClearDialogPacket.INSTANCE);
                    player.sendSystemMessage(Component.literal("That villager is gone.")
                            .withStyle(ChatFormatting.RED));
                    it.remove();
                }
            });
        }
    }

    private static void send(ServerPlayer player, ServerLevel level, Villager villager) {
        VillagerReport report = VillagerReport.collect(level, villager, player);

        List<DialogBody> body = new ArrayList<>();
        for (VillagerReport.Line line : report.lines()) {
            body.add(new PlainMessage(copyable(styled(line), line.plain()), LINE_WIDTH));
        }
        if (!ClientCapability.has(player)) {
            body.add(new PlainMessage(Component.literal("Snapshot — reopen to refresh.")
                    .withStyle(ChatFormatting.DARK_GRAY), LINE_WIDTH));
        }

        CommonDialogData common = new CommonDialogData(
                Component.literal("Villager Checkup"),
                Optional.empty(),
                true,                       // Escape allowed — exitAction below notifies us
                false,                      // never pause — multiplayer tool, and pause+NONE is inconsistent
                DialogAction.NONE,          // copy-clicks must not close the dialog
                body,
                List.of());

        // no "Copy report" button — clicking individual lines is the copy feature by design
        List<ActionButton> buttons = List.of(
                customButton("Toggle outline", "toggle_outline", villager),
                customButton("Post in chat", "post_chat", villager));
        // Close lives in exitAction: the same server round-trip fires on the button AND on
        // Escape, so the live refresh always stops when the screen goes away.
        ActionButton close = customButton("Close", "close", null);

        Dialog dialog = new MultiActionDialog(common, buttons, Optional.of(close), 2);
        player.openDialog(Holder.direct(dialog));
    }

    /** The same report as colored, click-to-copy chat lines (dialog's "Post in chat" button). */
    public static void postChat(ServerPlayer player, ServerLevel level, Villager villager) {
        VillagerReport report = VillagerReport.collect(level, villager, player);
        MutableComponent msg = null;
        for (VillagerReport.Line line : report.lines()) {
            MutableComponent styled = styled(line);
            msg = msg == null ? styled : msg.append(Component.literal("\n")).append(styled);
        }
        if (msg == null) return;
        MutableComponent chat = copyable(msg, report.plainText());
        player.sendSystemMessage(chat);
    }

    private static ActionButton customButton(String label, String action, Villager villager) {
        CompoundTag payload = new CompoundTag();
        payload.putString("action", action);
        if (villager != null) {
            payload.putString("villager", villager.getUUID().toString());
        }
        Action custom = new CustomAll(CheckupActions.ACTION_ID, Optional.of(payload));
        return new ActionButton(new CommonButtonData(Component.literal(label), BUTTON_WIDTH), Optional.of(custom));
    }

    private static MutableComponent styled(VillagerReport.Line line) {
        if (line.kind() == VillagerReport.Kind.HEADER) {
            return Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(line.value()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        }
        ChatFormatting valueColor = switch (line.kind()) {
            case GOOD -> ChatFormatting.GREEN;
            case WARN -> ChatFormatting.YELLOW;
            case BAD -> ChatFormatting.RED;
            default -> ChatFormatting.WHITE;
        };
        ChatFormatting labelColor = switch (line.kind()) {
            case WARN -> ChatFormatting.YELLOW;
            case BAD -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
        return Component.literal(line.label()).withStyle(labelColor)
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(line.value()).withStyle(valueColor));
    }

    private static MutableComponent copyable(MutableComponent msg, String plain) {
        return msg.withStyle(style -> style
                .withClickEvent(new ClickEvent.CopyToClipboard(plain))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.literal("Click to copy").withStyle(ChatFormatting.DARK_GRAY))));
    }
}
