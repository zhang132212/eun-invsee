package com.eun.invsee.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Versioned UTF-8 JSON payloads. Requests and responses carry a correlation ID;
 * successful responses contain fixed-slot arrays encoded with ItemStack.CODEC.
 */
public final class InvseePackets {

    public static final String CHANNEL_NS = "eun_invsee";
    // At most 3 UTF-8 bytes per Java character; stay below the vanilla S2C payload limit.
    public static final int MAX_RESPONSE_CHARS = 262144;

    /** 客户端→服务端：请求查看某玩家。 */
    public record RequestPayload(String json) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<RequestPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CHANNEL_NS, "req"));

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> CODEC =
                StreamCodec.of(
                        (buf, payload) -> buf.writeUtf(payload.json(), 1024),
                        buf -> new RequestPayload(buf.readUtf(1024)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 服务端→客户端：查看结果。 */
    public record ResponsePayload(String json) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ResponsePayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CHANNEL_NS, "resp"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ResponsePayload> CODEC =
                StreamCodec.of(
                        (buf, payload) -> buf.writeUtf(payload.json(), MAX_RESPONSE_CHARS),
                        buf -> new ResponsePayload(buf.readUtf(MAX_RESPONSE_CHARS)));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static boolean typesRegistered = false;

    private InvseePackets() {
    }

    /** 每个进程只需注册一次（服务端进程/客户端进程各跑一次）。 */
    public static synchronized void ensureTypesRegistered() {
        if (typesRegistered) {
            return;
        }
        typesRegistered = true;
        PayloadTypeRegistry.serverboundPlay().register(RequestPayload.TYPE, RequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ResponsePayload.TYPE, ResponsePayload.CODEC);
    }
}
