package dev.nothingitis.villagedoctor.mixin.client;

import dev.nothingitis.villagedoctor.client.DialogScrollMemory;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the checkup dialog's scroll position the moment its screen is torn down.
 * Minecraft.setScreen calls old.removed() before the replacement builds, so by the
 * time the refreshed dialog's init() runs the old screen is already detached —
 * removed() is the only reliable capture point.
 */
@Mixin(Screen.class)
public abstract class ScreenRemovedMixin {

    @Inject(method = "removed", at = @At("HEAD"))
    private void villagedoctor$captureDialogScroll(CallbackInfo ci) {
        if (!((Object) this instanceof DialogScreen<?> dialogScreen)) {
            return;
        }
        DialogScreenAccessor self = (DialogScreenAccessor) (Object) dialogScreen;
        String key = DialogScrollMemory.keyOf(self.villagedoctor$dialog());
        if (key == null) {
            return;
        }
        ScrollableLayout body = self.villagedoctor$bodyScroll();
        if (body == null) {
            return;
        }
        body.visitChildren(element -> {
            if (element instanceof AbstractScrollArea scrollArea) {
                DialogScrollMemory.remember(key, scrollArea.scrollAmount());
            }
        });
    }
}
