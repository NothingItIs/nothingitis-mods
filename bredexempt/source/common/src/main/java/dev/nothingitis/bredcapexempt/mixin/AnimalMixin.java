package dev.nothingitis.bredcapexempt.mixin;

import dev.nothingitis.bredcapexempt.BredExempt;
import dev.nothingitis.bredcapexempt.BredExemptMarked;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
public abstract class AnimalMixin implements BredExemptMarked {

    @Unique
    private String bredexempt$reason;

    @Override
    public String bredexempt$getReason() {
        return bredexempt$reason;
    }

    @Override
    public void bredexempt$setReason(String reason) {
        this.bredexempt$reason = reason;
    }

    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("TAIL"))
    private void bredexempt$onBred(ServerLevel level, Animal partner, AgeableMob baby, CallbackInfo ci) {
        BredExempt.markBreeding((Animal) (Object) this, partner, baby);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void bredexempt$save(ValueOutput out, CallbackInfo ci) {
        if (bredexempt$reason != null) out.putString(BredExempt.REASON_KEY, bredexempt$reason);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void bredexempt$load(ValueInput in, CallbackInfo ci) {
        this.bredexempt$reason = in.getString(BredExempt.REASON_KEY).orElse(null);
    }
}
