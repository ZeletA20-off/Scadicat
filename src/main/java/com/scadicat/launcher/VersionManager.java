package com.scadicat.launcher;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class VersionManager {

    public static final String MANIFEST_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    public List<String> fetchVersionList() throws IOException {
        JsonObject root = readJson(MANIFEST_URL);
        List<String> out = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("versions")) {
            JsonObject v = el.getAsJsonObject();
            if ("release".equals(v.get("type").getAsString())) {
                out.add(v.get("id").getAsString());
            }
        }
        return out;
    }

    public JsonObject fetchVersionManifest() throws IOException {
        return readJson(MANIFEST_URL);
    }

    public JsonObject fetchVersionJson(String versionId) throws IOException {
        JsonObject manifest = fetchVersionManifest();
        for (JsonElement el : manifest.getAsJsonArray("versions")) {
            JsonObject v = el.getAsJsonObject();
            if (v.get("id").getAsString().equals(versionId)) {
                return readJson(v.get("url").getAsString());
            }
        }
        throw new IOException("Версия не найдена: " + versionId);
    }

    public static JsonObject readJson(String url) throws IOException {
        try (InputStream in = new URL(url).openStream();
             Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }
}