package dev.nothingitis.villagedoctor.report;

import net.minecraft.world.level.Level;

/**
 * 1.21.x variant — a DIRECT call, deliberately not reflection.
 *
 * <p>1.21.x calls the overworld day clock {@code getDayTime()}; 26.x renamed it to
 * {@code getOverworldClockTime()}. Resolving that by name would fail on this jar for the same
 * reason described in {@link dev.nothingitis.villagedoctor.outline.TeamColorCompat} — string
 * literals survive remapping unchanged, so they never match intermediary names. That failure
 * is SILENT: the countdown would just read 0 forever.
 *
 * <p>Sibling: src/main/java-26/.../DayTimeCompat.java
 */
public final class DayTimeCompat {

    private DayTimeCompat() {
    }

    /** Overworld day-clock ticks (0-23999). */
    public static long dayTime(Level level) {
        return level.getDayTime();
    }
}
