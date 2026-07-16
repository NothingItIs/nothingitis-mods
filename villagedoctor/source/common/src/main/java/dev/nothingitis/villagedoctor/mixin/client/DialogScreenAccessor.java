package dev.nothingitis.villagedoctor.mixin.client;

import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.server.dialog.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DialogScreen.class)
public interface DialogScreenAccessor {

    @Accessor("dialog")
    Dialog villagedoctor$dialog();

    @Accessor("bodyScroll")
    ScrollableLayout villagedoctor$bodyScroll();
}
