package dev.nothingitis.villagedoctor.client;

import dev.nothingitis.villagedoctor.dialog.CheckupActions;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;

/**
 * Client-side one-slot memory carrying the checkup dialog's scroll position across
 * the server's live refreshes (each refresh is a full dialog reopen — vanilla dialogs
 * have no update-in-place). Keyed by the villager UUID embedded in our dialog's
 * button payloads, so a refresh keeps your place while switching to a different
 * villager starts at the top. Entries expire after 2s: a live refresh replaces the
 * screen within the same tick, anything older is a genuine new open.
 */
public final class DialogScrollMemory {

    private static final long MAX_AGE_MS = 2_000;

    private static String key;
    private static double amount;
    private static long at;

    private DialogScrollMemory() {
    }

    public static void remember(String dialogKey, double scrollAmount) {
        if (dialogKey == null) {
            return;
        }
        key = dialogKey;
        amount = scrollAmount;
        at = System.currentTimeMillis();
    }

    /** The remembered scroll for this key if it is fresh, else null. */
    public static Double consume(String dialogKey) {
        if (dialogKey == null || !dialogKey.equals(key)) {
            return null;
        }
        if (System.currentTimeMillis() - at > MAX_AGE_MS) {
            return null;
        }
        return amount;
    }

    /** The villager UUID inside our checkup buttons — null for any other dialog. */
    public static String keyOf(Dialog dialog) {
        if (!(dialog instanceof MultiActionDialog multi)) {
            return null;
        }
        for (ActionButton button : multi.actions()) {
            if (button.action().orElse(null) instanceof CustomAll custom
                    && custom.id().equals(CheckupActions.ACTION_ID)) {
                Optional<CompoundTag> additions = custom.additions();
                if (additions.isPresent()) {
                    Optional<String> villager = additions.get().getString("villager");
                    if (villager.isPresent()) {
                        return villager.get();
                    }
                }
            }
        }
        return null;
    }
}
