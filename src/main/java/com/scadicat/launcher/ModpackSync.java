package com.scadicat.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModpackSync {

    public static final Path ROOT = Paths.get("C:\\scadicat");
    public static final Path VERSIONS_DIR = ROOT.resolve("versions");
    public static final Path UPDATE_FILE = ROOT.resolve("updatecommit.json");
    public static final Path VERSION_FILE = ROOT.resolve(".scadicat-version.txt");

    private static final String DEFAULT_OWNER = "ZeletA20-off";
    private static final String DEFAULT_REPO = "modskotiki";
    private static final String DEFAULT_BRANCH = "main";
    private static final String DEFAULT_PACK_NAME = "ScadicatPack";
    private static final String DEFAULT_MC_VERSION = "1.20.1";

    // ⭐ ЗАЩИЩЕННЫЕ ПАПКИ И ФАЙЛЫ (которые лаунчер НЕ будет трогать при распаковке)
    private static final Set<String> PROTECTED_PATHS = Set.of(
            "saves",          // Миры игрока
            "logs",           // Логи
            "options.txt",    // Настройки игры (управление, графика)
            "optionsshaders.txt",
            "servers.dat",    // Список серверов
            "screenshots",    // Скриншоты
            "journeymap",     // Карты JourneyMap
            "tacz"            // Конфиги и данные мода TACZ
    );

    private final Consumer<String> logger;

    public ModpackSync(Consumer<String> logger) {
        this.logger = logger;
    }

    public static class SyncResult {
        public String packName;
        public String minecraftVersion;
        public String remoteVersion;
        public Path packDir;
        public int downloaded;
        public boolean updated;
        public boolean changed;
    }

    public String checkRemoteVersion() throws IOException {
        JsonObject config = loadConfig();
        if (!config.has("release_url")) return null;

        String releaseUrl = config.get("release_url").getAsString();
        HttpURLConnection conn = (HttpURLConnection) new URL(releaseUrl).openConnection();
        conn.setRequestProperty("User-Agent", "ScadicatLauncher/1.0");
        conn.setRequestProperty("Accept", "application/vnd.github+json");

        String token = System.getenv("SCADICAT_GITHUB_TOKEN");
        if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);

        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("Не удалось получить релиз (HTTP " + code + ")");

        try (InputStream in = conn.getInputStream()) {
            JsonObject release = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            String tagName = release.has("tag_name") ? release.get("tag_name").getAsString() : "?";
            log("Последний релиз: " + tagName);
            return tagName;
        }
    }

    public String getLocalVersion() {
        try {
            if (Files.exists(VERSION_FILE)) return Files.readString(VERSION_FILE, StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {}
        return "0.0";
    }

    private void saveLocalVersion(String version) throws IOException {
        Files.writeString(VERSION_FILE, version, StandardCharsets.UTF_8);
    }

    public SyncResult sync() throws IOException {
        SyncResult result = new SyncResult();
        JsonObject config = loadConfig();

        String packName = config.has("pack_name") ? config.get("pack_name").getAsString() : "ScadicatPack";
        String mcVersion = config.has("minecraft_version") ? config.get("minecraft_version").getAsString() : null;

        result.packName = packName;
        result.minecraftVersion = mcVersion;
        result.packDir = VERSIONS_DIR.resolve(packName);

        String localVersion = getLocalVersion();
        String remoteVersion = null;

        log("═══════════════════════════════════════");
        log("Синхронизация модпака: " + packName);
        log("Локальная версия: " + localVersion);
        log("═══════════════════════════════════════");

        Files.createDirectories(result.packDir);

        if (config.has("release_url")) {
            try {
                remoteVersion = checkRemoteVersion();
                result.remoteVersion = remoteVersion;
            } catch (IOException ex) {
                log("⚠ Не удалось проверить релиз: " + ex.getMessage());
            }
        }

        if (remoteVersion != null && remoteVersion.equals(localVersion)) {
            log("✓ Версия актуальна (" + localVersion + ")");
            result.changed = false;
            return result;
        }

        if (remoteVersion != null && !remoteVersion.equals(localVersion)) {
            log("🔄 Доступна новая версия: " + remoteVersion);
            downloadAndExtractRelease(config, result.packDir);
            saveLocalVersion(remoteVersion);
            result.updated = true;
            result.changed = true;
            log("✅ Обновлено до " + remoteVersion);
            return result;
        }

        log("Релиз не найден, использую старую синхронизацию...");
        fallbackSync(config, result);
        return result;
    }

    private void downloadAndExtractRelease(JsonObject config, Path packDir) throws IOException {
        String releaseUrl = config.get("release_url").getAsString();
        HttpURLConnection conn = (HttpURLConnection) new URL(releaseUrl).openConnection();
        conn.setRequestProperty("User-Agent", "ScadicatLauncher/1.0");
        conn.setRequestProperty("Accept", "application/vnd.github+json");

        String token = System.getenv("SCADICAT_GITHUB_TOKEN");
        if (token != null && !token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);

        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code);

        String zipUrl = null;
        long zipSize = 0;

        try (InputStream in = conn.getInputStream()) {
            JsonObject release = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray assets = release.getAsJsonArray("assets");
            for (JsonElement el : assets) {
                JsonObject asset = el.getAsJsonObject();
                String name = asset.get("name").getAsString();
                if (name.toLowerCase().endsWith(".zip")) {
                    zipUrl = asset.get("browser_download_url").getAsString();
                    zipSize = asset.get("size").getAsLong();
                    log("📦 Найден архив: " + name + " (" + (zipSize / 1024 / 1024) + " МБ)");
                    break;
                }
            }
        }

        if (zipUrl == null) throw new IOException("В релизе нет .zip файла!");

        Path zipFile = ROOT.resolve("modpack-temp.zip");
        final long fZipSize = zipSize;

        log("⬇ Скачивание архива на максимальной скорости...");
        Downloader.downloadForceFast(zipUrl, zipFile, downloaded -> {
            if (downloaded % (10 * 1024 * 1024) < 256 * 1024) {
                log("⬇ " + (downloaded / 1024 / 1024) + " МБ / " + (fZipSize / 1024 / 1024) + " МБ");
            }
        });

        log("✓ Архив скачан: " + (Files.size(zipFile) / 1024 / 1024) + " МБ");

        log("📂 Распаковка...");
        int extracted = extractZip(zipFile, packDir);
        log("✓ Распаковано файлов: " + extracted);
        Files.deleteIfExists(zipFile);
    }

    // ⭐ ОБНОВЛЕННЫЙ МЕТОД РАСПАКОВКИ С ЗАЩИТОЙ
    private int extractZip(Path zipFile, Path targetDir) throws IOException {
        // Сначала проверим, есть ли внутри ZIP одна корневая папка
        String rootFolderPrefix = null;
        boolean hasFilesInRoot = false;
        boolean hasMultipleRoots = false;

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace("\\", "/");
                if (name.isEmpty()) continue;

                String[] parts = name.split("/");
                if (parts.length > 0) {
                    String firstPart = parts[0];
                    if (parts.length == 1 && !entry.isDirectory()) {
                        hasFilesInRoot = true;
                    } else if (parts.length > 1) {
                        if (rootFolderPrefix == null) {
                            rootFolderPrefix = firstPart;
                        } else if (!rootFolderPrefix.equals(firstPart)) {
                            hasMultipleRoots = true;
                        }
                    }
                }
            }
        }

        boolean skipFirstFolder = rootFolderPrefix != null && !hasMultipleRoots && !hasFilesInRoot;
        if (skipFirstFolder) {
            log("⚠ Обнаружена лишняя папка '" + rootFolderPrefix + "' в архиве. Распаковываю её содержимое напрямую...");
        }

        int count = 0;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile), 256 * 1024))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace("\\", "/");
                if (name.contains("..")) continue;

                if (skipFirstFolder) {
                    if (name.equals(rootFolderPrefix) || name.equals(rootFolderPrefix + "/")) {
                        continue;
                    }
                    if (name.startsWith(rootFolderPrefix + "/")) {
                        name = name.substring(rootFolderPrefix.length() + 1);
                    }
                }

                if (name.isEmpty()) continue;

                // ⭐ ПРОВЕРКА НА ЗАЩИЩЕННЫЕ ПАПКИ
                String topFolder = name.contains("/") ? name.substring(0, name.indexOf('/')) : name;
                if (PROTECTED_PATHS.contains(topFolder)) {
                    // Если это файл, который защищен, или папка - пропускаем
                    continue;
                }

                Path target = targetDir.resolve(name);

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(target), 256 * 1024)) {
                        byte[] buf = new byte[256 * 1024];
                        int n;
                        while ((n = zis.read(buf)) != -1) out.write(buf, 0, n);
                    }
                    count++;
                }
                zis.closeEntry();
            }
        }
        return count;
    }

    private void fallbackSync(JsonObject config, SyncResult result) throws IOException {
        log("Fallback синхронизация не реализована в этой версии.");
    }

    private JsonObject loadConfig() throws IOException {
        if (!Files.exists(UPDATE_FILE)) createDefaultConfig();
        return JsonParser.parseString(Files.readString(UPDATE_FILE, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private void createDefaultConfig() throws IOException {
        Files.createDirectories(ROOT);
        String json = "{\n"
                + "  \"version\": \"1.0\",\n"
                + "  \"github_owner\": \"" + DEFAULT_OWNER + "\",\n"
                + "  \"github_repo\": \"" + DEFAULT_REPO + "\",\n"
                + "  \"github_branch\": \"" + DEFAULT_BRANCH + "\",\n"
                + "  \"pack_name\": \"" + DEFAULT_PACK_NAME + "\",\n"
                + "  \"minecraft_version\": \"" + DEFAULT_MC_VERSION + "\",\n"
                + "  \"release_url\": \"https://api.github.com/repos/" + DEFAULT_OWNER + "/" + DEFAULT_REPO + "/releases/latest\",\n"
                + "  \"mods\": []\n"
                + "}\n";
        Files.writeString(UPDATE_FILE, json, StandardCharsets.UTF_8);
    }

    private void log(String msg) {
        if (logger != null) logger.accept(msg);
        System.out.println("[ModpackSync] " + msg);
    }
}