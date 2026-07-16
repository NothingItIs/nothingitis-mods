package dev.nothingitis.villagedoctor.outline;

import dev.nothingitis.villagedoctor.VillageDoctor;
import dev.nothingitis.villagedoctor.config.VillageDoctorConfig;
import dev.nothingitis.villagedoctor.mixin.BlockDisplayAccessor;
import dev.nothingitis.villagedoctor.mixin.DisplayAccessor;
import dev.nothingitis.villagedoctor.mixin.EntityAccessor;
import dev.nothingitis.villagedoctor.network.ClientCapability;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-player villager outlines — everything is FAKE PACKETS sent to one connection only:
 * a glow-flag + client-side team (color) on the real villager, and glowing block-display
 * entities over his bed/workstation/meeting point. Nothing exists server-side; state
 * lives in this class and dies with the connection.
 *
 * Lifecycle (see fake-glow research doc): the server clobbers the glow flag whenever it
 * re-sends real entity data, so we re-assert once a second; displays are re-sent on
 * respawn/dimension change and on far->near re-tracking. Expiry is distance-armed
 * (project rule): the countdown only runs while the player is beyond
 * outlineExpireDistance of the villager, and returning cancels it.
 */
public final class OutlineService {

    private static final byte GLOW_BIT = 0x40;
    private static final float DISPLAY_SCALE = 1.02f;
    private static final double DISPLAY_OFFSET = (DISPLAY_SCALE - 1.0) / 2.0;
    private static final ChatFormatting PULSE_COLOR = ChatFormatting.WHITE;
    /**
     * Fake entity ids start far above anything a real server allocates in one uptime
     * (real ids count up from 1). Own counter because Entity.ENTITY_COUNTER no longer
     * exists in 26.2 — an accessor on it boot-crashes.
     */
    private static final java.util.concurrent.atomic.AtomicInteger FAKE_ENTITY_IDS =
            new java.util.concurrent.atomic.AtomicInteger(1_500_000_000);
    /** Packed sky/block light 15/15 — ghost boxes ignore local light (they'd sample 0 inside blocks). */
    private static final int FULL_BRIGHT = 15 << 20 | 15 << 4;
    /** Marks entry.displays records that were sent as v1.1 payloads, not fake entities. */
    private static final int PAYLOAD_SENTINEL = -1;
    /** EntityType.BLOCK_DISPLAY (the static field) was removed in 26.2 — resolve via registry, version-proof. */
    private static EntityType<?> blockDisplayType;

    private static final List<ChatFormatting> BASE_PALETTE = List.of(
            ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.YELLOW, ChatFormatting.GREEN,
            ChatFormatting.LIGHT_PURPLE, ChatFormatting.AQUA, ChatFormatting.GOLD, ChatFormatting.WHITE,
            ChatFormatting.DARK_PURPLE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
            ChatFormatting.DARK_RED, ChatFormatting.GRAY);
    private static final List<ChatFormatting> DARK_EXTRA = List.of(
            ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GRAY, ChatFormatting.BLACK);

    /** Player UUID -> outline state. Main server thread only. */
    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    private static final class PlayerState {
        final Map<UUID, Entry> outlines = new LinkedHashMap<>();
        final Map<UUID, Entry> pulses = new HashMap<>();
        /** Village-complete bells (black ghosts) — shown only while ALL claimants are outlined. */
        final Map<BlockPos, FakeDisplay> bells = new HashMap<>();
    }

    private static final class Entry {
        final UUID villagerId;
        final int slot;
        final ChatFormatting color;
        final String teamName;
        final List<FakeDisplay> displays = new ArrayList<>();
        long expireAt;     // gameTime deadline while beyond range; 0 = countdown not armed
        long pulseUntil;   // >0 marks a temporary owner-lookup pulse
        boolean stale;     // respawn/dimension change: client forgot everything, re-send
        boolean far;       // beyond visible range at last tick

        Entry(UUID villagerId, int slot, ChatFormatting color) {
            this.villagerId = villagerId;
            this.slot = slot;
            this.color = color;
            this.teamName = "vd" + slot + "_" + villagerId.toString().substring(0, 8);
        }
    }

    private record FakeDisplay(int entityId, UUID uuid, BlockPos pos, BlockState state) {
    }

    private OutlineService() {
    }

    // ---- public API ----

    public static void toggle(ServerPlayer player, ServerLevel level, Villager villager) {
        PlayerState state = STATES.computeIfAbsent(player.getUUID(), u -> new PlayerState());
        VillageDoctorConfig config = VillageDoctor.config();
        Entry existing = state.outlines.remove(villager.getUUID());
        if (existing != null) {
            tearDown(player, villager, existing);
            feedback(player, existing.color, name(villager) + " outline removed", state.outlines.size(), config.maxOutlined);
            syncVillageBells(player, state);
            return;
        }
        if (state.outlines.size() >= config.maxOutlined) {
            player.sendSystemMessage(Component.literal(
                            "Outline limit reached (" + state.outlines.size() + "/" + config.maxOutlined + ").")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        // ends any pulse on the same villager so the real color wins
        Entry pulse = state.pulses.remove(villager.getUUID());
        if (pulse != null) tearDown(player, villager, pulse);

        List<ChatFormatting> palette = palette(config);
        int slot = lowestFreeSlot(state);
        Entry entry = new Entry(villager.getUUID(), slot, palette.get(slot % palette.size()));
        state.outlines.put(villager.getUUID(), entry);
        apply(player, villager, entry);
        feedback(player, entry.color, name(villager) + " outlined", state.outlines.size(), config.maxOutlined);
        syncVillageBells(player, state); // last village member outlined -> black bell appears
    }

    /** Temporary glow (owner lookup): no color slot, no block displays, auto-ends. */
    public static void pulse(ServerPlayer player, ServerLevel level, Villager villager, int ticks) {
        PlayerState state = STATES.computeIfAbsent(player.getUUID(), u -> new PlayerState());
        if (state.outlines.containsKey(villager.getUUID())) return; // already permanently visible
        Entry entry = state.pulses.get(villager.getUUID());
        if (entry == null) {
            entry = new Entry(villager.getUUID(), 0, PULSE_COLOR);
            state.pulses.put(villager.getUUID(), entry);
            sendGlow(player, villager, true);
            sendTeam(player, villager, entry, true);
        }
        entry.pulseUntil = level.getGameTime() + ticks;
    }

    /** Respawn / dimension change: the client forgot all fakes — re-send next tick. */
    public static void markStale(ServerPlayer player) {
        PlayerState state = STATES.get(player.getUUID());
        if (state == null) return;
        state.outlines.values().forEach(e -> e.stale = true);
        state.pulses.values().forEach(e -> e.stale = true);
    }

    public static void clear(UUID playerId) {
        STATES.remove(playerId); // connection is gone, nothing to send
    }

    /** Every tick: 2 Hz pulse blink; once a second: re-assert glow, expiry, staleness. */
    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;
        if (server.getTickCount() % 10 == 0) blinkPulses(server);
        if (server.getTickCount() % 20 != 0) return;
        VillageDoctorConfig config = VillageDoctor.config();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerState state = STATES.get(player.getUUID());
            if (state == null) continue;
            VillageDoctor.guarded("outline tick", null, () -> tickPlayer(player, state, config));
        }
        STATES.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static void tickPlayer(ServerPlayer player, PlayerState state, VillageDoctorConfig config) {
            long gameTime = player.level().getGameTime();

            Iterator<Entry> it = state.outlines.values().iterator();
            while (it.hasNext()) {
                Entry entry = it.next();
                Villager villager = resolve(player, entry.villagerId);
                if (villager == null) {
                    removeDisplays(player, entry);
                    it.remove();
                    feedback(player, entry.color, "Outline ended (villager gone)",
                            state.outlines.size(), config.maxOutlined);
                    continue;
                }
                boolean sameDim = villager.level().dimension().equals(player.level().dimension());
                double distance = sameDim ? player.distanceTo(villager) : Double.MAX_VALUE;
                double visible = visibleRange(player, villager);

                // visibility-armed expiry (hardcoded to the tracking range by design)
                if (config.outlineExpireMinutes > 0) {
                    boolean beyond = distance > visible;
                    if (!beyond) {
                        entry.expireAt = 0;
                    } else if (entry.expireAt == 0) {
                        entry.expireAt = gameTime + config.outlineExpireMinutes * 1200L;
                    } else if (gameTime >= entry.expireAt) {
                        tearDown(player, villager, entry);
                        it.remove();
                        feedback(player, entry.color, name(villager) + " outline expired",
                                state.outlines.size(), config.maxOutlined);
                        continue;
                    }
                }

                boolean nowFar = !sameDim || distance > visible;
                if (entry.stale || (entry.far && !nowFar)) {
                    // full re-send: client dropped the fakes (respawn/dim change/re-track)
                    removeDisplays(player, entry);
                    entry.displays.clear();
                    apply(player, villager, entry);
                    entry.stale = false;
                } else if (!nowFar) {
                    sendGlow(player, villager, true); // heal real-data clobbers
                    if (displaysOutOfSync(villager, entry)) {
                        // POI broken / re-claimed / stolen — ghost boxes follow reality
                        removeDisplays(player, entry);
                        spawnDisplays(player, villager, entry);
                    }
                }
                entry.far = nowFar;
            }

            Iterator<Entry> pit = state.pulses.values().iterator();
            while (pit.hasNext()) {
                Entry entry = pit.next();
                Villager villager = resolve(player, entry.villagerId);
                if (villager == null || gameTime >= entry.pulseUntil) {
                    if (villager != null) tearDown(player, villager, entry);
                    pit.remove();
                    continue;
                }
                if (entry.stale) { // blinkPulses drives the glow; only the team needs re-sending
                    sendTeam(player, villager, entry, true);
                    entry.stale = false;
                }
            }
            syncVillageBells(player, state);
    }

    /** Owner-lookup pulses BLINK (glow toggled at 2 Hz) so they read as a flash, not an outline. */
    private static void blinkPulses(MinecraftServer server) {
        boolean on = (server.getTickCount() / 10) % 2 == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerState state = STATES.get(player.getUUID());
            if (state == null || state.pulses.isEmpty()) continue;
            VillageDoctor.guarded("pulse blink", null, () -> {
                for (Entry entry : state.pulses.values()) {
                    Villager villager = resolve(player, entry.villagerId);
                    if (villager != null) sendGlow(player, villager, on);
                }
            });
        }
    }

    // ---- packet plumbing ----

    private static void apply(ServerPlayer player, Villager villager, Entry entry) {
        sendGlow(player, villager, true);
        sendTeam(player, villager, entry, true);
        spawnDisplays(player, villager, entry);
    }

    private static void tearDown(ServerPlayer player, Villager villager, Entry entry) {
        sendGlow(player, villager, false);
        sendTeam(player, villager, entry, false);
        removeDisplays(player, entry);
    }

    private static void sendGlow(ServerPlayer player, Villager villager, boolean on) {
        byte real = villager.getEntityData().get(EntityAccessor.villagedoctor$sharedFlags());
        byte value = on ? (byte) (real | GLOW_BIT) : real;
        send(player, new ClientboundSetEntityDataPacket(villager.getId(),
                List.of(SynchedEntityData.DataValue.create(EntityAccessor.villagedoctor$sharedFlags(), value))));
    }

    private static void sendTeam(ServerPlayer player, Villager villager, Entry entry, boolean add) {
        PlayerTeam team = new PlayerTeam(new Scoreboard(), entry.teamName); // detached — never touches the real scoreboard
        TeamColorCompat.setColor(team, entry.color); // 26.1/26.2 signature drift — never call setColor directly
        if (add) {
            send(player, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
            send(player, ClientboundSetPlayerTeamPacket.createPlayerPacket(team,
                    villager.getStringUUID(), ClientboundSetPlayerTeamPacket.Action.ADD));
        } else {
            send(player, ClientboundSetPlayerTeamPacket.createRemovePacket(team));
        }
    }

    private static void spawnDisplays(ServerPlayer player, Villager villager, Entry entry) {
        if (!(villager.level() instanceof ServerLevel level)
                || !level.dimension().equals(player.level().dimension())) {
            return; // POIs render in the villager's dimension only
        }
        // The ghost is a GLASS box, not a copy of the real block: bed/bell models are
        // renderer-driven (a copied state draws wrong), and a copy overlapping the real
        // block samples light level 0 and renders pitch black. Glass + forced full-bright
        // + glow color = a clean colored outline box with the real block visible inside.
        int color = rgb(entry.color);
        if (ClientCapability.has(player)) {
            // modded client: one structured payload, client renders wireframes (v1.1)
            List<BlockPos> positions = new ArrayList<>();
            for (BlockPos pos : poiBlocks(level, villager)) {
                if (!level.getBlockState(pos).isAir()) positions.add(pos);
            }
            ClientCapability.sendOutline(player, entry.villagerId, color, positions);
            for (BlockPos pos : positions) {
                entry.displays.add(new FakeDisplay(PAYLOAD_SENTINEL, entry.villagerId, pos, null));
            }
            return;
        }
        for (BlockPos pos : poiBlocks(level, villager)) {
            if (level.getBlockState(pos).isAir()) continue;
            FakeDisplay display = spawnGhost(player, pos, color);
            if (display != null) entry.displays.add(display);
        }
    }

    /** One glowing glass ghost box over a block, sent to this player only. */
    private static FakeDisplay spawnGhost(ServerPlayer player, BlockPos pos, int colorRgb) {
        EntityType<?> displayType = blockDisplayType();
        if (displayType == null) return null; // registry miss — glow still works, boxes degrade away
        BlockState ghost = Blocks.GLASS.defaultBlockState();
        int id = FAKE_ENTITY_IDS.incrementAndGet();
        FakeDisplay display = new FakeDisplay(id, UUID.randomUUID(), pos, ghost);
        send(player, new ClientboundAddEntityPacket(id, display.uuid(),
                pos.getX() - DISPLAY_OFFSET, pos.getY() - DISPLAY_OFFSET, pos.getZ() - DISPLAY_OFFSET,
                0f, 0f, displayType, 0, Vec3.ZERO, 0d));
        send(player, new ClientboundSetEntityDataPacket(id, List.of(
                SynchedEntityData.DataValue.create(EntityAccessor.villagedoctor$sharedFlags(), GLOW_BIT),
                SynchedEntityData.DataValue.create(BlockDisplayAccessor.villagedoctor$blockState(), ghost),
                SynchedEntityData.DataValue.create(DisplayAccessor.villagedoctor$scale(),
                        new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE)),
                SynchedEntityData.DataValue.create(DisplayAccessor.villagedoctor$brightnessOverride(), FULL_BRIGHT),
                SynchedEntityData.DataValue.create(DisplayAccessor.villagedoctor$glowColorOverride(), colorRgb))));
        return display;
    }

    private static void removeDisplays(ServerPlayer player, Entry entry) {
        if (entry.displays.isEmpty()) return;
        if (entry.displays.getFirst().entityId() == PAYLOAD_SENTINEL) {
            ClientCapability.sendOutline(player, entry.villagerId, 0, List.of()); // empty = remove key
            entry.displays.clear();
            return;
        }
        IntList ids = new IntArrayList(entry.displays.size());
        entry.displays.forEach(d -> ids.add(d.entityId()));
        send(player, new ClientboundRemoveEntitiesPacket(ids));
        entry.displays.clear();
    }

    /** True when the ghost boxes no longer match the villager's real, still-standing POI blocks. */
    private static boolean displaysOutOfSync(Villager villager, Entry entry) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        List<BlockPos> expected = new ArrayList<>();
        for (BlockPos pos : poiBlocks(level, villager)) {
            if (!level.getBlockState(pos).isAir()) expected.add(pos);
        }
        if (expected.size() != entry.displays.size()) return true;
        for (int i = 0; i < expected.size(); i++) {
            if (!expected.get(i).equals(entry.displays.get(i).pos())) return true;
        }
        return false;
    }

    /**
     * The villager's OWN claimed blocks: bed (both halves) + workstation. The meeting
     * point is deliberately absent — bells are village-level and handled by
     * {@link #syncVillageBells} (black ghost only when ALL claimants are outlined).
     */
    private static List<BlockPos> poiBlocks(ServerLevel level, Villager villager) {
        List<BlockPos> out = new ArrayList<>();
        addPoi(out, level, villager.getBrain().getMemory(MemoryModuleType.HOME));
        addPoi(out, level, villager.getBrain().getMemory(MemoryModuleType.JOB_SITE));
        return out;
    }

    /**
     * Show a BLACK ghost on each bell whose every claimant is currently outlined by this
     * player (black is reserved — never in the villager palette). Runs on toggle and each
     * tick: recomputes the desired bell set and diffs it against what the client has.
     */
    private static void syncVillageBells(ServerPlayer player, PlayerState state) {
        Map<BlockPos, FakeDisplay> current = state.bells;
        java.util.Set<BlockPos> desired = new java.util.HashSet<>();
        if (!state.outlines.isEmpty() && player.level() instanceof ServerLevel level) {
            java.util.Set<GlobalPos> candidates = new java.util.HashSet<>();
            for (Entry entry : state.outlines.values()) {
                Villager villager = resolve(player, entry.villagerId);
                if (villager != null) {
                    villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).ifPresent(candidates::add);
                }
            }
            for (GlobalPos bell : candidates) {
                BlockPos pos = bell.pos();
                if (!bell.dimension().equals(level.dimension()) || !level.isLoaded(pos)
                        || level.getBlockState(pos).isAir()) continue;
                double bellRange = viewDistanceChunks(player) * 16.0;
                if (player.blockPosition().distSqr(pos) > bellRange * bellRange) continue;
                List<Villager> claimants = level.getEntitiesOfClass(Villager.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(dev.nothingitis.villagedoctor.report.VillagerReport.POI_RADIUS),
                        v -> v.getBrain().getMemory(MemoryModuleType.MEETING_POINT).map(bell::equals).orElse(false));
                boolean allOutlined = !claimants.isEmpty()
                        && claimants.stream().allMatch(v -> state.outlines.containsKey(v.getUUID()));
                if (allOutlined) desired.add(pos);
            }
        }
        Iterator<Map.Entry<BlockPos, FakeDisplay>> it = current.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, FakeDisplay> e = it.next();
            if (!desired.contains(e.getKey())) {
                if (e.getValue().entityId() == PAYLOAD_SENTINEL) {
                    ClientCapability.sendOutline(player, bellKey(e.getKey()), 0, List.of());
                } else {
                    send(player, new ClientboundRemoveEntitiesPacket(IntList.of(e.getValue().entityId())));
                }
                it.remove();
            }
        }
        for (BlockPos pos : desired) {
            if (!current.containsKey(pos)) {
                if (ClientCapability.has(player)) {
                    ClientCapability.sendOutline(player, bellKey(pos), 0x000000, List.of(pos));
                    current.put(pos, new FakeDisplay(PAYLOAD_SENTINEL, bellKey(pos), pos, null));
                } else {
                    FakeDisplay ghost = spawnGhost(player, pos, 0x000000);
                    if (ghost != null) current.put(pos, ghost);
                }
            }
        }
    }

    /** Stable synthetic key for a bell position (bells aren't entities). */
    private static UUID bellKey(BlockPos pos) {
        return UUID.nameUUIDFromBytes(("villagedoctor:bell:" + pos.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void addPoi(List<BlockPos> out, ServerLevel level, Optional<GlobalPos> gp) {
        if (gp.isEmpty() || !gp.get().dimension().equals(level.dimension())) return;
        BlockPos pos = gp.get().pos();
        if (!level.isLoaded(pos)) return;
        out.add(pos);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock) { // outline both halves of the bed
            Direction facing = state.getValue(BedBlock.FACING);
            out.add(state.getValue(BedBlock.PART) == BedPart.HEAD
                    ? pos.relative(facing.getOpposite())
                    : pos.relative(facing));
        }
    }

    // ---- helpers ----

    /**
     * Our own RGB table for the 16 chat colors — ChatFormatting.getColor() was removed
     * in 26.2 (caught by the compile sweep); these values are decade-stable vanilla
     * constants, so owning them beats chasing the API.
     */
    private static int rgb(ChatFormatting color) {
        return switch (color) {
            case RED -> 0xFF5555;
            case BLUE -> 0x5555FF;
            case YELLOW -> 0xFFFF55;
            case GREEN -> 0x55FF55;
            case LIGHT_PURPLE -> 0xFF55FF;
            case AQUA -> 0x55FFFF;
            case GOLD -> 0xFFAA00;
            case DARK_PURPLE -> 0xAA00AA;
            case DARK_GREEN -> 0x00AA00;
            case DARK_AQUA -> 0x00AAAA;
            case DARK_RED -> 0xAA0000;
            case GRAY -> 0xAAAAAA;
            case DARK_BLUE -> 0x0000AA;
            case DARK_GRAY -> 0x555555;
            case BLACK -> 0x000000;
            default -> 0xFFFFFF; // WHITE and anything unexpected
        };
    }

    /**
     * Blocks across which this player can actually SEE the villager — vanilla's entity
     * tracking range (from the type registration) capped by the server view distance.
     * Hardcoded by design: outlines expire relative to visibility, never a config knob.
     */
    private static double visibleRange(ServerPlayer player, Villager villager) {
        return Math.min(villager.getType().clientTrackingRange(), viewDistanceChunks(player)) * 16.0;
    }

    private static int viewDistanceChunks(ServerPlayer player) {
        return player.level() instanceof ServerLevel level
                ? level.getServer().getPlayerList().getViewDistance() : 10;
    }

    private static Villager resolve(ServerPlayer player, UUID villagerId) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        return level.getEntityInAnyDimension(villagerId) instanceof Villager villager && villager.isAlive()
                ? villager : null;
    }

    private static List<ChatFormatting> palette(VillageDoctorConfig config) {
        if (!config.useDarkColors) return BASE_PALETTE;
        List<ChatFormatting> all = new ArrayList<>(BASE_PALETTE);
        all.addAll(DARK_EXTRA);
        return all;
    }

    private static int lowestFreeSlot(PlayerState state) {
        for (int slot = 0; ; slot++) {
            final int s = slot;
            if (state.outlines.values().stream().noneMatch(e -> e.slot == s)) return slot;
        }
    }

    /** EntityType.BLOCK_DISPLAY static field is gone in 26.2 — registry lookup works on every version. */
    private static EntityType<?> blockDisplayType() {
        if (blockDisplayType == null) {
            blockDisplayType = BuiltInRegistries.ENTITY_TYPE
                    .getOptional(Identifier.fromNamespaceAndPath("minecraft", "block_display"))
                    .orElse(null);
            if (blockDisplayType == null) {
                VillageDoctor.LOGGER.warn("[Village Doctor] block_display entity type not found — outline boxes disabled");
            }
        }
        return blockDisplayType;
    }

    private static String name(Villager villager) {
        return dev.nothingitis.villagedoctor.report.VillagerReport.displayName(villager);
    }

    private static void feedback(ServerPlayer player, ChatFormatting color, String text, int used, int max) {
        player.sendSystemMessage(Component.literal("■ ").withStyle(color)
                .append(Component.literal(text + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("(" + used + "/" + max + ")").withStyle(ChatFormatting.DARK_GRAY)));
    }

    private static void send(ServerPlayer player, Packet<? extends ClientGamePacketListener> packet) {
        player.connection.send(packet);
    }
}
