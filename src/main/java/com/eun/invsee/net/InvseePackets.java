package com.eun.invsee.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 双端自定义协议。双向各携带一条 UTF-8 JSON：
 *  C2S req  = {"target":"玩家名"}
 *  S2C resp = {"ok":true,...} / {"ok":false,"err":"..."}
 * 与 command-gui-server 的做法一致：两侧可独立编译，未知字段各自忽略。
 */
public final class InvseePackets {

    public static final String CHANNEL_NS = "eun_invsee";

    /** 客户端→服务端：请求查看某玩家。 */
    public record RequestPayload(String json) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<RequestPayload> TYPE =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CHANNEL_NS, "req"));

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestPayload> CODEC =
                StreamCodec.of(
                        (buf, payload) -> buf.writeUtf(payload.json(), 65536),
                        buf -> new RequestPayload(buf.readUtf(65536)));

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
                        (buf, payload) -> buf.writeUtf(payload.json(), 1048576),
                        buf -> new ResponsePayload(buf.readUtf(1048576)));

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
