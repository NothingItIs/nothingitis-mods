package dev.nothingitis.villagedoctor.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Shared-flags accessor (glow bit). Do NOT add an ENTITY_COUNTER accessor here:
 * 26.2 removed that field from Entity (boot-crashed both loaders) — fake entity
 * ids come from OutlineService's own high-floor counter instead.
 */
@Mixin(Entity.class)
public interface EntityAccessor {

    @Accessor("DATA_SHARED_FLAGS_ID")
    static EntityDataAccessor<Byte> villagedoctor$sharedFlags() {
        throw new AssertionError();
    }
}
