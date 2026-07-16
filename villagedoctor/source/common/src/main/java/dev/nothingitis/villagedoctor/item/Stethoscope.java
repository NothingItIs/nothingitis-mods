package dev.nothingitis.villagedoctor.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * The Stethoscope is a vanilla spyglass carrying a hidden custom-data marker
 * (set by the crafting recipe). Identified by the marker only — an anvil-renamed
 * spyglass is NOT a Stethoscope.
 */
public final class Stethoscope {

    public static final String MARKER_KEY = "villagedoctor:stethoscope";
    private static final CompoundTag MARKER = new CompoundTag();

    static {
        MARKER.putBoolean(MARKER_KEY, true);
    }

    private Stethoscope() {
    }

    public static boolean isStethoscope(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.matchedBy(MARKER);
    }

    /** The same stack the crafting recipe produces (marker + name + lore + glint). */
    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.SPYGLASS);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(MARKER.copy()));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Stethoscope").withStyle(style -> style.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("For checking on the villagers.")
                        .withStyle(style -> style.withItalic(true).withColor(ChatFormatting.GRAY)))));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        // cosmetic only — drives the client module's texture override (vanilla clients
        // ignore it); identity stays the CUSTOM_DATA marker above, never this
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(List.of(), List.of(), List.of(MARKER_KEY), List.of()));
        return stack;
    }
}
