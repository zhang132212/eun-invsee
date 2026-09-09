package com.eun.invsee.service;

import com.mojang.serialization.DataResult;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.Container;

import java.util.Optional;

/**
 * /invsee box <玩家> <位置码>：把玩家背包里某格容器(潜影盒等)的内容，
 * 以"只读箱子界面"的形式在查看者客户端打开（类似 MiniHUD 容器预览的替代）。
 *
 * 位置码：h0..h8=快捷栏, m0..m26=背包, e0..e26=末影箱,
 * head/chest/legs/feet/body/offhand=装备与副手。
 *
 * 只读：界面里任何点击/快捷移动都被吞掉，不写任何容器与背包。
 */
public final class BoxOpener {

    private BoxOpener() {
    }

    public static int execute(CommandSourceStack src, String targetName, String code) {
        try {
            return open(src, targetName, code);
        } catch (Exception e) {
            src.sendFailure(Component.literal("§c开箱查看出错: " + e.getMessage()));
            return 0;
        }
    }

    private static int open(CommandSourceStack src, String targetName, String code) {
        Entity executor = src.getEntity();
        if (!(executor instanceof ServerPlayer viewer)) {
            src.sendFailure(Component.literal("§c只有游戏内玩家可以打开只读箱子界面，控制台请用 /invsee 看文字内容。"));
            return 0;
        }
        MinecraftServer server = src.getServer();

        Optional<TargetResolver.Resolved> maybe = TargetResolver.resolve(server, targetName);
        if (maybe.isEmpty()) {
            src.sendFailure(Component.literal("§c找不到玩家 §f" + targetName + "§c（不在线且不在 usercache 记录中）。"));
            return 0;
        }
        TargetResolver.Resolved r = maybe.get();
        String name = r.canonName();

        ItemStack box;
        if (r.online() != null) {
            box = stackOnline(r.online(), code);
        } else if (r.data() != null) {
            CompoundTag tag = compoundOffline(r.data(), code);
            if (tag == null) {
                src.sendFailure(Component.literal("§c在 §f" + name + "§c 的该位置没找到物品（离线存档）。"));
                return 0;
            }
            box = decodeStack(server, tag);
        } else {
            src.sendFailure(Component.literal("§c玩家 §f" + name + "§c 没有可读存档。"));
            return 0;
        }

        if (box == null || box.isEmpty()) {
            src.sendFailure(Component.literal("§c该位置没有物品。"));
            return 0;
        }

        ItemContainerContents cc = box.getComponents().get(DataComponents.CONTAINER);
        if (cc == null) {
            src.sendFailure(Component.literal("§c物品 " + idOf(server, box) + " 不是可打开的容器（无容器数据）。"));
            return 0;
        }

        // 用非空物品流逐格填入（不假设容器组件固定 27 格，避免越界）
        SimpleContainer boxContainer = new SimpleContainer(27);
        int slot = 0;
        for (ItemStack st : cc.nonEmptyItemCopyStream().limit(27).toList()) {
            boxContainer.setItem(slot++, st);
        }

        String title = "§6" + name + " §7的盒子内容（只读）";
        viewer.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal(title);
            }

            @Override
            public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
                return new ReadOnlyChestMenu(syncId, playerInventory, boxContainer);
            }
        });

        src.sendSuccess(() -> Component.literal("§a已打开 §f" + name + "§a 的容器内容查看（只读，点不动、不会改动任何物品）。"), false);
        return 1;
    }

    // ---------- 定位目标格子的真实物品 ----------

    private static ItemStack stackOnline(ServerPlayer p, String code) {
        char kind = code.charAt(0);
        if (kind == 'h') {
            return p.getInventory().getItem(indexOf(code));
        }
        if (kind == 'm') {
            return p.getInventory().getItem(9 + indexOf(code));
        }
        if (kind == 'e') {
            return p.getEnderChestInventory().getItem(indexOf(code));
        }
        EquipmentSlot slot = EquipmentSlot.byName(code);
        return p.getItemBySlot(slot);
    }

    private static CompoundTag compoundOffline(CompoundTag root, String code) {
        char kind = code.charAt(0);
        if (kind == 'h' || kind == 'm') {
            int wantSlot = kind == 'h' ? indexOf(code) : 9 + indexOf(code);
            ListTag inv = root.getListOrEmpty("Inventory");
            CompoundTag fallback = null;
            for (int i = 0; i < inv.size(); i++) {
                Tag t = inv.get(i);
                if (!(t instanceof CompoundTag entry)) {
                    continue;
                }
                int slot = entry.contains("Slot") ? (entry.getByteOr("Slot", (byte) 0) & 0xFF) : i;
                if (slot == wantSlot) {
                    String id = entry.getStringOr("id", "minecraft:air");
                    if (id.equals("minecraft:air") || entry.getIntOr("count", 0) <= 0) {
                        return null;
                    }
                    return entry;
                }
                if (slot > wantSlot && fallback == null) {
                    fallback = entry; // 无 Slot 标签时按序回退
                }
            }
            return null;
        }
        if (kind == 'e') {
            int wantSlot = indexOf(code);
            ListTag ender = root.getListOrEmpty("EnderItems");
            for (int i = 0; i < ender.size(); i++) {
                Tag t = ender.get(i);
                if (!(t instanceof CompoundTag entry)) {
                    continue;
                }
                int slot = entry.contains("Slot") ? (entry.getByteOr("Slot", (byte) 0) & 0xFF) : i;
                if (slot == wantSlot) {
                    String id = entry.getStringOr("id", "minecraft:air");
                    if (id.equals("minecraft:air") || entry.getIntOr("count", 0) <= 0) {
                        return null;
                    }
                    return entry;
                }
            }
            return null;
        }
        // 装备 / 副手
        CompoundTag eq = root.getCompoundOrEmpty("equipment");
        if (!eq.contains(code)) {
            return null;
        }
        CompoundTag it = eq.getCompoundOrEmpty(code);
        String id = it.getStringOr("id", "minecraft:air");
        if (id.equals("minecraft:air") || it.getIntOr("count", 0) <= 0) {
            return null;
        }
        return it;
    }

    private static int indexOf(String code) {
        return Integer.parseInt(code.substring(1));
    }

    // ---------- 离线 NBT → 真物品 ----------

    private static ItemStack decodeStack(MinecraftServer server, CompoundTag compound) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        DataResult<ItemStack> result = ItemStack.CODEC.parse(ops, compound);
        return result.result().orElse(null);
    }

    // ---------- 显示辅助 ----------

    private static String idOf(MinecraftServer server, ItemStack stack) {
        try {
            Registry<Item> reg = server.registryAccess().lookupOrThrow(Registries.ITEM);
            Identifier key = reg.getKey(stack.getItem());
            return key == null ? "?" : key.toString();
        } catch (Exception e) {
            return "?";
        }
    }

    // ---------- 只读箱子菜单 ----------

    private static final class ReadOnlyChestMenu extends ChestMenu {

        ReadOnlyChestMenu(int syncId, Inventory playerInventory, Container box) {
            super(MenuType.GENERIC_9x3, syncId, playerInventory, box, 3);
        }

        /** 吞掉一切点击/拖拽/快捷操作 —— 完全只读。 */
        @Override
        public void clicked(int slotId, int button, ContainerInput input, Player player) {
            // 无操作
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }
}
