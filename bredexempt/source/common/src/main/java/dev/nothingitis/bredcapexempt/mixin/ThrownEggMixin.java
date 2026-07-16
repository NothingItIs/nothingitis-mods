package dev.nothingitis.bredcapexempt.mixin;

import dev.nothingitis.bredcapexempt.BredExempt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Exempts chicks hatched from thrown eggs. ThrownEgg.onHit creates the baby chicken
 * and adds it via level.addFreshEntity; we intercept that add and route the chick
 * through the shared mark logic (config toggle + eligibility + marker) first.
 */
@Mixin(ThrownEgg.class)
public abstract class ThrownEggMixin {

    @Redirect(
            method = "onHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean bredCapExempt$exemptHatchedChick(Level level, Entity entity) {
        if (entity instanceof Chicken chicken) {
            BredExempt.markEggChick(chicken);
        }
        return level.addFreshEntity(entity);
    }
}
