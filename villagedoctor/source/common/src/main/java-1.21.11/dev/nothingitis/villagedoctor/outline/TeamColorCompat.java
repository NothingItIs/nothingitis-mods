package dev.nothingitis.villagedoctor.outline;

import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;

/**
 * 1.21.x variant — a DIRECT call, deliberately not reflection.
 *
 * <p>⛔ The 26.x sibling resolves {@code setColor} by string name. That is safe there because
 * 26.x is unobfuscated. It is NOT safe here: this jar is remapped to Fabric intermediary, and
 * Loom remaps class and method REFERENCES but never string literals — so {@code "setColor"}
 * would be compared against {@code method_NNNN}, never match, and the shim would silently do
 * nothing. Symptom when that happened live (2026-07-20): villager outlines rendered pure
 * white on Fabric while their beds and workstations, which use glow_color_override rather
 * than a team colour, coloured correctly. NeoForge was unaffected because it runs Mojang names.
 *
 * <p>1.21.11 has exactly one signature — {@code setColor(ChatFormatting)} — so no branching is
 * needed. Sibling: src/main/java-26/.../TeamColorCompat.java
 */
final class TeamColorCompat {

    private TeamColorCompat() {
    }

    static void setColor(PlayerTeam team, ChatFormatting color) {
        team.setColor(color);
    }
}
