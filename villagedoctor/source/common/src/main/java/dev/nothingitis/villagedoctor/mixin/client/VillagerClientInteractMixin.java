package dev.nothingitis.villagedoctor.mixin.client;

import dev.nothingitis.villagedoctor.item.Stethoscope;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CLIENT prediction fix (v1.1, loaded only on clients via the mixins "client" array):
 * vanilla's baby-villager branch runs setUnhappy in local prediction BEFORE the server
 * is consulted — the reported head-wobble. With the mod client-side we can end the
 * prediction early when the player is holding the Stethoscope; the interaction packet
 * has already been sent by the caller, so the server-side checkup proceeds normally.
 */
@Mixin(Villager.class)
public abstract class VillagerClientInteractMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void villagedoctor$suppressPrediction(Player player, InteractionHand hand,
                                                  CallbackInfoReturnable<InteractionResult> cir) {
        Villager self = (Villager) (Object) this;
        if (self.level().isClientSide()
                && Stethoscope.isStethoscope(player.getItemInHand(InteractionHand.MAIN_HAND))) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
