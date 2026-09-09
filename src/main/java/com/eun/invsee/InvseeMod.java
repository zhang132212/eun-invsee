package com.eun.invsee;

import com.eun.invsee.command.InvseeCommand;
import com.eun.invsee.net.InvseePackets;
import com.eun.invsee.server.InvseeServerNet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

/**
 * EUN Invsee - 背包查看（双端）。服务端：/invsee 聊天版 + 为客户端 GUI 提供数据；客户端另有界面。
 */
public class InvseeMod implements ModInitializer {

    public static final String MOD_ID = "eun_invsee";

    @Override
    public void onInitialize() {
        // Payload 类型在服务端/客户端进程各注册一次
        InvseePackets.ensureTypesRegistered();

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated, environment) ->
                InvseeCommand.register(dispatcher));

        // 服务端进程（非客户端环境）才接收 GUI 请求
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            InvseeServerNet.registerReceiver();
        }
    }
}
