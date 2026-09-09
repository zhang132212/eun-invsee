package com.eun.invsee.client;

import com.eun.invsee.client.gui.InvseeScreen;
import com.eun.invsee.client.net.ClientInvseeNet;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口：快捷键打开背包查看界面（OP 服务器配合读取在线/离线玩家数据）。
 */
public class InvseeClient implements ClientModInitializer {

    public static final Category CATEGORY = Category.register(
            Identifier.fromNamespaceAndPath("eun_invsee", "general"));

    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        ClientInvseeNet.init();

        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.eun_invsee.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.gui.screen() instanceof InvseeScreen open) {
                    open.onClose(); // 确保资源释放并解绑网络回调
                    client.gui.setScreen(null);
                } else if (client.gui.screen() == null) {
                    client.gui.setScreen(new InvseeScreen());
                }
            }
        });
    }
}
