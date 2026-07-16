package dev.nothingitis.villagedoctor.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Display-entity data accessors used when faking glowing block displays. */
@Mixin(Display.class)
public interface DisplayAccessor {

    @Accessor("DATA_SCALE_ID")
    static EntityDataAccessor<Vector3fc> villagedoctor$scale() {
        throw new AssertionError();
    }

    @Accessor("DATA_GLOW_COLOR_OVERRIDE_ID")
    static EntityDataAccessor<Integer> villagedoctor$glowColorOverride() {
        throw new AssertionError();
    }

    @Accessor("DATA_BRIGHTNESS_OVERRIDE_ID")
    static EntityDataAccessor<Integer> villagedoctor$brightnessOverride() {
        throw new AssertionError();
    }
}
