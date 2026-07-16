package dev.nothingitis.villagedoctor.client;

import dev.nothingitis.villagedoctor.network.ClientOutlineStore;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v1.1 wireframe renderer — built on vanilla's gizmo API (26.x render-rewrite era;
 * survives backend changes because vanilla maintains the pipeline, not us). Called
 * every client tick by each loader's client entrypoint.
 *
 * <p>Draws the LITERAL block outline, not a unit cube: each outlined position's
 * VoxelShape (the same shape the vanilla selection box uses) is merged per outline
 * key with {@link Shapes#or}, which culls the touching faces — the two bed halves
 * read as one flat bed, a lectern shows its real silhouette.
 *
 * <p>VERSION GATE: on 26.2+ the merged shape's edges are drawn as line gizmos
 * (vanilla-selection-box look). On 26.1.x we draw one stroked cuboid gizmo per
 * shape box instead — the very first 26.x gizmo pipeline died in native code
 * (opengl32.dll, 0xC0000005) after sustained line-gizmo submission on a live
 * 26.1.0 client, while cuboid gizmos ran for hours on the same machine. Unknown
 * or snapshot versions fall back to the cuboid mode, the one proven safe
 * everywhere. Seams where shape boxes touch are the accepted trade-off there.
 *
 * <p>Merged shapes are cached per key and rebuilt only when the server sends new
 * positions or a block at one of them changes client-side (BlockStates are
 * canonical instances, so an identity hash detects that cheaply). A position whose
 * shape is empty (block broken, chunk not loaded) is skipped — the server re-syncs
 * ghosts on block changes, so the authoritative removal follows within a second.
 *
 * <p>This is a client entry point, so it carries its own crash armor: rendering
 * must never take the game down, and failures log at most once per 10s.
 */
public final class OutlineGizmos {

    private static final Logger LOGGER = LoggerFactory.getLogger("villagedoctor");
    private static final int PERSIST_MS = 200;
    private static final float STROKE_WIDTH = 2.0f;
    private static final long ERROR_LOG_INTERVAL_MS = 10_000;

    private record CachedShape(List<BlockPos> positions, int statesHash, BlockPos anchor, VoxelShape shape) {
    }

    private static final Map<UUID, CachedShape> SHAPES = new HashMap<>();
    private static Boolean lineGizmosSafe;
    private static long lastErrorLog;

    private OutlineGizmos() {
    }

    public static void submit() {
        try {
            Level level = Minecraft.getInstance().level;
            Map<UUID, ClientOutlineStore.Boxes> all = ClientOutlineStore.all();
            if (level == null || all.isEmpty()) {
                if (!SHAPES.isEmpty()) {
                    SHAPES.clear();
                }
                return;
            }
            SHAPES.keySet().retainAll(all.keySet());
            for (Map.Entry<UUID, ClientOutlineStore.Boxes> entry : all.entrySet()) {
                ClientOutlineStore.Boxes boxes = entry.getValue();
                if (boxes.positions().isEmpty()) {
                    continue;
                }
                CachedShape cached = shapeFor(entry.getKey(), boxes.positions(), level);
                int argb = 0xFF000000 | boxes.colorRgb();
                BlockPos anchor = cached.anchor();
                if (lineGizmosSafe()) {
                    Vec3 base = new Vec3(anchor.getX(), anchor.getY(), anchor.getZ());
                    cached.shape().forAllEdges((x1, y1, z1, x2, y2, z2) ->
                            Gizmos.line(base.add(x1, y1, z1), base.add(x2, y2, z2), argb, STROKE_WIDTH)
                                    .setAlwaysOnTop()
                                    .persistForMillis(PERSIST_MS));
                } else {
                    GizmoStyle style = GizmoStyle.stroke(argb, STROKE_WIDTH);
                    for (AABB box : cached.shape().toAabbs()) {
                        Gizmos.cuboid(box.move(anchor.getX(), anchor.getY(), anchor.getZ()), style)
                                .setAlwaysOnTop()
                                .persistForMillis(PERSIST_MS);
                    }
                }
            }
        } catch (Throwable t) {
            long now = System.currentTimeMillis();
            if (now - lastErrorLog >= ERROR_LOG_INTERVAL_MS) {
                lastErrorLog = now;
                LOGGER.error("Village Doctor outline rendering failed", t);
            }
        }
    }

    private static boolean lineGizmosSafe() {
        Boolean safe = lineGizmosSafe;
        if (safe == null) {
            safe = detectLineGizmoSupport();
            lineGizmosSafe = safe;
            LOGGER.info("Village Doctor outline mode: {}", safe ? "edge lines (26.2+)" : "cuboids (26.1.x-safe)");
        }
        return safe;
    }

    private static boolean detectLineGizmoSupport() {
        try {
            String[] parts = SharedConstants.getCurrentVersion().name().split("\\.");
            int major = Integer.parseInt(parts[0].trim());
            int minor = Integer.parseInt(parts[1].trim());
            return major > 26 || (major == 26 && minor >= 2);
        } catch (Throwable t) {
            return false;
        }
    }

    private static CachedShape shapeFor(UUID key, List<BlockPos> positions, Level level) {
        int statesHash = 1;
        for (BlockPos pos : positions) {
            statesHash = 31 * statesHash + System.identityHashCode(level.getBlockState(pos));
        }
        CachedShape cached = SHAPES.get(key);
        if (cached != null && cached.statesHash() == statesHash && cached.positions().equals(positions)) {
            return cached;
        }
        BlockPos anchor = positions.get(0);
        VoxelShape merged = Shapes.empty();
        for (BlockPos pos : positions) {
            VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
            if (shape.isEmpty()) {
                continue;
            }
            merged = Shapes.or(merged, shape.move(
                    pos.getX() - anchor.getX(), pos.getY() - anchor.getY(), pos.getZ() - anchor.getZ()));
        }
        cached = new CachedShape(List.copyOf(positions), statesHash, anchor, merged);
        SHAPES.put(key, cached);
        return cached;
    }
}
