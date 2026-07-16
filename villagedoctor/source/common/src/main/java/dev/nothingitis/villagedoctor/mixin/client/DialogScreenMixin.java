package dev.nothingitis.villagedoctor.mixin.client;

import dev.nothingitis.villagedoctor.client.DialogScrollMemory;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Restores the body scroll position when the server's live checkup refresh replaces
 * the dialog screen. init() runs on every open; DialogScrollMemory only answers for
 * the same villager within 2s, so genuine new dialogs still start at the top. The
 * scroll state lives on the ScrollableLayout's internal container (an
 * AbstractScrollArea reachable via visitChildren — the field itself is
 * package-private), and setScrollAmount clamps, so height changes are safe.
 */
@Mixin(DialogScreen.class)
public abstract class DialogScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void villagedoctor$restoreScroll(CallbackInfo ci) {
        DialogScreenAccessor self = (DialogScreenAccessor) this;
        Double remembered = DialogScrollMemory.consume(DialogScrollMemory.keyOf(self.villagedoctor$dialog()));
        if (remembered == null) {
            return;
        }
        ScrollableLayout body = self.villagedoctor$bodyScroll();
        if (body == null) {
            return;
        }
        body.visitChildren(element -> {
            if (element instanceof AbstractScrollArea scrollArea) {
                scrollArea.setScrollAmount(remembered);
            }
        });
    }
}
