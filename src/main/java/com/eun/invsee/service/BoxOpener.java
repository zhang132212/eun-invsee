package com.eun.invsee.service;

import com.eun.invsee.data.InventorySnapshot;
import com.eun.invsee.data.ContainerLayout;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Vanilla-client fallback for /invsee box. The displayed container contains only detached copies. */
public final class BoxOpener {
    private BoxOpener() {}

    public static int execute(CommandSourceStack source, String targetName, String code) {
        try {
            if (!(source.getEntity() instanceof ServerPlayer viewer)) {
                source.sendFailure(Component.literal("只有游戏内玩家可以打开容器界面，控制台请用 /invsee 查看文字内容。"));
                return 0;
            }
            var server = source.getServer();
            var target = TargetResolver.resolve(server, targetName);
            if (target.isEmpty()) {
                source.sendFailure(Component.literal("找不到玩家 " + targetName + "。"));
                return 0;
            }
            InventorySnapshot snapshot = SnapshotReader.read(target.get(), server);
            ItemStack stack = SnapshotReader.itemAt(snapshot, code);
            if (!InventorySnapshot.isContainer(stack)) {
                source.sendFailure(Component.literal("该位置没有可查看的容器。"));
                return 0;
            }
            var contents = InventorySnapshot.containerSlots(stack);
            var layout = ContainerLayout.of(stack, contents.size());
            if (ReadOnlyContainerMenu.menuType(layout) == null) {
                source.sendFailure(Component.literal("此容器没有匹配的原版预览菜单，请安装客户端模组并使用 R 键界面查看完整内容。"));
                return 0;
            }
            SimpleContainer copy = new SimpleContainer(contents.size());
            for (int i = 0; i < contents.size(); i++) copy.setItem(i, contents.get(i));
            viewer.openMenu(new MenuProvider() {
                @Override public Component getDisplayName() {
                    return Component.literal(snapshot.target() + " · ").append(stack.getHoverName()).append("（只读）");
                }
                @Override public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
                    return new ReadOnlyContainerMenu(syncId, inventory, copy, layout);
                }
            });
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("开箱查看失败：" + e.getMessage()));
            return 0;
        }
    }

}
