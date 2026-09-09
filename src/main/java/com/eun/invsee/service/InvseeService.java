package com.eun.invsee.service;

import com.eun.invsee.data.UserNameCache;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;

/**
 * /invsee 命令入口：解析目标(在线/离线) → 读取(只读) → 分页输出。
 */
public final class InvseeService {

    private static final int PAGE_SIZE = 14;

    private InvseeService() {
    }

    public static int execute(CommandSourceStack src, String name, int page) {
        try {
            return doExecute(src, name, page);
        } catch (Exception e) {
            src.sendFailure(Component.literal("§c背包查看出错: " + e.getMessage()));
            return 0;
        }
    }

    private static int doExecute(CommandSourceStack src, String name, int page) {
        if (name == null || name.isEmpty()) {
            help(src);
            return 1;
        }
        MinecraftServer server = src.getServer();

        Optional<TargetResolver.Resolved> maybe = TargetResolver.resolve(server, name);
        if (maybe.isEmpty()) {
            List<String> fuzzy = UserNameCache.fuzzyNames(server, name);
            MutableComponent msg = Component.literal("§c未找到玩家 §f" + name
                    + "§c（不在线且不在 usercache 记录中）。输入的名字需与最近上线记录一致。");
            if (!fuzzy.isEmpty()) {
                msg.append("\n§7相近玩家: §e" + String.join("§7, §e", fuzzy));
            }
            src.sendFailure(msg);
            return 0;
        }
        TargetResolver.Resolved r = maybe.get();
        String canonName = r.canonName();

        List<Component> lines;
        if (r.online() != null) {
            lines = PlayerViews.viewOnline(r.online(), server);
        } else if (r.data() == null) {
            src.sendFailure(Component.literal("§c玩家 §f" + canonName
                    + "§c 没有可读的存档数据（从未上线或数据损坏）。"));
            return 0;
        } else {
            lines = PlayerViews.viewOffline(r.data(), server, r.savedAtMs(), canonName);
        }

        sendPaged(src, lines, canonName, page);
        return 1;
    }

    private static void sendPaged(CommandSourceStack src, List<Component> lines, String name, int page) {
        if (lines.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§8（无内容）"), false);
            return;
        }
        int pages = Math.max(1, (int) Math.ceil((double) lines.size() / PAGE_SIZE));
        int cur = Math.max(1, Math.min(page, pages));
        int from = (cur - 1) * PAGE_SIZE;
        int to = Math.min(lines.size(), from + PAGE_SIZE);
        for (int i = from; i < to; i++) {
            final Component c = lines.get(i);
            src.sendSuccess(() -> c, false);
        }
        if (pages > 1) {
            MutableComponent nav = Component.literal("");
            if (cur > 1) {
                final int prev = cur - 1;
                nav.append(Component.literal("§7[§a上一页§7]").withStyle(st ->
                        st.withClickEvent(new ClickEvent.RunCommand("/invsee " + name + " " + prev))));
            } else {
                nav.append(Component.literal("§8[上一页]"));
            }
            nav.append(" §7第 §e" + cur + "§7/" + pages + " 页 ");
            if (cur < pages) {
                final int next = cur + 1;
                nav.append(Component.literal("§7[§a下一页§7]").withStyle(st ->
                        st.withClickEvent(new ClickEvent.RunCommand("/invsee " + name + " " + next))));
            } else {
                nav.append(Component.literal("§8[下一页]"));
            }
            final Component navFinal = nav;
            src.sendSuccess(() -> navFinal, false);
        }
    }

    private static void help(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("§6===== EUN 背包查看 ====="), false);
        src.sendSuccess(() -> Component.literal("§f/invsee <玩家名> [页码]"), false);
        src.sendSuccess(() -> Component.literal("§7查看目标玩家的 快捷栏/背包/装备/副手/末影箱。"), false);
        src.sendSuccess(() -> Component.literal("§7在线玩家读取实时数据；离线玩家读取存档(最后保存)。只读不可修改。"), false);
        src.sendSuccess(() -> Component.literal("§7物品行末尾的 §b[开箱]§7 可点击：在客户端弹出只读容器界面查看盒内物品。"), false);
        src.sendSuccess(() -> Component.literal("§7需要 OP 权限(等级≥2)。悬停物品可看详情，翻页可点击。"), false);
    }
}
