package com.scadicat.launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ScadicatLauncher {

    public static final Path ROOT = ModpackSync.ROOT;
    public static final Path VERSIONS = ModpackSync.VERSIONS_DIR;
    public static final Path LIBS = ROOT.resolve("libraries");
    public static final Path ASSETS = ROOT.resolve("assets");
    public static final Path JRE_DIR = ROOT.resolve("jre");
    public static final String RESOURCES = "https://resources.download.minecraft.net";

    private final Consumer<String> logger;

    public ScadicatLauncher(Consumer<String> logger) {
        this.logger = logger;
        try {
            Files.createDirectories(VERSIONS);
            Files.createDirectories(LIBS);
            Files.createDirectories(ASSETS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** ⭐ ПРОВЕРКА И СКАЧИВАНИЕ ВСТРОЕННОЙ JAVA */
    public void installJavaIfNeeded() throws IOException {
        Path javaExe = JRE_DIR.resolve("bin").resolve("java.exe");
        if (Files.exists(javaExe)) {
            log("✓ Встроенная Java уже установлена");
            return;
        }

        log("⚠ Встроенная Java не найдена. Скачиваем...");
        log("Запрос к API Adoptium...");

        String apiUrl = "https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jre&os=windows";
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestProperty("User-Agent", "ScadicatLauncher/1.0");

        String downloadUrl = null;
        long expectedSize = -1;

        try (InputStream in = conn.getInputStream()) {
            JsonArray arr = com.google.gson.JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            if (arr.size() > 0) {
                JsonObject first = arr.get(0).getAsJsonObject();
                downloadUrl = first.getAsJsonObject("binary").getAsJsonObject("package").get("link").getAsString();
                expectedSize = first.getAsJsonObject("binary").getAsJsonObject("package").get("size").getAsLong();
            }
        }

        if (downloadUrl == null) throw new IOException("Не удалось получить ссылку на Java");

        log("⬇ Скачивание Java (" + (expectedSize / 1024 / 1024) + " МБ)...");
        Path zipFile = ROOT.resolve("java-temp.zip");

        final long fExpectedSize = expectedSize;

        Downloader.downloadForceFast(downloadUrl, zipFile, downloaded -> {
            if (downloaded % (10 * 1024 * 1024) < 256 * 1024) {
                log("⬇ Java: " + (downloaded / 1024 / 1024) + " МБ / " + (fExpectedSize / 1024 / 1024) + " МБ");
            }
        });

        log("📂 Распаковка Java...");
        extractJreZip(zipFile, JRE_DIR);
        Files.deleteIfExists(zipFile);
        log("✓ Встроенная Java успешно установлена!");
    }

    private void extractJreZip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace("\\", "/");
                if (name.contains("..")) continue;

                String[] parts = name.split("/");
                if (parts.length > 1) {
                    StringBuilder newName = new StringBuilder();
                    for (int i = 1; i < parts.length; i++) {
                        newName.append(parts[i]);
                        if (i < parts.length - 1) newName.append("/");
                    }
                    name = newName.toString();
                } else {
                    continue;
                }

                Path target = targetDir.resolve(name);
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream os = Files.newOutputStream(target)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = zis.read(buf)) != -1) os.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    // ============ УСТАНОВКА FORGE ============
    public void installForge(String mcVersion, String forgeVersion) throws IOException {
        log("═══════════════════════════════════════");
        log("=== FORGE INSTALL START ===");
        log("MC: " + mcVersion + " | Forge: " + forgeVersion);
        log("═══════════════════════════════════════");

        // 1. Убедимся, что родительская ванильная версия установлена
        Path parentJson = VERSIONS.resolve(mcVersion).resolve(mcVersion + ".json");
        if (!Files.exists(parentJson)) {
            log("Родительская версия " + mcVersion + " не установлена. Скачиваю...");
            installVanilla(mcVersion, null);
        }

        // 2. Создаем launcher_profiles.json (требование установщика Forge)
        Path profileFile = ROOT.resolve("launcher_profiles.json");
        if (!Files.exists(profileFile)) {
            log("Создаю launcher_profiles.json...");
            Files.writeString(profileFile, "{\"profiles\":{},\"settings\":{},\"version\":3}", StandardCharsets.UTF_8);
        }

        String fullVersion = mcVersion + "-" + forgeVersion;
        String installerName = "forge-" + fullVersion + "-installer.jar";
        String url = "https://maven.minecraftforge.net/net/minecraftforge/forge/" + fullVersion + "/" + installerName;
        Path installerPath = ROOT.resolve(installerName);

        log("⬇ Скачивание установщика Forge...");
        Downloader.downloadForceFast(url, installerPath, null);
        log("✓ Установщик скачан (" + (Files.size(installerPath) / 1024) + " KB)");

        String javaExe = findJava();
        log("Java: " + javaExe);
        log("Запуск установщика Forge...");

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.add("-jar");
        cmd.add(installerPath.toString());
        cmd.add("--installClient");
        cmd.add(ROOT.toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(ROOT.toFile());
        pb.redirectErrorStream(true);

        Process proc = pb.start();

        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                log("[Forge] " + line);
            }
        }

        try {
            int exitCode = proc.waitFor();
            if (exitCode == 0) {
                log("✅ Forge " + fullVersion + " успешно установлен!");
            } else {
                log("❌ Установщик Forge завершился с кодом: " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try {
            Files.deleteIfExists(installerPath);
            log("Установщик удалён.");
        } catch (IOException ignored) {}
    }

    // ============ УСТАНОВКА ВАНИЛИ (нужна для Forge) ============
    public void installVanilla(String versionId, Consumer<Integer> progress) throws IOException {
        log("=== Установка ванильной версии " + versionId + " ===");
        VersionManager vm = new VersionManager();
        JsonObject versionData = vm.fetchVersionJson(versionId);

        Path versionDir = VERSIONS.resolve(versionId);
        Files.createDirectories(versionDir);

        Path jsonPath = versionDir.resolve(versionId + ".json");
        Files.writeString(jsonPath, versionData.toString(), StandardCharsets.UTF_8);

        JsonObject downloads = versionData.getAsJsonObject("downloads");
        if (downloads.has("client")) {
            String url = downloads.getAsJsonObject("client").get("url").getAsString();
            Path jar = versionDir.resolve(versionId + ".jar");
            log("⬇ Скачивание клиента Minecraft...");
            Downloader.downloadForceFast(url, jar, null);
        }

        log("⬇ Скачивание библиотек...");
        JsonArray libs = versionData.getAsJsonArray("libraries");
        for (JsonElement el : libs) {
            JsonObject lib = el.getAsJsonObject();
            if (!checkRules(lib)) continue;

            JsonObject libDownloads = lib.getAsJsonObject("downloads");
            if (libDownloads == null) continue;

            if (libDownloads.has("artifact")) {
                JsonObject art = libDownloads.getAsJsonObject("artifact");
                Path target = LIBS.resolve(art.get("path").getAsString());
                try {
                    Downloader.downloadForceFast(art.get("url").getAsString(), target, null);
                } catch (IOException ignored) {}
            }

            if (libDownloads.has("classifiers")) {
                String key = nativeKey();
                JsonObject classifiers = libDownloads.getAsJsonObject("classifiers");
                if (classifiers.has(key)) {
                    JsonObject nat = classifiers.getAsJsonObject(key);
                    Path target = LIBS.resolve(nat.get("path").getAsString());
                    Downloader.downloadForceFast(nat.get("url").getAsString(), target, null);

                    Path nativesDir = versionDir.resolve("natives");
                    Files.createDirectories(nativesDir);
                    extractNatives(target, nativesDir);
                }
            }
        }
        log("Установка ванили завершена!");
    }

    // ============ ЗАПУСК ============
    public void launch(String versionId, String packName, String username) throws IOException {
        Settings settings = Settings.load();
        Path versionDir = VERSIONS.resolve(versionId);
        Path packDir = VERSIONS.resolve(packName);
        Path nativesDir = versionDir.resolve("natives");
        Path jsonPath = versionDir.resolve(versionId + ".json");

        Files.createDirectories(packDir);
        Files.createDirectories(nativesDir);

        JsonObject versionData = VersionManager.readJson(jsonPath.toUri().toString());

        if (versionData.has("inheritsFrom")) {
            String parentId = versionData.get("inheritsFrom").getAsString();
            JsonObject parentData = VersionManager.readJson(VERSIONS.resolve(parentId).resolve(parentId + ".json").toUri().toString());
            versionData = mergeJson(parentData, versionData);
        }

        String classpath = buildClasspath(versionId, versionData);
        String mainClass = versionData.get("mainClass").getAsString();
        String assetIndexId = versionData.getAsJsonObject("assetIndex").get("id").getAsString();
        String uuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");

        Map<String, String> vars = new HashMap<>();
        vars.put("auth_player_name", username);
        vars.put("version_name", versionId);
        vars.put("game_directory", packDir.toString());
        vars.put("assets_root", ASSETS.toString());
        vars.put("assets_index_name", assetIndexId);
        vars.put("auth_uuid", uuid);
        vars.put("auth_access_token", "0");
        vars.put("clientid", "scadicat");
        vars.put("auth_xuid", "0");
        vars.put("user_type", "legacy");
        vars.put("version_type", "release");
        vars.put("natives_directory", nativesDir.toString());
        vars.put("launcher_name", "scadicat");
        vars.put("launcher_version", "1.0");
        vars.put("classpath", classpath);
        vars.put("classpath_separator", File.pathSeparator);
        vars.put("library_directory", LIBS.toString());
        vars.put("resolution_width", "854");
        vars.put("resolution_height", "480");
        vars.put("quickPlayPath", "");
        vars.put("quickPlaySingleplayer", "");
        vars.put("quickPlayMultiplayer", "");
        vars.put("quickPlayRealms", "");

        List<String> cmd = new ArrayList<>();

        if (settings.javaPath != null && !settings.javaPath.isEmpty()) {
            cmd.add(settings.javaPath);
        } else {
            cmd.add(findJava());
        }

        cmd.add("-Xmx" + settings.ramMb + "M");
        cmd.add("-Xms" + Math.min(1024, settings.ramMb) + "M");
        cmd.add("-Djava.library.path=" + nativesDir);
        cmd.add("-Dminecraft.launcher.brand=scadicat");
        cmd.add("-Dminecraft.launcher.version=1.0");
        cmd.add("-Dfile.encoding=UTF-8");

        Path legacyCpFile = ROOT.resolve("legacy-classpath.txt");
        Files.write(legacyCpFile, Arrays.asList(classpath.split(File.pathSeparator)), StandardCharsets.UTF_8);

        String vanillaJarPrefix = versionData.has("inheritsFrom") ? versionData.get("inheritsFrom").getAsString() : versionId;

        if (versionData.has("arguments")) {
            JsonObject args = versionData.getAsJsonObject("arguments");
            if (args.has("jvm")) {
                for (JsonElement el : args.getAsJsonArray("jvm")) {
                    if (el.isJsonPrimitive()) {
                        String arg = replaceVars(el.getAsString(), vars);
                        if (arg.isEmpty()) continue;
                        if (arg.startsWith("-DlegacyClassPath=")) {
                            cmd.add("-DlegacyClassPath=" + legacyCpFile.toString());
                            continue;
                        }
                        if (arg.startsWith("-DignoreList=")) {
                            cmd.add(arg + "," + vanillaJarPrefix);
                            continue;
                        }
                        cmd.add(arg);
                    } else if (el.isJsonObject() && checkRule(el.getAsJsonObject())) {
                        addArgValue(cmd, el.getAsJsonObject(), vars);
                    }
                }
            }
        }

        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(mainClass);

        if (versionData.has("arguments")) {
            JsonObject args = versionData.getAsJsonObject("arguments");
            if (args.has("game")) {
                for (JsonElement el : args.getAsJsonArray("game")) {
                    if (el.isJsonPrimitive()) {
                        String arg = replaceVars(el.getAsString(), vars);
                        if (!arg.isEmpty()) cmd.add(arg);
                    } else if (el.isJsonObject() && checkRule(el.getAsJsonObject())) {
                        addArgValue(cmd, el.getAsJsonObject(), vars);
                    }
                }
            }
        }

        log("═══════════════════════════════════════");
        log("Запуск Minecraft " + versionId);
        log("Java: " + cmd.get(0));
        log("═══════════════════════════════════════");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(packDir.toFile());
        pb.redirectErrorStream(true);

        Process proc = pb.start();

        new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    System.out.println("[MC] " + line);
                    log("[MC] " + line);
                }
            } catch (IOException ignored) {}
        }).start();
    }

    private String buildClasspath(String versionId, JsonObject versionData) {
        String sep = File.pathSeparator;
        List<String> paths = new ArrayList<>();
        JsonArray libs = versionData.getAsJsonArray("libraries");
        for (JsonElement el : libs) {
            JsonObject lib = el.getAsJsonObject();
            if (!checkRules(lib)) continue;
            JsonObject dl = lib.getAsJsonObject("downloads");
            if (dl != null && dl.has("artifact")) {
                paths.add(LIBS.resolve(dl.getAsJsonObject("artifact").get("path").getAsString()).toString());
            }
        }
        Path clientJar = versionData.has("inheritsFrom")
                ? VERSIONS.resolve(versionData.get("inheritsFrom").getAsString()).resolve(versionData.get("inheritsFrom").getAsString() + ".jar")
                : VERSIONS.resolve(versionId).resolve(versionId + ".jar");
        paths.add(clientJar.toString());
        return String.join(sep, paths);
    }

    private void addArgValue(List<String> cmd, JsonObject obj, Map<String, String> vars) {
        if (!obj.has("value")) return;
        JsonElement val = obj.get("value");
        if (val.isJsonArray()) {
            for (JsonElement v : val.getAsJsonArray()) {
                String arg = replaceVars(v.getAsString(), vars);
                if (!arg.isEmpty()) cmd.add(arg);
            }
        } else {
            String arg = replaceVars(val.getAsString(), vars);
            if (!arg.isEmpty()) cmd.add(arg);
        }
    }

    private String replaceVars(String input, Map<String, String> vars) {
        String result = input;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("${" + e.getKey() + "}", e.getValue());
        }
        return result;
    }

    private boolean checkRule(JsonObject obj) {
        if (!obj.has("rules")) return true;
        String currentOs = osName();
        for (JsonElement el : obj.getAsJsonArray("rules")) {
            JsonObject rule = el.getAsJsonObject();
            boolean allow = "allow".equals(rule.get("action").getAsString());
            if (rule.has("features")) {
                JsonObject features = rule.getAsJsonObject("features");
                boolean match = true;
                if (features.has("is_demo_user") && features.get("is_demo_user").getAsBoolean()) match = false;
                if (features.has("has_custom_resolution") && features.get("has_custom_resolution").getAsBoolean()) match = false;
                if (features.has("has_quick_plays_support") && features.get("has_quick_plays_support").getAsBoolean()) match = false;
                if (features.has("is_quick_play_singleplayer") && features.get("is_quick_play_singleplayer").getAsBoolean()) match = false;
                if (features.has("is_quick_play_multiplayer") && features.get("is_quick_play_multiplayer").getAsBoolean()) match = false;
                if (features.has("is_quick_play_realms") && features.get("is_quick_play_realms").getAsBoolean()) match = false;
                if (!match) continue;
            }
            if (!rule.has("os")) return allow;
            else {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name") && os.get("name").getAsString().equals(currentOs)) return allow;
            }
        }
        return false;
    }

    private boolean checkRules(JsonObject lib) {
        if (!lib.has("rules")) return true;
        String currentOs = osName();
        boolean result = false;
        for (JsonElement el : lib.getAsJsonArray("rules")) {
            JsonObject rule = el.getAsJsonObject();
            boolean allow = "allow".equals(rule.get("action").getAsString());
            if (!rule.has("os")) result = allow;
            else {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name") && os.get("name").getAsString().equals(currentOs)) result = allow;
            }
        }
        return result;
    }

    private String osName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    private String nativeKey() {
        return switch (osName()) {
            case "windows" -> "natives-windows";
            case "osx" -> "natives-macos";
            default -> "natives-linux";
        };
    }

    private void extractNatives(Path zip, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("META-INF")) continue;
                if (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib") || name.endsWith(".jnilib")) {
                    String fileName = Paths.get(name).getFileName().toString();
                    Path out = targetDir.resolve(fileName);
                    try (OutputStream os = Files.newOutputStream(out)) {
                        zis.transferTo(os);
                    }
                }
            }
        }
    }

    private JsonObject mergeJson(JsonObject parent, JsonObject child) {
        JsonObject result = parent.deepCopy();
        for (Map.Entry<String, JsonElement> e : child.entrySet()) {
            String key = e.getKey();
            JsonElement value = e.getValue();
            if (key.equals("libraries") && value.isJsonArray()) {
                JsonArray merged = new JsonArray();
                if (result.has("libraries")) {
                    for (JsonElement el : result.getAsJsonArray("libraries")) merged.add(el);
                }
                for (JsonElement el : value.getAsJsonArray()) merged.add(el);
                result.add(key, merged);
            } else if (key.equals("arguments") && value.isJsonObject()) {
                JsonObject childArgs = value.getAsJsonObject();
                JsonObject parentArgs = result.has("arguments") ? result.getAsJsonObject("arguments") : new JsonObject();
                JsonObject mergedArgs = parentArgs.deepCopy();
                for (Map.Entry<String, JsonElement> ae : childArgs.entrySet()) {
                    String type = ae.getKey();
                    JsonElement val = ae.getValue();
                    if (val.isJsonArray()) {
                        JsonArray arr = mergedArgs.has(type) ? mergedArgs.getAsJsonArray(type) : new JsonArray();
                        for (JsonElement el : val.getAsJsonArray()) arr.add(el);
                        mergedArgs.add(type, arr);
                    } else mergedArgs.add(type, val);
                }
                result.add("arguments", mergedArgs);
            } else {
                result.add(key, value);
            }
        }
        return result;
    }

    private String findJava() {
        Path bundledJre = JRE_DIR.resolve("bin").resolve(isWindows() ? "java.exe" : "java");
        if (Files.exists(bundledJre)) return bundledJre.toString();

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null) {
            Path p = Paths.get(javaHome, "bin", isWindows() ? "java.exe" : "java");
            if (Files.exists(p)) return p.toString();
        }
        return "java";
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private void log(String msg) {
        if (logger != null) logger.accept(msg);
        System.out.println("[ScadicatLauncher] " + msg);
    }
}