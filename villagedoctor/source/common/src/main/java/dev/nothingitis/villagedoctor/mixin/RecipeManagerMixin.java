package dev.nothingitis.villagedoctor.mixin;

import dev.nothingitis.villagedoctor.VillageDoctor;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/** Honors {@code craftingRecipe=false}: drops this mod's recipes at datapack (re)load. */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @ModifyVariable(method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"), argsOnly = true)
    private RecipeMap villagedoctor$dropRecipeWhenDisabled(RecipeMap map) {
        if (VillageDoctor.config().craftingRecipe) return map;
        List<RecipeHolder<?>> kept = new ArrayList<>();
        for (RecipeHolder<?> holder : map.values()) {
            if (!VillageDoctor.MOD_ID.equals(holder.id().identifier().getNamespace())) {
                kept.add(holder);
            }
        }
        return RecipeMap.create(kept);
    }
}
