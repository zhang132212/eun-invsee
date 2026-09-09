package com.eun.invsee.client.net;

import com.eun.invsee.client.gui.InvseeScreen;
import com.eun.invsee.data.InventorySnapshot;
import com.eun.invsee.net.InvseePackets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public final class ClientInvseeNet {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static InvseeScreen currentScreen;

    private ClientInvseeNet() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(InvseePackets.ResponsePayload.TYPE,
                (payload, context) -> context.client().execute(() -> apply(payload.json())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (currentScreen != null) {
                currentScreen.disconnected();
            }
            currentScreen = null;
        });
    }

    public static void setScreen(InvseeScreen screen) {
        currentScreen = screen;
    }

    public static void clearScreen(InvseeScreen screen) {
        if (currentScreen == screen) {
            currentScreen = null;
        }
    }

    public static long nextRequestId() {
        return SEQUENCE.incrementAndGet();
    }

    public static boolean available() {
        return Minecraft.getInstance().getConnection() != null
                && ClientPlayNetworking.canSend(InvseePackets.RequestPayload.TYPE);
    }

    public static void request(String target, long requestId) {
        JsonObject request = new JsonObject();
        request.addProperty("protocol", InventorySnapshot.PROTOCOL);
        request.addProperty("requestId", requestId);
        request.addProperty("target", target);
        ClientPlayNetworking.send(new InvseePackets.RequestPayload(request.toString()));
    }

    private static void apply(String json) {
        if (currentScreen == null) {
            return;
        }
        try {
            currentScreen.applyResponse(JsonParser.parseString(json).getAsJsonObject());
        } catch (Exception e) {
            LoggerFactory.getLogger("eun_invsee.client").warn("Invalid inventory response", e);
            currentScreen.queryFailed(Component.translatable("screen.eun_invsee.decode_error"));
        }
    }
}
