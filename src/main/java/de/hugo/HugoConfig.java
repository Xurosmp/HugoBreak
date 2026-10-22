package de.hugo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class HugoConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("hugo.json");

    public boolean enabled = true;
    /** Reparatur-Command ohne fuehrenden Slash, z.B. "fix" oder "home repair". */
    public String command = "fix";
    /** true = Schwelle in Prozent, false = Schwelle in verbleibenden Haltbarkeitspunkten. */
    public boolean percentMode = true;
    public int threshold = 5;
    public int cooldownSeconds = 5;
    public boolean checkMainHand = true;
    public boolean checkOffHand = true;
    public boolean checkArmor = true;
    public boolean hudEnabled = true;
    public int hudX = 4;
    public int hudY = 4;

    // --- Rueckkehr-Home ---
    /** Vor der Reparatur ein temporaeres Home setzen, danach zurueck teleportieren und Home loeschen. */
    public boolean returnHome = true;
    public String tempHomeName = "hugoback";
    /** {name} wird durch tempHomeName ersetzt. */
    public String setHomeCommand = "sethome {name}";
    public String tpHomeCommand = "home {name}";
    public String delHomeCommand = "delhome {name}";
    /** Pause zwischen den Commands (Sekunden), damit der Server sie sauber verarbeitet. */
    public double stepDelaySeconds = 1.0;
    /** Maximale Wartezeit (Sekunden) auf volle Haltbarkeit. Danach geht es auch ohne volles Item zurueck. */
    public int repairWaitSeconds = 60;

    public static HugoConfig load() {
        try {
            if (Files.exists(FILE)) {
                HugoConfig cfg = GSON.fromJson(Files.readString(FILE), HugoConfig.class);
                if (cfg != null) {
                    return cfg;
                }
            }
        } catch (Exception e) {
            System.err.println("[Hugo] Config konnte nicht geladen werden: " + e.getMessage());
        }
        HugoConfig cfg = new HugoConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException e) {
            System.err.println("[Hugo] Config konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    public static String clean(String raw) {
        String c = raw == null ? "" : raw.trim();
        while (c.startsWith("/")) {
            c = c.substring(1);
        }
        return c;
    }

    public String cleanCommand() {
        return clean(command);
    }

    public String withName(String template) {
        String name = tempHomeName == null || tempHomeName.isBlank() ? "hugoback" : tempHomeName.trim();
        return clean(template == null ? "" : template.replace("{name}", name));
    }
}
