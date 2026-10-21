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
    /** Command ohne fuehrenden Slash, z.B. "fix" oder "repair". */
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

    public String cleanCommand() {
        String c = command == null ? "" : command.trim();
        while (c.startsWith("/")) {
            c = c.substring(1);
        }
        return c;
    }
}
