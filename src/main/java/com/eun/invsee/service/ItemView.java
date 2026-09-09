package com.eun.invsee.service;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天指令使用的物品摘要。在线与离线物品均由 SnapshotReader 解码。
 */
public final class ItemView {

    public record EnchantInfo(String key, int level) {
    }

    /** 容器内含物品：id/customText/count 供双端 JSON；chatName 供聊天悬停本地化显示。 */
    public record InnerItem(String id, String customText, int count, Component chatName) {
    }

    public final String itemId;          // 如 minecraft:netherite_axe
    public final int count;              // 堆叠数量
    public final Component display;      // 聊天用的显示名（translatable 交给客户端解析）
    public final String customText;      // 纯文本自定义名；无则为 null
    public final int damage;             // 耐久损伤, -1 = 无
    public final List<EnchantInfo> enchants;
    public final List<InnerItem> inner;  // 容器(潜影盒等)内含物品
    public final int innerTotal;         // 容器内非空格数

    private ItemView(String itemId, int count, Component display, String customText, int damage,
                     List<EnchantInfo> enchants, List<InnerItem> inner, int innerTotal) {
        this.itemId = itemId;
        this.count = count;
        this.display = display;
        this.customText = customText;
        this.damage = damage;
        this.enchants = enchants;
        this.inner = inner;
        this.innerTotal = innerTotal;
    }

    // ---------- 在线：由 ItemStack 构建 ----------

    public static ItemView fromStack(ItemStack stack, MinecraftServer server) {
        String id = idOf(stack, server);
        Component display = stack.getHoverName();
        Component customComp = stack.getComponents().get(DataComponents.CUSTOM_NAME);
        String customText = customComp == null ? null : stripCodes(customComp.getString());

        List<EnchantInfo> enchants = new ArrayList<>();
        ItemEnchantments enc = stack.getComponents().get(DataComponents.ENCHANTMENTS);
        if (enc != null && !enc.isEmpty()) {
            for (Object2IntMap.Entry<Holder<Enchantment>> e : enc.entrySet()) {
                String key = e.getKey().unwrapKey()
                        .map(k -> k.identifier().toString())
                        .orElse("unknown");
                enchants.add(new EnchantInfo(key, e.getIntValue()));
            }
        }
        Integer dmg = stack.getComponents().get(DataComponents.DAMAGE);
        int damage = dmg == null ? -1 : dmg;

        List<InnerItem> inner = new ArrayList<>();
        int innerTotal = 0;
        ItemContainerContents cc = stack.getComponents().get(DataComponents.CONTAINER);
        if (cc != null) {
            innerTotal = (int) cc.nonEmptyItemCopyStream().count();
            cc.nonEmptyItemCopyStream()
                    .limit(27)
                    .forEach(s -> inner.add(new InnerItem(idOf(s, server),
                            customTextOf(s), s.getCount(), s.getHoverName())));
        }
        return new ItemView(id, stack.getCount(), display, customText, damage, enchants, inner, innerTotal);
    }

    private static String customTextOf(ItemStack stack) {
        Component c = stack.getComponents().get(DataComponents.CUSTOM_NAME);
        return c == null ? null : stripCodes(c.getString());
    }

    // ---------- 渲染（聊天） ----------

    /** 显示名 + 悬停详情（附魔/损伤/内含/ID）。 */
    public MutableComponent withTooltip() {
        return display.copy().withStyle(st -> st.withHoverEvent(new HoverEvent.ShowText(tooltip())));
    }

    private Component tooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("§7物品 ID: §f" + itemId));
        if (damage >= 0) {
            lines.add(Component.literal("§7耐久损伤: §f" + damage));
        }
        if (!enchants.isEmpty()) {
            for (EnchantInfo e : enchants) {
                lines.add(Component.literal("§7附魔: §f")
                        .append(Component.translatable("enchantment." + e.key().replace(':', '.')))
                        .append(" §7" + roman(e.level())));
            }
        }
        if (innerTotal > 0) {
            lines.add(Component.literal("§7内含物品 §f" + innerTotal + "§7 格:"));
            int shown = 0;
            for (InnerItem ii : inner) {
                lines.add(Component.literal("§8  └ ")
                        .append(ii.chatName().copy())
                        .append(Component.literal(" §7×§f" + ii.count())));
                shown++;
                if (shown >= 8) {
                    break;
                }
            }
            if (inner.size() > 8) {
                lines.add(Component.literal("§8  … 及另外 " + (innerTotal - shown) + " 格"));
            }
        }
        MutableComponent out = Component.literal("");
        boolean first = true;
        for (Component c : lines) {
            if (!first) {
                out.append("\n");
            }
            out.append(c);
            first = false;
        }
        return out;
    }

    /** 行内灰色角标摘要（有则加，无则空串）。 */
    public String inlineSuffix() {
        StringBuilder sb = new StringBuilder();
        if (damage >= 0) {
            sb.append(" §8[损").append(damage).append(']');
        }
        if (!enchants.isEmpty()) {
            sb.append(" §8[附魔×").append(enchants.size()).append(']');
        }
        if (innerTotal > 0) {
            sb.append(" §8[箱").append(innerTotal).append(']');
        }
        return sb.toString();
    }

    // ---------- 工具 ----------

    private static String idOf(ItemStack stack, MinecraftServer server) {
        try {
            Registry<Item> reg = server.registryAccess().lookupOrThrow(Registries.ITEM);
            Identifier key = reg.getKey(stack.getItem());
            return key == null ? "?" : key.toString();
        } catch (Exception e) {
            return "?";
        }
    }

    /** 显示名：自定义名优先，否则 translatable(item.descriptionId)。server 可空(客户端纯文本场景只取自定义名)。 */
    public static Component nameOf(String itemId, String customName, MinecraftServer server) {
        if (customName != null && !customName.isEmpty()) {
            return Component.literal(customName);
        }
        if (server != null) {
            try {
                Identifier id = Identifier.tryParse(itemId);
                if (id != null) {
                    Registry<Item> reg = server.registryAccess().lookupOrThrow(Registries.ITEM);
                    Item item = reg.getValue(id);
                    if (item != null) {
                        return Component.translatable(item.getDescriptionId());
                    }
                }
            } catch (Exception ignored) {
                // fallthrough
            }
        }
        return Component.literal(itemId);
    }

    private static String roman(int level) {
        if (level <= 0) {
            return "";
        }
        if (level <= 10) {
            return new String[]{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"}[level];
        }
        return String.valueOf(level);
    }

    /** 去掉聊天格式化代码（§x），用于发给客户端的纯文本自定义名。 */
    public static String stripCodes(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        boolean skip = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§' || ch == '\u00a7') {
                skip = true;
                continue;
            }
            if (skip) {
                skip = false;
                continue;
            }
            sb.append(ch);
        }
        return sb.toString().trim();
    }
}
