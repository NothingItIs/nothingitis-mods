package dev.nothingitis.villagedoctor.report;

import dev.nothingitis.villagedoctor.VillageDoctor;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/**
 * The overworld day-clock accessor was renamed across the MC generations this mod ships for:
 * 1.21.x calls it {@code getDayTime()}, 26.x renamed it to {@code getOverworldClockTime()}.
 * Same semantics either way (ticks since dawn, 0-23999).
 *
 * <p>Source compiles against exactly one generation, so neither name can be called directly
 * without breaking the other. Resolved once by reflection — the same approach
 * {@link dev.nothingitis.villagedoctor.outline.TeamColorCompat} uses for the 26.1/26.2
 * {@code PlayerTeam#setColor} drift.
 *
 * <p>Degrades rather than throws: if neither name resolves, the trade-refresh countdown falls
 * back to a sane value instead of taking down the checkup dialog (crash-armor standing rule).
 */
public final class DayTimeCompat {

    private static final Method DAY_TIME;

    static {
        Method found = null;
        // Newest name first — 26.x is the primary build target.
        for (String name : new String[]{"getOverworldClockTime", "getDayTime"}) {
            try {
                found = Level.class.getMethod(name);
                break;
            } catch (NoSuchMethodException ignored) {
                // try the next name
            }
        }
        if (found == null) {
            VillageDoctor.LOGGER.warn(
                "[Village Doctor] No day-clock accessor found on Level "
                    + "(tried getOverworldClockTime, getDayTime) — trade-refresh countdown will be approximate.");
        }
        DAY_TIME = found;
    }

    private DayTimeCompat() {
    }

    /** Overworld day-clock ticks, or 0 when the accessor could not be resolved. */
    public static long dayTime(Level level) {
        if (DAY_TIME == null) return 0L;
        try {
            return (long) DAY_TIME.invoke(level);
        } catch (ReflectiveOperationException | ClassCastException e) {
            VillageDoctor.LOGGER.warn("[Village Doctor] Could not read the day clock: {}", e.toString());
            return 0L;
        }
    }
}
