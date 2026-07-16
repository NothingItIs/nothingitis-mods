package dev.nothingitis.villagedoctor;

import dev.nothingitis.villagedoctor.config.VillageDoctorConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class VillageDoctor {
    public static final String MOD_ID = "villagedoctor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static VillageDoctorConfig config;

    private VillageDoctor() {
    }

    public static void init(Path configDir) {
        config = VillageDoctorConfig.load(configDir);
        LOGGER.info("[{}] Village Doctor common init.", MOD_ID);
    }

    /** Never null — falls back to defaults if init hasn't run yet (e.g. very early reload). */
    public static VillageDoctorConfig config() {
        return config == null ? new VillageDoctorConfig() : config;
    }

    /**
     * Crash armor for every entry point (interaction events, dialog callbacks, tick):
     * a mod bug must NEVER stop the server — log it, tell the player, move on.
     * (A NoSuchMethodError escaped a packet handler and downed a live test server, 2026-07-16.)
     */
    public static void guarded(String what, ServerPlayer player, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            LOGGER.error("[{}] {} failed", MOD_ID, what, t);
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                                "Village Doctor hit an internal error (" + what + ") — see the server log.")
                        .withStyle(ChatFormatting.RED));
            }
        }
    }
}
