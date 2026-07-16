package dev.nothingitis.villagedoctor.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Zero-dependency config: plain java.util.Properties at {@code config/villagedoctor.properties}.
 * Written with commented defaults on first launch; read once at startup.
 */
public final class VillageDoctorConfig {
    /** Allow crafting the Stethoscope (false = the recipe is not loaded; admin-distributed only). */
    public boolean craftingRecipe = true;
    /** Register the op-only /villagedoctor stethoscope give command (false = no command at all). */
    public boolean giveCommand = true;
    /** Permission level required to use the Stethoscope (0 = everyone, 2+ = ops). */
    public int permissionLevel = 0;
    /** Max simultaneously outlined villagers per player (colors recycle past the palette). */
    public int maxOutlined = 13;
    /** Add dark blue / dark gray / black to the palette (full 16, but hard to see). */
    public boolean useDarkColors = false;
    /**
     * Countdown (minutes) that starts when the outlined villager leaves visible range
     * (vanilla tracking range capped by view distance — hardcoded by design);
     * returning cancels it. 0 = never expire.
     */
    public int outlineExpireMinutes = 5;
    /** Search radius for bed/workstation owner lookup (48 = vanilla village radius). */
    public int ownerLookupRadius = 48;

    private static final String FILE_NAME = "villagedoctor.properties";

    public static VillageDoctorConfig load(Path configDir) {
        VillageDoctorConfig cfg = new VillageDoctorConfig();
        Path file = configDir.resolve(FILE_NAME);
        try {
            if (Files.notExists(file)) {
                writeDefault(file);
                return cfg;
            }
            Properties props = new Properties();
            try (Reader r = Files.newBufferedReader(file)) {
                props.load(r);
            }
            cfg.craftingRecipe = bool(props, "craftingRecipe", cfg.craftingRecipe);
            cfg.giveCommand = bool(props, "giveCommand", cfg.giveCommand);
            cfg.permissionLevel = clamp(intVal(props, "permissionLevel", cfg.permissionLevel), 0, 4);
            cfg.maxOutlined = clamp(intVal(props, "maxOutlined", cfg.maxOutlined), 1, 1024);
            cfg.useDarkColors = bool(props, "useDarkColors", cfg.useDarkColors);
            cfg.outlineExpireMinutes = clamp(intVal(props, "outlineExpireMinutes", cfg.outlineExpireMinutes), 0, 100000);
            cfg.ownerLookupRadius = clamp(intVal(props, "ownerLookupRadius", cfg.ownerLookupRadius), 4, 128);
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger("villagedoctor")
                    .warn("[Village Doctor] Could not read {} — using defaults ({})", file, e.toString());
        }
        return cfg;
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim().toLowerCase(Locale.ROOT));
    }

    private static int intVal(Properties p, String key, int def) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void writeDefault(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(file)) {
            w.write("""
                    # Village Doctor configuration
                    #
                    # Allow crafting the Stethoscope (false = recipe not loaded; admins hand it out instead).
                    craftingRecipe=true
                    # Register the /villagedoctor stethoscope give command (always op-only; false = no command).
                    giveCommand=true
                    # Permission level required to use the Stethoscope (0 = everyone, 2+ = ops only).
                    permissionLevel=0
                    #
                    # Outlines.
                    # Max simultaneously outlined villagers per player (colors recycle past the palette).
                    maxOutlined=13
                    # Add dark blue / dark gray / black to the palette (full 16, but hard to see).
                    useDarkColors=false
                    # An outline never expires while the villager is in visible range (vanilla
                    # tracking range). Once it leaves visible range this countdown (minutes)
                    # starts; coming back cancels it. 0 = outlines only clear manually/on logout.
                    outlineExpireMinutes=5
                    #
                    # Search radius for bed/workstation owner lookup (48 = vanilla village radius).
                    ownerLookupRadius=48
                    """);
        }
    }
}
