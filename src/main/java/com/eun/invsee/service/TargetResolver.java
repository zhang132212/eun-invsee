package com.eun.invsee.service;

import com.eun.invsee.data.UserNameCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 目标玩家解析：在线实体优先，其次 usercache → 离线存档。
 * 只读：不修改任何数据。
 */
public final class TargetResolver {

    public record Resolved(ServerPlayer online, String canonName, NameAndId rec,
                           CompoundTag data, long savedAtMs) {
    }

    private TargetResolver() {
    }

    public static Optional<Resolved> resolve(MinecraftServer server, String name) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getGameProfile().name().equalsIgnoreCase(name)) {
                return Optional.of(new Resolved(p, p.getGameProfile().name(), null, null, -1));
            }
        }
        NameAndId rec = UserNameCache.findByName(server, name);
        if (rec == null) {
            return Optional.empty();
        }
        CompoundTag data = server.getPlayerList().loadPlayerData(rec).orElse(null);
        long mtime = -1;
        try {
            Path f = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(rec.id() + ".dat");
            if (Files.isRegularFile(f)) {
                mtime = Files.getLastModifiedTime(f).toMillis();
            }
        } catch (Exception ignored) {
            // mtime 拿不到就算了
        }
        return Optional.of(new Resolved(null, rec.name(), rec, data, mtime));
    }
}
