package com.eun.invsee.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * usercache.json 名字↔UUID 缓存（离线玩家名解析）。
 * 服务端后端为 offline-mode + Velocity 现代转发，usercache 记录的是真实正版 UUID。
 */
public final class UserNameCache {

    private static final long TTL_MS = 15_000L;

    private static volatile List<NameAndId> cached = List.of();
    private static volatile long cachedAt = 0L;
    private static volatile Path cachedFile = null;

    private UserNameCache() {
    }

    public static List<NameAndId> snapshot(MinecraftServer server) {
        Path file = server.getServerDirectory().resolve("usercache.json");
        long now = System.currentTimeMillis();
        List<NameAndId> c = cached;
        if (cachedFile != null && cachedFile.equals(file) && c != null && now - cachedAt < TTL_MS) {
            return c;
        }
        List<NameAndId> fresh = read(file);
        cached = fresh;
        cachedAt = now;
        cachedFile = file;
        return fresh;
    }

    /** 按名字不区分大小写查找，返回 NameAndId（name 用缓存里的原始大小写）。 */
    public static NameAndId findByName(MinecraftServer server, String input) {
        String low = input.toLowerCase(Locale.ROOT);
        NameAndId firstHit = null;
        for (NameAndId n : snapshot(server)) {
            if (n.name().toLowerCase(Locale.ROOT).equals(low)) {
                if (n.name().equals(input)) {
                    return n; // 完全一致命中
                }
                if (firstHit == null) {
                    firstHit = n;
                }
            }
        }
        return firstHit;
    }

    public static List<String> fuzzyNames(MinecraftServer server, String input) {
        String low = input.toLowerCase(Locale.ROOT);
        List<String> hits = new ArrayList<>();
        for (NameAndId n : snapshot(server)) {
            if (n.name().toLowerCase(Locale.ROOT).contains(low)) {
                hits.add(n.name());
                if (hits.size() >= 10) {
                    break;
                }
            }
        }
        return hits;
    }

    private static List<NameAndId> read(Path file) {
        try {
            if (file == null || !Files.isRegularFile(file)) {
                return List.of();
            }
            String text = Files.readString(file);
            JsonArray arr = JsonParser.parseString(text).getAsJsonArray();
            List<NameAndId> out = new ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String name = o.has("name") ? o.get("name").getAsString() : null;
                String uuid = o.has("uuid") ? o.get("uuid").getAsString() : null;
                if (name == null || uuid == null || name.isEmpty()) {
                    continue;
                }
                try {
                    out.add(new NameAndId(UUID.fromString(uuid), name));
                } catch (IllegalArgumentException ignored) {
                    // 跳过坏行
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
