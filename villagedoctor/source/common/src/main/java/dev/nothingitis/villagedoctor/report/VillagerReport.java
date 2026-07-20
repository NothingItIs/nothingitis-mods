package dev.nothingitis.villagedoctor.report;

import dev.nothingitis.villagedoctor.mixin.VillagerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * One villager checkup: raw facts + diagnosis lines. UI-agnostic — the dialog and
 * the chat report both render from lines(); outline/owner features reuse the POI getters.
 */
public final class VillagerReport {

    public enum Kind { HEADER, INFO, GOOD, WARN, BAD }

    public record Line(Kind kind, String label, String value) {
        public String plain() {
            return label.isEmpty() ? value : label + ": " + value;
        }
    }

    /** Vanilla merchant level titles, index = level 1..5. */
    private static final String[] LEVEL_NAMES = {"Novice", "Apprentice", "Journeyman", "Expert", "Master"};
    /** Vanilla POI search radius for beds/workstations (village radius). */
    public static final int POI_RADIUS = 48;
    /** Vanilla max restocks per day. */
    private static final int MAX_RESTOCKS_PER_DAY = 2;

    private final List<Line> lines = new ArrayList<>();
    private final Optional<GlobalPos> bed;
    private final Optional<GlobalPos> jobSite;
    private final Optional<GlobalPos> meetingPoint;

    private VillagerReport(Optional<GlobalPos> bed, Optional<GlobalPos> jobSite, Optional<GlobalPos> meetingPoint) {
        this.bed = bed;
        this.jobSite = jobSite;
        this.meetingPoint = meetingPoint;
    }

    public List<Line> lines() {
        return lines;
    }

    public Optional<GlobalPos> bed() {
        return bed;
    }

    public Optional<GlobalPos> jobSite() {
        return jobSite;
    }

    public Optional<GlobalPos> meetingPoint() {
        return meetingPoint;
    }

    /** Full plain-text report (copy button / chat fallback). */
    public String plainText() {
        StringBuilder sb = new StringBuilder();
        for (Line line : lines) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(line.plain());
        }
        return sb.toString();
    }

    public static VillagerReport collect(ServerLevel level, Villager villager, ServerPlayer viewer) {
        Optional<GlobalPos> bed = villager.getBrain().getMemory(MemoryModuleType.HOME);
        Optional<GlobalPos> jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        Optional<GlobalPos> potentialJobSite = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        Optional<GlobalPos> meetingPoint = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
        VillagerReport report = new VillagerReport(bed, jobSite, meetingPoint);

        VillagerData data = villager.getVillagerData();
        String profession = keyPath(data.profession());
        boolean nitwit = "nitwit".equals(profession);
        boolean jobless = "none".equals(profession);
        boolean baby = villager.isBaby();
        VillagerAccessor access = (VillagerAccessor) villager;

        // ---- identity ----
        String professionLabel = professionLabel(villager);
        String title = villager.hasCustomName()
                ? villager.getCustomName().getString() + " (" + professionLabel + ")"
                : "Villager".equals(professionLabel) ? "Villager" : "Villager — " + professionLabel;
        report.add(Kind.HEADER, "", title);
        if (!jobless && !nitwit) {
            int lvl = Math.max(1, Math.min(data.level(), LEVEL_NAMES.length));
            report.add(Kind.INFO, "Level", LEVEL_NAMES[lvl - 1] + " (" + data.level() + ")");
        }
        report.add(Kind.INFO, "Type", keyPath(data.type()) + (baby ? " · baby" : ""));

        // ---- claimed POIs ----
        BlockPos here = villager.blockPosition();
        report.add(Kind.INFO, "Bed", bed.map(gp -> formatPos(level, villager, gp)).orElse("none claimed"));
        report.add(Kind.INFO, "Workstation", jobSite.map(gp -> formatPos(level, villager, gp) + blockName(level, gp))
                .orElse("none claimed"));
        potentialJobSite.ifPresent(gp ->
                report.add(Kind.INFO, "Acquiring workstation", formatPos(level, villager, gp) + blockName(level, gp)));
        meetingPoint.ifPresent(gp -> report.add(Kind.INFO, "Meeting point", formatPos(level, villager, gp)));

        // ---- trades & restocking ----
        if (!baby && !jobless && !nitwit) {
            MerchantOffers offers = villager.getOffers();
            long locked = offers.stream().filter(MerchantOffer::isOutOfStock).count();
            report.add(Kind.INFO, "Trades", offers.size() + (locked > 0 ? " (" + locked + " locked)" : ""));

            int restocks = access.villagedoctor$restocksToday();
            report.add(Kind.INFO, "Restocks today", restocks + "/" + MAX_RESTOCKS_PER_DAY);
            if (locked > 0) {
                report.add(Kind.INFO, "Locked trades refresh",
                        restockCountdown(level, restocks, access.villagedoctor$lastRestockGameTime()));
            }
            boolean working = villager.getBrain().getActiveNonCoreActivity()
                    .map(a -> a == Activity.WORK).orElse(false);
            if (jobSite.isEmpty()) {
                report.add(Kind.BAD, "Can restock now", "no — no workstation");
            } else if (restocks >= MAX_RESTOCKS_PER_DAY) {
                report.add(Kind.INFO, "Can restock now", "no — already restocked twice today");
            } else if (!working) {
                report.add(Kind.INFO, "Can restock now", "no — outside work hours");
            } else {
                report.add(Kind.GOOD, "Can restock now", "yes — next visit to the workstation");
            }
        }

        // ---- breeding: every vanilla gate, all failing reasons listed ----
        if (baby) {
            report.add(Kind.INFO, "Can breed", "no — is a baby");
        } else {
            List<String> reasons = new ArrayList<>();
            int points = access.villagedoctor$foodLevel() + access.villagedoctor$foodPointsInInventory();
            if (points < Villager.BREEDING_FOOD_THRESHOLD) {
                reasons.add("needs " + (Villager.BREEDING_FOOD_THRESHOLD - points) + " more food points — has "
                        + points + "/" + Villager.BREEDING_FOOD_THRESHOLD + " (bread = 4, carrot/potato/beetroot = 1)");
            }
            if (villager.getAge() > 0) {
                reasons.add("recently bred — on cooldown");
            }
            long freeBedsNearby = level.getPoiManager().getCountInRange(
                    h -> h.is(PoiTypes.HOME), here, POI_RADIUS, PoiManager.Occupancy.HAS_SPACE);
            if (freeBedsNearby == 0) {
                reasons.add("no free bed nearby for the baby (add beds)");
            }
            if (reasons.isEmpty()) {
                // eligible ≠ willing right now: the make-love behavior only runs while IDLE
                Optional<Activity> activity = villager.getBrain().getActiveNonCoreActivity();
                boolean idleNow = activity.map(a -> a == Activity.IDLE).orElse(true);
                if (idleNow) {
                    report.add(Kind.GOOD, "Can breed", "yes — " + points + " food points, "
                            + freeBedsNearby + " free bed" + (freeBedsNearby == 1 ? "" : "s")
                            + " nearby (partner needs its own 12 food points too)");
                } else {
                    String doing = activity.map(a -> cap(a.getName())).orElse("busy");
                    report.add(Kind.WARN, "Can breed", "ready, but villagers only breed during idle hours — currently "
                            + doing + " (they idle in the early morning and before dusk)");
                }
            } else {
                report.add(Kind.WARN, "Can breed", "no — " + String.join("; ", reasons));
            }
        }
        // babies carry food too (they keep it until they grow up)
        String food = foodSummary(villager.getInventory());
        report.add(Kind.INFO, "Food carried", food.isEmpty() ? "none" : food);
        if (!villager.getInventory().canAddItem(new ItemStack(net.minecraft.world.item.Items.BREAD))) {
            report.add(Kind.WARN, "⚠ Inventory full", "can't pick up more food");
        }
        if (!villager.canPickUpLoot()) {
            // command/plugin-summoned villagers ship with the flag off (26.x /summon does!)
            report.add(Kind.BAD, "⚠ Item pickup disabled",
                    "CanPickUpLoot is off — will never collect food (summon with {CanPickUpLoot:1b} or data-merge it)");
        }

        // ---- reputation ----
        int reputation = villager.getPlayerReputation(viewer);
        if (reputation != 0) {
            report.add(reputation < 0 ? Kind.WARN : Kind.GOOD, "Reputation with you",
                    (reputation > 0 ? "+" : "") + reputation
                            + (reputation < 0 ? " (raises your prices)" : " (lowers your prices)"));
        } else {
            report.add(Kind.INFO, "Reputation with you", "0 (neutral prices)");
        }

        // ---- diagnosis: warnings only when something is off ----
        PoiManager poi = level.getPoiManager();
        if (bed.isEmpty()) {
            long freeBeds = poi.getCountInRange(h -> h.is(PoiTypes.HOME), here, POI_RADIUS,
                    PoiManager.Occupancy.HAS_SPACE);
            report.add(Kind.BAD, "⚠ No bed claimed", freeBeds > 0
                    ? freeBeds + " free bed" + (freeBeds == 1 ? "" : "s") + " within " + POI_RADIUS + " blocks — should claim soon"
                    : "no free beds within " + POI_RADIUS + " blocks — place beds closer");
        }
        if (!baby && nitwit) {
            report.add(Kind.INFO, "Note", "nitwits never take a job, trade, or restock");
        } else if (!baby && jobSite.isEmpty() && potentialJobSite.isEmpty()) {
            Predicate<Holder<PoiType>> acquirable = jobless
                    ? h -> h.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)
                    : data.profession().value().acquirableJobSite();
            long freeStations = poi.getCountInRange(acquirable, here, POI_RADIUS, PoiManager.Occupancy.HAS_SPACE);
            report.add(Kind.BAD, "⚠ No workstation", freeStations > 0
                    ? freeStations + " matching free within " + POI_RADIUS + " blocks — should claim soon"
                    : "no matching free workstation within " + POI_RADIUS + " blocks");
        }

        return report;
    }

    private void add(Kind kind, String label, String value) {
        lines.add(new Line(kind, label, value));
    }

    /** Vanilla restock cooldown between two same-day restocks. */
    private static final long RESTOCK_COOLDOWN_TICKS = 2400L;
    /** Villagers start their work schedule around this time of day. */
    private static final long WORK_START_TICK = 2000L;

    /** Relative time until locked trades can refresh (vanilla restock rules). */
    private static String restockCountdown(ServerLevel level, int restocksToday, long lastRestockGameTime) {
        if (restocksToday >= MAX_RESTOCKS_PER_DAY) {
            // Renamed between generations (1.21.x getDayTime / 26.x getOverworldClockTime),
            // so it goes through the compat shim — see DayTimeCompat.
            long dayTime = DayTimeCompat.dayTime(level) % 24000L;
            long untilWork = (24000L - dayTime + WORK_START_TICK) % 24000L;
            if (untilWork == 0) untilWork = 24000L;
            return "next work day — in ~" + relative(untilWork);
        }
        long readyAt = lastRestockGameTime + RESTOCK_COOLDOWN_TICKS;
        long remaining = readyAt - level.getGameTime();
        return remaining <= 0 ? "ready — next workstation visit" : "in ~" + relative(remaining);
    }

    /** Ticks → "12m 30s" (house rule: relative time in anything user-facing). */
    private static String relative(long ticks) {
        long seconds = Math.max(1, ticks / 20);
        long minutes = seconds / 60;
        long rest = seconds % 60;
        if (minutes == 0) return rest + "s";
        return rest == 0 ? minutes + "m" : minutes + "m " + rest + "s";
    }

    private static String keyPath(Holder<?> holder) {
        return holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
    }

    /** Proper English, never raw ids: custom name, else "Farmer"/"Nitwit"/"Villager". */
    public static String displayName(Villager villager) {
        if (villager.hasCustomName()) return villager.getCustomName().getString();
        return professionLabel(villager);
    }

    /** "Farmer"/"Nitwit"/… — jobless villagers read as "Villager", never "none". */
    public static String professionLabel(Villager villager) {
        String profession = villager.getVillagerData().profession().unwrapKey()
                .map(k -> k.identifier().getPath()).orElse("none");
        if ("none".equals(profession) || profession.isEmpty()) return "Villager";
        return Character.toUpperCase(profession.charAt(0)) + profession.substring(1);
    }

    /** "125 64 -330 (12 blocks away)" — dimension noted only when it differs. */
    private static String formatPos(ServerLevel level, Villager villager, GlobalPos gp) {
        BlockPos pos = gp.pos();
        String s = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        if (!gp.dimension().equals(level.dimension())) {
            return s + " (in " + pretty(gp.dimension().identifier()) + ")";
        }
        int dist = (int) Math.round(Math.sqrt(pos.distSqr(villager.blockPosition())));
        return s + " (" + dist + " block" + (dist == 1 ? "" : "s") + " away)";
    }

    /** " — lectern" when the position is loaded in this dimension. */
    private static String blockName(ServerLevel level, GlobalPos gp) {
        if (!gp.dimension().equals(level.dimension()) || !level.isLoaded(gp.pos())) return "";
        return " — " + level.getBlockState(gp.pos()).getBlock().getName().getString().toLowerCase();
    }

    /** "bread x3, carrot x12" for the villager-food items in the inventory. */
    private static String foodSummary(SimpleContainer inventory) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && Villager.FOOD_POINTS.containsKey(stack.getItem())) {
                counts.merge(stack.getHoverName().getString().toLowerCase(), stack.getCount(), Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder();
        counts.forEach((name, count) -> {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(name).append(" x").append(count);
        });
        return sb.toString();
    }

    private static String pretty(Identifier id) {
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
