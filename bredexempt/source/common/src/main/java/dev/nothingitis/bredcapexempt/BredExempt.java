package dev.nothingitis.bredcapexempt;

import dev.nothingitis.bredcapexempt.config.BredExemptConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/** Shared core: config, eligibility (lists + tags), and the mark logic all mixins route through. */
public final class BredExempt {
    public static final String MOD_ID = "bredcapexempt";
    public static final Logger LOGGER = LoggerFactory.getLogger("bredexempt");

    /** Key stored in the animal's saved data while marked. */
    public static final String REASON_KEY = "bredexempt:reason";
    public static final String REASON_OFFSPRING = "breeding_offspring";
    public static final String REASON_PARENT = "breeding_parent";
    public static final String REASON_EGG = "thrown_egg";

    public static final TagKey<EntityType<?>> ALLOWED_TAG =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("bredexempt", "allowed"));
    public static final TagKey<EntityType<?>> DENIED_TAG =
            TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("bredexempt", "denied"));

    private static BredExemptConfig config = new BredExemptConfig();

    private BredExempt() {}

    /** Called once by each loader entrypoint with that loader's config directory. */
    public static void init(Path configDir) {
        config = BredExemptConfig.load(configDir);
        LOGGER.info("[BredExempt] Bred mobs will not count towards the passive mob cap."
                + (config.allowlist.isEmpty() && config.denylist.isEmpty() ? "" : " (entity filters active)"));
    }

    public static BredExemptConfig config() {
        return config;
    }

    // ---- mark sites (called from the mixins) ----

    public static void markBreeding(Animal parent, Animal partner, AgeableMob baby) {
        if (config.exemptOffspring && baby != null) mark(baby, REASON_OFFSPRING);
        if (config.exemptParents) {
            mark(parent, REASON_PARENT);
            if (partner != null) mark(partner, REASON_PARENT);
        }
    }

    public static void markEggChick(Chicken chick) {
        if (config.exemptEggHatchedChicks) mark(chick, REASON_EGG);
    }

    /** Marks one mob persistent (if its type is eligible) and records our reason. */
    public static void mark(Mob mob, String reason) {
        if (!eligible(mob.getType())) return;
        mob.setPersistenceRequired();
        if (mob instanceof BredExemptMarked marked && marked.bredexempt$getReason() == null) {
            marked.bredexempt$setReason(reason);
        }
    }

    // ---- eligibility: denial always wins; a non-empty allow set restricts ----

    public static boolean eligible(EntityType<?> type) {
        Holder<EntityType<?>> holder = type.builtInRegistryHolder();
        if (holder.is(DENIED_TAG) || matchesList(type, holder, config.denylist)) return false;
        boolean allowSetExists = !config.allowlist.isEmpty() || tagNonEmpty(ALLOWED_TAG);
        if (!allowSetExists) return true;
        return holder.is(ALLOWED_TAG) || matchesList(type, holder, config.allowlist);
    }

    private static boolean matchesList(EntityType<?> type, Holder<EntityType<?>> holder, List<String> entries) {
        if (entries.isEmpty()) return false;
        Identifier key = EntityType.getKey(type);
        for (String entry : entries) {
            if (entry.startsWith("#")) {
                Identifier tagId = Identifier.tryParse(entry.substring(1));
                if (tagId != null && holder.is(TagKey.create(Registries.ENTITY_TYPE, tagId))) return true;
            } else {
                Identifier id = Identifier.tryParse(entry);
                if (id != null && id.equals(key)) return true;
            }
        }
        return false;
    }

    private static boolean tagNonEmpty(TagKey<EntityType<?>> tag) {
        return BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(tag).iterator().hasNext();
    }
}
