package dev.nothingitis.villagedoctor.network;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CLIENT-side store of the outline boxes the server sent us (v1.1 wireframe mode).
 * Pure data — no client-only classes, so it lives safely in common. Keyed exactly
 * like the server sends: villager UUID or a position-derived bell key.
 */
public final class ClientOutlineStore {

    public record Boxes(int colorRgb, List<BlockPos> positions) {
    }

    private static final Map<java.util.UUID, Boxes> BOXES = new ConcurrentHashMap<>();

    private ClientOutlineStore() {
    }

    public static void apply(OutlineDataPayload payload) {
        if (payload.positions().isEmpty()) {
            BOXES.remove(payload.key());
        } else {
            BOXES.put(payload.key(), new Boxes(payload.color(), List.copyOf(payload.positions())));
        }
    }

    public static Map<java.util.UUID, Boxes> all() {
        return BOXES;
    }

    public static void clear() {
        BOXES.clear();
    }
}
