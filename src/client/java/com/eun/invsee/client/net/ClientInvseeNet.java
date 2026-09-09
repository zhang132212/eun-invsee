package com.eun.invsee.client.net;

import com.eun.invsee.client.gui.InvseeScreen;
import com.eun.invsee.net.InvseePackets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 客户端网络：请求目标玩家背包数据，接收服务端 JSON 并交给打开的界面。
 */
public final class ClientInvseeNet {

    private static InvseeScreen currentScreen = null;

    private ClientInvseeNet() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(InvseePackets.ResponsePayload.TYPE,
                (payload, context) -> context.client().execute(() -> apply(payload.json())));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            currentScreen = null;
        });
    }

    public static void setScreen(InvseeScreen screen) {
        currentScreen = screen;
    }

    /** 请求查看某玩家（OP≥2）。结果到达后自动填到打开的界面。 */
    public static void request(String target) {
        JsonObject o = new JsonObject();
        o.addProperty("target", target);
        ClientPlayNetworking.send(new InvseePackets.RequestPayload(o.toString()));
    }

    private static void apply(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (currentScreen != null) {
                currentScreen.applyResponse(root);
            }
        } catch (Exception ignored) {
            // 畸形响应直接忽略
        }
    }
}
