package com.eun.invsee.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 把"在线实体"或"离线存档 NBT"统一解析为：
 *  1) 聊天行（服务端 /invsee 文字版）
 *  2) 结构化 JSON（双端客户端界面版）
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

    public static String jsonOnline(ServerPlayer p, MinecraftServer server) {
        Collector c = collectOnline(p, server);
        return toJson(c, p.getGameProfile().name(), true, -1);
    }

    private static Collector collectOnline(ServerPlayer p, MinecraftServer server) {
        Collector c = new Collector();
        c.selected = p.getInventory().getSelectedSlot();

        Inventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty()) {
                c.addBySlot(i, ItemView.fromStack(st, server));
            }
        }
        addEquipLive(c, p, EquipmentSlot.HEAD, "头盔", server);
        addEquipLive(c, p, EquipmentSlot.CHEST, "胸甲", server);
        addEquipLive(c, p, EquipmentSlot.LEGS, "护腿", server);
        addEquipLive(c, p, EquipmentSlot.FEET, "靴子", server);
        addEquipLive(c, p, EquipmentSlot.BODY, "身体", server);
        ItemStack off = p.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!off.isEmpty()) {
            c.offhand = ItemView.fromStack(off, server);
        }

        PlayerEnderChestContainer ender = p.getEnderChestInventory();
        int es = Math.min(ender.getContainerSize(), 27);
        for (int i = 0; i < es; i++) {
            ItemStack st = ender.getItem(i);
            if (!st.isEmpty()) {
                c.ender[i] = ItemView.fromStack(st, server);
                c.enderFilled++;
            }
        }
        return c;
    }

    private static void addEquipLive(Collector c, ServerPlayer p, EquipmentSlot slot, String label,
                                     MinecraftServer server) {
        ItemStack st = p.getItemBySlot(slot);
        if (!st.isEmpty()) {
            c.armor.add(new Labeled(label, slot.getSerializedName(), ItemView.fromStack(st, server)));
        }
    }

    // ================= 离线 =================

    public static List<Component> viewOffline(CompoundTag root, MinecraftServer server, long savedAtMs, String name) {
        Collector c = collectOffline(root, server);
        String saved = savedAtMs > 0 ? formatTime(savedAtMs) : "未知";
        List<Component> lines = base(name, "§7离线 · 存档时间 " + saved);
        lines.addAll(assemble(c, name));
        return lines;
    }

    public static String jsonOffline(CompoundTag root, MinecraftServer server, long savedAtMs, String name) {
        Collector c = collectOffline(root, server);
        return toJson(c, name, false, savedAtMs);
    }

    private static Collector collectOffline(CompoundTag root, MinecraftServer server) {
        Collector c = new Collector();
        c.selected = root.getIntOr("SelectedItemSlot", -1);

        ListTag invList = root.getListOrEmpty("Inventory");
        for (int i = 0; i < invList.size(); i++) {
            Tag t = invList.get(i);
            if (!(t instanceof CompoundTag entry)) {
                continue;
            }
            int slot = entry.contains("Slot") ? (entry.getByteOr("Slot", (byte) 0) & 0xFF) : i;
            String id = entry.getStringOr("id", "minecraft:air");
            if (id.equals("minecraft:air") || entry.getIntOr("count", 0) <= 0) {
                continue;
            }
            c.addBySlot(slot, ItemView.fromItemCompound(entry, server));
        }

        CompoundTag eq = root.getCompoundOrEmpty("equipment");
        addEquipTag(c, eq, EquipmentSlot.HEAD, "头盔", server);
        addEquipTag(c, eq, EquipmentSlot.CHEST, "胸甲", server);
        addEquipTag(c, eq, EquipmentSlot.LEGS, "护腿", server);
        addEquipTag(c, eq, EquipmentSlot.FEET, "靴子", server);
        addEquipTag(c, eq, EquipmentSlot.BODY, "身体", server);
        addEquipTag(c, eq, EquipmentSlot.OFFHAND, "副手", server);

        ListTag enderList = root.getListOrEmpty("EnderItems");
        for (int i = 0; i < enderList.size(); i++) {
            Tag t = enderList.get(i);
            if (!(t instanceof CompoundTag entry)) {
                continue;
            }
            int slot = entry.contains("Slot") ? (entry.getByteOr("Slot", (byte) 0) & 0xFF) : i;
            if (slot < 0 || slot >= 27) {
                continue;
            }
            String id = entry.getStringOr("id", "minecraft:air");
            if (id.equals("minecraft:air") || entry.getIntOr("count", 0) <= 0) {
                continue;
            }
            if (c.ender[slot] == null) {
                c.ender[slot] = ItemView.fromItemCompound(entry, server);
                c.enderFilled++;
            }
        }
        return c;
    }

    private static void addEquipTag(Collector c, CompoundTag eq, EquipmentSlot slot, String label,
                                    MinecraftServer server) {
        String key = slot.getSerializedName();
        if (!eq.contains(key)) {
            return;
        }
        CompoundTag it = eq.getCompoundOrEmpty(key);
        String id = it.getStringOr("id", "minecraft:air");
        if (id.equals("minecraft:air") || it.getIntOr("count", 0) <= 0) {
            return;
        }
        ItemView iv = ItemView.fromItemCompound(it, server);
        if (slot == EquipmentSlot.OFFHAND) {
            c.offhand = iv;
        } else {
            c.armor.add(new Labeled(label, key, iv));
        }
    }

    // ================= JSON（双端客户端界面） =================

    private static String toJson(Collector c, String name, boolean online, long savedAtMs) {
        JsonObject root = new JsonObject();
        root.addProperty("ok", true);
        root.addProperty("target", name);
        root.addProperty("online", online);
        root.addProperty("savedAt", savedAtMs);
        JsonArray groups = new JsonArray();

        // 快捷栏
        JsonObject hot = group("快捷栏", "hot", c.hotFilled, 9);
        for (int i = 0; i < 9; i++) {
            ItemView iv = c.hot[i];
            if (iv != null) {
                hot.getAsJsonArray("rows").add(rowJson("快" + (i + 1), "h" + i, iv, i == c.selected));
            }
        }
        groups.add(hot);

        // 背包
        JsonObject main = group("背包", "main", c.mainFilled, 27);
        for (int i = 0; i < 27; i++) {
            ItemView iv = c.main[i];
            if (iv != null) {
                main.getAsJsonArray("rows").add(rowJson("包" + (i + 1), "m" + i, iv, false));
            }
        }
        groups.add(main);

        // 装备
        JsonObject armor = group("装备", "armor", c.armor.size(), 0);
        for (Labeled l : c.armor) {
            armor.getAsJsonArray("rows").add(rowJson(l.label(), l.code(), l.view(), false));
        }
        groups.add(armor);

        // 副手
        JsonObject off = group("副手", "offhand", c.offhand == null ? 0 : 1, 0);
        if (c.offhand != null) {
            off.getAsJsonArray("rows").add(rowJson("副手", "offhand", c.offhand, false));
        }
        groups.add(off);

        // 末影箱
        JsonObject ender = group("末影箱", "ender", c.enderFilled, 27);
        for (int i = 0; i < 27; i++) {
            ItemView iv = c.ender[i];
            if (iv != null) {
                ender.getAsJsonArray("rows").add(rowJson("末" + (i + 1), "e" + i, iv, false));
            }
        }
        groups.add(ender);

        root.add("groups", groups);
        return root.toString();
    }

    private static JsonObject group(String title, String kind, int filled, int cap) {
        JsonObject g = new JsonObject();
        g.addProperty("title", title);
        g.addProperty("kind", kind);
        g.addProperty("filled", filled);
        g.addProperty("cap", cap);
        g.add("rows", new JsonArray());
        return g;
    }

    private static JsonObject rowJson(String label, String code, ItemView iv, boolean selected) {
        JsonObject r = new JsonObject();
        r.addProperty("label", label);
        r.addProperty("code", code);
        r.addProperty("id", iv.itemId);
        r.addProperty("count", iv.count);
        if (iv.customText != null && !iv.customText.isEmpty()) {
            r.addProperty("custom", iv.customText);
        }
        if (selected) {
            r.addProperty("sel", true);
        }
        if (iv.damage >= 0) {
            r.addProperty("dmg", iv.damage);
        }
        if (!iv.enchants.isEmpty()) {
            JsonArray ench = new JsonArray();
            for (ItemView.EnchantInfo e : iv.enchants) {
                JsonObject eo = new JsonObject();
                eo.addProperty("k", e.key());
                eo.addProperty("l", e.level());
                ench.add(eo);
            }
            r.add("ench", ench);
        }
        if (iv.innerTotal > 0) {
            r.addProperty("box", true);
            JsonArray inn = new JsonArray();
            for (ItemView.InnerItem ii : iv.inner) {
                JsonObject io = new JsonObject();
                io.addProperty("id", ii.id());
                io.addProperty("count", ii.count());
                if (ii.customText() != null && !ii.customText().isEmpty()) {
                    io.addProperty("custom", ii.customText());
                }
                inn.add(io);
            }
            r.add("inner", inn);
        }
        return r;
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
