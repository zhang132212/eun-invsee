package com.eun.invsee.server;

import com.eun.invsee.net.InvseePackets;
import com.eun.invsee.service.PlayerViews;
import com.eun.invsee.service.TargetResolver;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * 服务端：接收客户端 /invsee GUI 的请求（C2S req），
 * OP(等级≥2)校验通过后读数据（在线实时/离线存档），以 JSON(S2C resp)回传。只读。
 */
public final class InvseeServerNet {

    private InvseeServerNet() {
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(
                InvseePackets.RequestPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    MinecraftServer server = context.server();
                    if (player == null || server == null) {
                        return;
                    }
                    if (!Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
                        sendError(player, "权限不足：需要 OP(等级≥2)。");
                        return;
                    }
                    String target;
                    try {
                        target = JsonParser.parseString(payload.json())
                                .getAsJsonObject()
                                .get("target")
                                .getAsString();
                    } catch (Exception e) {
                        sendError(player, "请求格式错误。");
                        return;
                    }
                    handle(player, server, target);
                });
    }

    private static void handle(ServerPlayer requester, MinecraftServer server, String target) {
        Optional<TargetResolver.Resolved> maybe = TargetResolver.resolve(server, target);
        if (maybe.isEmpty()) {
            sendError(requester, "未找到玩家 " + target + "（不在线且不在 usercache 记录中）。");
            return;
        }
        TargetResolver.Resolved r = maybe.get();

        try {
            String out;
            if (r.online() != null) {
                out = PlayerViews.jsonOnline(r.online(), server);
            } else if (r.data() == null) {
                sendError(requester, "玩家 " + r.canonName() + " 没有可读存档。");
                return;
            } else {
                out = PlayerViews.jsonOffline(r.data(), server, r.savedAtMs(), r.canonName());
            }
            ServerPlayNetworking.send(requester, new InvseePackets.ResponsePayload(out));
        } catch (Exception e) {
            sendError(requester, "读取失败: " + e.getMessage());
        }
    }

    private static void sendError(ServerPlayer player, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("err", message);
        ServerPlayNetworking.send(player, new InvseePackets.ResponsePayload(o.toString()));
    }
}
