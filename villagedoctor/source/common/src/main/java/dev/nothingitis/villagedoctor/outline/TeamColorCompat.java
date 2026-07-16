package dev.nothingitis.villagedoctor.outline;

import dev.nothingitis.villagedoctor.VillageDoctor;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * PlayerTeam#setColor drifted between 26.1 (takes ChatFormatting) and 26.2
 * (takes Optional&lt;TeamColor&gt;, an enum with the same 16 color names).
 * Resolved once by reflection so ONE jar runs on both — a direct call
 * crashed 26.2 servers with NoSuchMethodError (live incident 2026-07-16).
 */
final class TeamColorCompat {

    private static final Method SET_COLOR;
    private static final Class<?> TEAM_COLOR; // non-null on 26.2+ (Optional<TeamColor> signature)

    static {
        Method found = null;
        for (Method m : PlayerTeam.class.getMethods()) {
            if ("setColor".equals(m.getName()) && m.getParameterCount() == 1) {
                found = m;
                break;
            }
        }
        SET_COLOR = found;
        Class<?> teamColor = null;
        if (found != null && found.getParameterTypes()[0] == Optional.class) {
            try {
                teamColor = Class.forName("net.minecraft.world.scores.TeamColor");
            } catch (ClassNotFoundException e) {
                VillageDoctor.LOGGER.warn("[Village Doctor] Optional setColor but no TeamColor class: {}", e.toString());
            }
        }
        TEAM_COLOR = teamColor;
    }

    private TeamColorCompat() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void setColor(PlayerTeam team, ChatFormatting color) {
        if (SET_COLOR == null) return; // outline stays white — degraded, never fatal
        try {
            if (TEAM_COLOR != null) {
                Object value = Enum.valueOf((Class<? extends Enum>) TEAM_COLOR, color.name());
                SET_COLOR.invoke(team, Optional.of(value));
            } else {
                SET_COLOR.invoke(team, color);
            }
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            VillageDoctor.LOGGER.warn("[Village Doctor] Could not set outline team color: {}", e.toString());
        }
    }
}
