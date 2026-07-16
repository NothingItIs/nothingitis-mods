package dev.nothingitis.villagedoctor.mixin.client;

import dev.nothingitis.villagedoctor.item.Stethoscope;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With the client module the Stethoscope shows its own texture — it is not a
 * spyglass there, so its use (the scope zoom) is blocked entirely on modded
 * clients. Vanilla clients keep the zoom (they still see a spyglass).
 *
 * <p>This lives HERE, at the head of the client's use-item entry point, and not in
 * loader interaction events: the cancel must land BEFORE the use packet leaves and
 * before the local use starts (a later block desyncs the server's using-state), and
 * the loader events proved unreliable for that on a live client. Common "client"
 * mixin array — one patch covers both loaders.
 *
 * <p>26.x declares useItem(Player, InteractionHand) — the handler parameter must be
 * Player exactly (not LocalPlayer), or the descriptor check rejects the mixin at
 * apply time and the client disconnects from every server with "Network Protocol
 * Error" (the class loads mid-login).
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void villagedoctor$blockStethoscopeUse(Player player, InteractionHand hand,
                                                   CallbackInfoReturnable<InteractionResult> cir) {
        if (Stethoscope.isStethoscope(player.getItemInHand(hand))) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
