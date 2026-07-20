package dev.nothingitis.villagedoctor.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.nothingitis.villagedoctor.item.Stethoscope;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /villagedoctor stethoscope — ops only (level 2+). The Stethoscope is not a registered
 * item (server-side-only mod), so vanilla /give can never name it; this is the admin
 * path next to the crafting recipe. Op-gating answers the abuse concern behind the
 * original no-give-command decision.
 */
public final class VillageDoctorCommands {

    private VillageDoctorCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!dev.nothingitis.villagedoctor.VillageDoctor.config().giveCommand) return; // opt-out
        dispatcher.register(Commands.literal("villagedoctor")
                .then(Commands.literal("stethoscope")
                        .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> giveStethoscope(ctx.getSource()))));
    }

    private static int giveStethoscope(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Players only.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemStack stack = Stethoscope.create();
        if (!player.addItem(stack)) {
            player.drop(stack, false);
        }
        source.sendSuccess(() -> Component.literal("Gave 1 Stethoscope.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }
}
