package com.eun.invsee.service;

import com.eun.invsee.data.InventorySnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天指令视图，和 GUI 共用 SnapshotReader 的物品解析与槽位模型。
 * 只读：任何路径都不写背包/存档。
 */
public final class PlayerViews {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PlayerViews() {
    }

    // ================= 在线 =================

    public static List<Component> viewOnline(ServerPlayer p, MinecraftServer server) {
        Collector c = collectOnline(p, server);
        List<Component> lines = base(p.getGameProfile().name(), "§a在线 · 实时（非存档快照）");
        lines.addAll(assemble(c, p.getGameProfile().name()));
        return lines;
    }

    private static Collector collectOnline(ServerPlayer p, MinecraftServer server) {
        return collect(SnapshotReader.online(p), server);
    }

    // ================= 离线 =================

    public static List<Component> viewOffline(CompoundTag root, MinecraftServer server, long savedAtMs, String name) {
        Collector c = collectOffline(root, server);
        String saved = savedAtMs > 0 ? formatTime(savedAtMs) : "未知";
        List<Component> lines = base(name, "§7离线 · 存档时间 " + saved);
        lines.addAll(assemble(c, name));
        return lines;
    }

    private static Collector collectOffline(CompoundTag root, MinecraftServer server) {
        return collect(SnapshotReader.offline(root, server.registryAccess(), -1, ""), server);
    }

    private static Collector collect(InventorySnapshot snapshot, MinecraftServer server) {
        Collector c = new Collector();
        c.selected = snapshot.selected();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = snapshot.inventory().get(i);
            if (!stack.isEmpty()) c.addBySlot(i, ItemView.fromStack(stack, server));
        }
        String[] labels = {"头盔", "胸甲", "护腿", "靴子", "身体", "副手"};
        for (int i = 0; i < 6; i++) {
            ItemStack stack = snapshot.equipment().get(i);
            if (stack.isEmpty()) continue;
            ItemView view = ItemView.fromStack(stack, server);
            if (i == 5) c.offhand = view;
            else c.armor.add(new Labeled(labels[i], InventorySnapshot.EQUIPMENT_KEYS.get(i), view));
        }
        for (int i = 0; i < 27; i++) {
            ItemStack stack = snapshot.ender().get(i);
            if (!stack.isEmpty()) {
                c.ender[i] = ItemView.fromStack(stack, server);
                c.enderFilled++;
            }
        }
        return c;
    }

    // ================= 聊天组装 =================

    private static List<Component> base(String name, String source) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal("§6========= §f" + name + " §6的背包查看 ========="));
        out.add(Component.literal("§7来源: ").append(source));
        return out;
    }

    private static String formatTime(long ms) {
        return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(TIME_FMT);
    }

    private static List<Component> assemble(Collector c, String targetName) {
        List<Component> lines = new ArrayList<>();
        boolean any = false;

        if (c.hotFilled > 0) {
            any = true;
            lines.add(Component.literal("§6—— 快捷栏 (§e" + c.hotFilled + "§6/9) ——"));
            for (int i = 0; i < 9; i++) {
                ItemView iv = c.hot[i];
                if (iv == null) {
                    continue;
                }
                String color = (i == c.selected) ? "§e" : "§7";
                lines.add(itemLine(color + "[快" + (i + 1) + "] ", iv, "h" + i, targetName));
            }
        }
        if (c.mainFilled > 0) {
            any = true;
            lines.add(Component.literal("§6—— 背包 (§e" + c.mainFilled + "§6/27) ——"));
            for (int i = 0; i < 27; i++) {
                ItemView iv = c.main[i];
                if (iv == null) {
                    continue;
                }
                lines.add(itemLine("§7[包" + (i + 1) + "] ", iv, "m" + i, targetName));
            }
        }
        if (!c.armor.isEmpty()) {
            any = true;
            lines.add(Component.literal("§6—— 装备 ——"));
            for (Labeled l : c.armor) {
                lines.add(itemLine("§7[" + l.label() + "] ", l.view(), l.code(), targetName));
            }
        }
        if (c.offhand != null) {
            any = true;
            lines.add(Component.literal("§6—— 副手 ——"));
            lines.add(itemLine("§7[副手] ", c.offhand, "offhand", targetName));
        }

        // 末影箱始终展示
        lines.add(Component.literal("§6—— 末影箱 (§e" + c.enderFilled + "§6/27) ——"));
        if (c.enderFilled > 0) {
            any = true;
            for (int i = 0; i < 27; i++) {
                ItemView iv = c.ender[i];
                if (iv == null) {
                    continue;
                }
                lines.add(itemLine("§7[末" + (i + 1) + "] ", iv, "e" + i, targetName));
            }
        } else {
            lines.add(Component.literal("§8（空）"));
        }

        if (!any) {
            lines.add(Component.literal("§8（该玩家背包/装备/末影箱均为空）"));
        }
        return lines;
    }

    /** 一行物品；若是含容器的物品，追加可点击的 [开箱]。 */
    private static MutableComponent itemLine(String prefix, ItemView iv, String code, String targetName) {
        MutableComponent line = Component.literal(prefix)
                .append(iv.withTooltip())
                .append(Component.literal(" §7×" + iv.count + iv.inlineSuffix()));
        if (iv.innerTotal > 0) {
            final String cmd = "/invsee box " + targetName + " " + code;
            line.append(Component.literal(" §b[开箱]").withStyle(st -> st
                    .withClickEvent(new ClickEvent.RunCommand(cmd))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Component.literal("§7点击在客户端打开只读容器界面，查看盒内物品")))));
        }
        return line;
    }

    private record Labeled(String label, String code, ItemView view) {
    }

    private static final class Collector {
        final ItemView[] hot = new ItemView[9];
        final ItemView[] main = new ItemView[27];
        final ItemView[] ender = new ItemView[27];
        final List<Labeled> armor = new ArrayList<>();
        ItemView offhand;
        int selected = -1;
        int hotFilled = 0;
        int mainFilled = 0;
        int enderFilled = 0;

        void addBySlot(int slot, ItemView iv) {
            if (slot >= 0 && slot < 9) {
                if (hot[slot] == null) {
                    hot[slot] = iv;
                    hotFilled++;
                }
            } else if (slot >= 9 && slot < 36) {
                int idx = slot - 9;
                if (idx < 27 && main[idx] == null) {
                    main[idx] = iv;
                    mainFilled++;
                }
            }
            // 其余槽位忽略
        }
    }
}
