package dev.nothingitis.villagedoctor.mixin;

import dev.nothingitis.villagedoctor.dialog.CheckupActions;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's handler for custom dialog-click actions is an empty stub and neither
 * loader exposes an event — this is the documented interception point. The method
 * only exists on the common (config+play) listener; guard to the play-phase
 * listener to reach the player.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {

    @Inject(method = "handleCustomClickAction", at = @At("HEAD"))
    private void villagedoctor$onCustomClickAction(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl gameListener) {
            CheckupActions.handle(gameListener.player, packet);
        }
    }
}
