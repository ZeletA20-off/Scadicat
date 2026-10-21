package com.scadicat.launcher;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Settings {

    private static final Path FILE = ModpackSync.ROOT.resolve("settings.json");

    public int ramMb = 4096;
    public String javaPath = null;
    public String username = "Player";

    public static Settings load() {
        Settings s = new Settings();
        try {
            if (!Files.exists(FILE)) return s;
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();

            if (o.has("ram_mb")) s.ramMb = o.get("ram_mb").getAsInt();
            if (o.has("java_path") && !o.get("java_path").isJsonNull()) {
                s.javaPath = o.get("java_path").getAsString();
            }
            if (o.has("username")) s.username = o.get("username").getAsString();
        } catch (Exception ex) {
            System.err.println("Не удалось загрузить settings.json: " + ex.getMessage());
        }
        return s;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("ram_mb", ramMb);
            if (javaPath != null) o.addProperty("java_path", javaPath);
            else o.add("java_path", JsonNull.INSTANCE);
            o.addProperty("username", username);
            Files.writeString(FILE, o.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("Не удалось сохранить settings.json: " + ex.getMessage());
        }
    }

    public static long getTotalRamMb() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean)
                            ManagementFactory.getOperatingSystemMXBean();
            return os.getTotalMemorySize() / (1024 * 1024);
        } catch (Throwable t) {
            return 8192;
        }
    }

    public static long getFreeRamMb() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean)
                            ManagementFactory.getOperatingSystemMXBean();
            return os.getFreeMemorySize() / (1024 * 1024);
        } catch (Throwable t) {
            return 4096;
        }
    }

    public static long getMaxRamForMcMb() {
        long total = getTotalRamMb();
        long max = total;
        if (max < 1024) max = 1024;
        return max;
    }

    public static long getRecommendedRamMb() {
        long max = getMaxRamForMcMb();
        long rec = (long) (max * 0.85);
        rec = (rec / 512) * 512;
        if (rec < 2048) rec = 2048;
        return rec;
    }
}