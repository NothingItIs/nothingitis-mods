package dev.nothingitis.villagedoctor.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccessor {

    @Accessor("DATA_BLOCK_STATE_ID")
    static EntityDataAccessor<BlockState> villagedoctor$blockState() {
        throw new AssertionError();
    }
}
