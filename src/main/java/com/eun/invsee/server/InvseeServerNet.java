package com.eun.invsee.server;

import com.eun.invsee.data.InventorySnapshot;
import com.eun.invsee.net.InvseePackets;
import com.eun.invsee.service.SnapshotReader;
import com.eun.invsee.service.TargetResolver;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InvseeServerNet {
    private static final Logger LOG = LoggerFactory.getLogger("eun_invsee");
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static final long REQUEST_INTERVAL_NS = 500_000_000L;

    private InvseeServerNet() {}

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(InvseePackets.RequestPayload.TYPE,
                (payload, context) -> context.server().execute(() -> handle(context.player(), context.server(), payload.json())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_REQUEST.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_REQUEST.clear());
    }

    private static void handle(ServerPlayer player, MinecraftServer server, String json) {
        long requestId = -1;
        try {
            JsonObject request = JsonParser.parseString(json).getAsJsonObject();
            requestId = request.has("requestId") ? request.get("requestId").getAsLong() : -1;
            if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                sendError(player, requestId, "权限不足：需要 OP 等级 2 或以上。");
                return;
            }
            if (!request.has("protocol") || request.get("protocol").getAsInt() != InventorySnapshot.PROTOCOL) {
                sendError(player, requestId, "请将客户端和服务端的 EUN Invsee 一起更新到 1.1.0 或以上。");
                return;
            }
            long now = System.nanoTime();
            Long last = LAST_REQUEST.get(player.getUUID());
            if (last != null && now - last < REQUEST_INTERVAL_NS) {
                sendError(player, requestId, "查询太快，请稍后重试。");
                return;
            }
            LAST_REQUEST.put(player.getUUID(), now);
            String target = request.get("target").getAsString().trim();
            if (target.isEmpty() || target.length() > 32) {
                sendError(player, requestId, "请输入有效的玩家名（最多 32 个字符）。");
                return;
            }
            var resolved = TargetResolver.resolve(server, target);
            if (resolved.isEmpty()) {
                sendError(player, requestId, "找不到玩家 " + target + "。离线玩家需在本服 usercache 中有记录。");
                return;
            }
            JsonObject response = SnapshotReader.read(resolved.get(), server).toJson(server.registryAccess());
            response.addProperty("requestId", requestId);
            String out = response.toString();
            if (out.length() > InvseePackets.MAX_RESPONSE_CHARS) {
                sendError(player, requestId, "该玩家物品数据过大，请用 /invsee 指令分项查看。");
                return;
            }
            ServerPlayNetworking.send(player, new InvseePackets.ResponsePayload(out));
        } catch (Exception e) {
            LOG.warn("Unable to read inventory for request {}", requestId, e);
            sendError(player, requestId, "读取失败，请检查玩家存档及服务端日志。");
        }
    }

    private static void sendError(ServerPlayer player, long requestId, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("protocol", InventorySnapshot.PROTOCOL);
        response.addProperty("requestId", requestId);
        response.addProperty("ok", false);
        response.addProperty("err", message);
        ServerPlayNetworking.send(player, new InvseePackets.ResponsePayload(response.toString()));
    }
}
