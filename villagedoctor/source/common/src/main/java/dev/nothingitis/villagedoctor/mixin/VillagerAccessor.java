package dev.nothingitis.villagedoctor.mixin;

import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only access to the villager internals the checkup report needs. */
@Mixin(Villager.class)
public interface VillagerAccessor {

    @Accessor("foodLevel")
    int villagedoctor$foodLevel();

    @Accessor("numberOfRestocksToday")
    int villagedoctor$restocksToday();

    @Accessor("lastRestockGameTime")
    long villagedoctor$lastRestockGameTime();

    @Invoker("countFoodPointsInInventory")
    int villagedoctor$foodPointsInInventory();
}
