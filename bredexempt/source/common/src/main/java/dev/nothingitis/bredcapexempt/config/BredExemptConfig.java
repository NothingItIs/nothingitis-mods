package dev.nothingitis.bredcapexempt.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Zero-dependency config: plain java.util.Properties at {@code config/bredexempt.properties}.
 * Written with commented defaults on first launch; read once at startup.
 */
public final class BredExemptConfig {
    public boolean exemptOffspring = true;
    public boolean exemptParents = true;
    public boolean exemptEggHatchedChicks = true;
    /** Entity ids ({@code minecraft:cow}) or tag refs ({@code #somepack:farm_animals}).
     *  Non-empty allowlist (or a non-empty {@code #bredexempt:allowed} tag) restricts
     *  exemption to members; empty means every animal is eligible. */
    public List<String> allowlist = List.of();
    /** Same syntax; denied entries are never exempted. Denial always wins. */
    public List<String> denylist = List.of();

    private static final String FILE_NAME = "bredexempt.properties";

    public static BredExemptConfig load(Path configDir) {
        BredExemptConfig cfg = new BredExemptConfig();
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
            cfg.exemptOffspring = bool(props, "exemptOffspring", cfg.exemptOffspring);
            cfg.exemptParents = bool(props, "exemptParents", cfg.exemptParents);
            cfg.exemptEggHatchedChicks = bool(props, "exemptEggHatchedChicks", cfg.exemptEggHatchedChicks);
            cfg.allowlist = list(props, "allowlist");
            cfg.denylist = list(props, "denylist");
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger("bredexempt")
                    .warn("[BredExempt] Could not read {} — using defaults ({})", file, e.toString());
        }
        return cfg;
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim().toLowerCase(Locale.ROOT));
    }

    private static List<String> list(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : v.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return List.copyOf(out);
    }

    private static void writeDefault(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(file)) {
            w.write("""
                    # BredExempt configuration
                    #
                    # Which animals get marked persistent (= no longer count toward the
                    # passive mob cap) when the event happens:
                    exemptOffspring=true
                    exemptParents=true
                    exemptEggHatchedChicks=true
                    #
                    # Optional entity filters. Comma-separated entity ids (minecraft:cow)
                    # and/or tag refs (#somepack:farm_animals).
                    #   denylist: never exempted (denial always wins).
                    #   allowlist: if non-empty, ONLY members are exempted.
                    # The data-driven tags #bredexempt:allowed / #bredexempt:denied work the
                    # same way and combine with these lists (for modpack datapacks).
                    allowlist=
                    denylist=
                    """);
        }
    }
}
