package com.eun.invsee.client.gui;

import com.eun.invsee.client.net.ClientInvseeNet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 背包式查看界面：按“玩家背包/容器”的样式，以 9 列物品格子展示图标+数量；
 * 悬停显示物品名（原版提示风格），点开带箱物品进入盒内容器预览（仿 MiniHUD/开箱）。
 * 只读：仅展示服务端发来的数据。
 */
public class InvseeScreen extends Screen {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final int CELL = 18;
    private static final int COLS = 9;
    private static final int COLOR_TITLE = 0xFFFFAA00;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_HEADER = 0xFFFFFF55;
    private static final int COLOR_BACK = 0xFF55FFFF;
    private static final int COLOR_SLOT_BORDER = 0xFF373737;
    private static final int COLOR_SLOT_BG = 0xFF8B8B8B;
    private static final int COLOR_SLOT_BG_INNER = 0xFFC6C6C6;
    private static final int COLOR_HOVER = 0xFFFFFFFF;

    private EditBox nameField;
    private int scrollRow = 0;
    private String status = "输入玩家名后点「查询」。需要 OP(等级≥2) 且服务端装有本 mod。";

    private long frames = 0;
    private int lastMetricSecond = -1;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("eun_invsee.client");

    private List<Group> groups = new ArrayList<>();
    private boolean boxMode = false;
    private String boxTitle = "";
    private List<RowItem> boxItems = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();

    public InvseeScreen() {
        super(Component.literal("EUN 背包查看"));
    }

    @Override
    protected void init() {
        super.init();
        int fieldW = Math.min(280, this.width / 2 - 70);
        nameField = new EditBox(this.font, 8, 6, fieldW, 16, Component.literal("玩家名"));
        nameField.setHint(Component.literal("玩家名（在线或最近上线过）"));
        nameField.setMaxLength(32);
        this.addRenderableWidget(nameField);

        Button btnQuery = Button.builder(Component.literal("查询"), b -> doQuery())
                .bounds(8 + fieldW + 4, 4, 56, 20)
                .build();
        this.addRenderableWidget(btnQuery);

        ClientInvseeNet.setScreen(this);
        rebuildRows();
    }

    private void doQuery() {
        String target = nameField.getValue().trim();
        if (target.isEmpty()) {
            status = "请输入玩家名。";
            return;
        }
        status = "正在查询 " + target + " …";
        ClientInvseeNet.request(target);
    }

    /** 服务端响应到达（客户端主线程）。 */
    public void applyResponse(JsonObject root) {
        boxMode = false;
        scrollRow = 0;
        releaseState();
        int groupCount = 0;
        int rowCount = 0;
        if (root.has("groups")) {
            groupCount = root.getAsJsonArray("groups").size();
        }
        LOG.info("[eun_invsee] applyResponse ok={} target={} groups={} jsonLen={}",
                root.has("ok") ? root.get("ok").getAsBoolean() : false,
                root.has("target") ? root.get("target").getAsString() : "?",
                groupCount,
                root.toString().length());
        if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
            String err = root.has("err") ? root.get("err").getAsString() : "未知错误";
            status = "查询失败：" + err;
            groups = new ArrayList<>();
            rows = new ArrayList<>();
            return;
        }
        String target = root.has("target") ? root.get("target").getAsString() : "";
        boolean online = root.has("online") && root.get("online").getAsBoolean();
        long savedAt = root.has("savedAt") ? root.get("savedAt").getAsLong() : -1;

        StringBuilder sb = new StringBuilder(target).append("   ·   ");
        if (online) {
            sb.append("在线（实时）");
        } else {
            sb.append("离线 · 存档 ");
            sb.append(savedAt > 0
                    ? TIME_FMT.format(Instant.ofEpochMilli(savedAt).atZone(ZoneId.systemDefault()))
                    : "未知");
        }
        status = sb.toString();

        groups = new ArrayList<>();
        if (root.has("groups")) {
            for (JsonElement ge : root.getAsJsonArray("groups")) {
                JsonObject go = ge.getAsJsonObject();
                Group g = new Group();
                g.title = go.has("title") ? go.get("title").getAsString() : "";
                g.kind = go.has("kind") ? go.get("kind").getAsString() : "";
                g.cap = go.has("cap") ? go.get("cap").getAsInt() : 0;
                g.filled = go.has("filled") ? go.get("filled").getAsInt() : 0;
                g.rows = new ArrayList<>();
                if (go.has("rows")) {
                    for (JsonElement re : go.getAsJsonArray("rows")) {
                        g.rows.add(parseRow(re.getAsJsonObject()));
                    }
                }
                groups.add(g);
            }
        }
        rebuildRows();
    }

    private RowItem parseRow(JsonObject o) {
        RowItem it = new RowItem();
        it.label = o.has("label") ? o.get("label").getAsString() : "";
        it.code = o.has("code") ? o.get("code").getAsString() : "";
        it.id = o.has("id") ? o.get("id").getAsString() : "";
        it.count = o.has("count") ? o.get("count").getAsInt() : 1;
        it.custom = o.has("custom") ? o.get("custom").getAsString() : null;
        it.sel = o.has("sel") && o.get("sel").getAsBoolean();
        it.box = o.has("box") && o.get("box").getAsBoolean();
        it.enchKeys = new ArrayList<>();
        if (o.has("ench")) {
            for (JsonElement e : o.getAsJsonArray("ench")) {
                JsonObject eo = e.getAsJsonObject();
                it.enchKeys.add(eo.has("k") ? eo.get("k").getAsString() : "");
            }
        }
        it.inner = new ArrayList<>();
        if (o.has("inner")) {
            for (JsonElement e : o.getAsJsonArray("inner")) {
                JsonObject io = e.getAsJsonObject();
                RowItem ii = new RowItem();
                ii.id = io.has("id") ? io.get("id").getAsString() : "";
                ii.count = io.has("count") ? io.get("count").getAsInt() : 1;
                ii.custom = io.has("custom") ? io.get("custom").getAsString() : null;
                it.inner.add(ii);
            }
        }
        return it;
    }

    // ---------- 行模型（9 列格子） ----------

    private static final class Row {
        boolean header;
        String text;
        RowItem[] slots; // 长度 9，可为 null 表示空格
        boolean backRow;
    }

    private void rebuildRows() {
        rows = new ArrayList<>();
        if (boxMode) {
            addHeader(boxTitle, true);
            addSlotsGrid(boxItems, false);
            return;
        }
        for (Group g : groups) {
            boolean ender = "ender".equals(g.kind);
            boolean important = "armor".equals(g.kind) || "offhand".equals(g.kind);
            if (g.rows.isEmpty() && !ender) {
                continue;
            }
            StringBuilder t = new StringBuilder(g.title);
            if (g.cap > 0) {
                t.append(" (").append(g.filled).append('/').append(g.cap).append(')');
            }
            addHeader(t.toString(), false);
            if (g.rows.isEmpty()) {
                addSlotsGrid(new ArrayList<>(), true);
            } else {
                addSlotsGrid(g.rows, !important && !ender);
            }
        }
        clampScroll();
    }

    private void addHeader(String text, boolean backRow) {
        Row r = new Row();
        r.header = true;
        r.text = text;
        r.backRow = backRow;
        rows.add(r);
    }

    /** pad=true 时把空格补满整行（背包/末影箱的样式），false 只画已占格子。 */
    private void addSlotsGrid(List<RowItem> items, boolean padToRow) {
        int pos = 0;
        while (pos < items.size() || (padToRow && pos % COLS != 0)) {
            Row r = new Row();
            r.slots = new RowItem[COLS];
            for (int c = 0; c < COLS; c++) {
                if (pos < items.size()) {
                    r.slots[c] = items.get(pos);
                    pos++;
                } else if (padToRow) {
                    r.slots[c] = null; // 空格
                } else {
                    break;
                }
            }
            rows.add(r);
            if (!padToRow && pos >= items.size()) {
                break;
            }
        }
        if (items.isEmpty() && !padToRow) {
            // 不会发生：调用方在空时会用 padToRow=true
        }
    }

    // ---------- 区域 ----------

    private int gridLeft() {
        return (this.width - COLS * CELL) / 2;
    }

    private int gridTop() {
        return 34;
    }

    private int gridBottom() {
        return this.height - 46;
    }

    private int visibleRows() {
        return Math.max(1, (gridBottom() - gridTop()) / CELL);
    }

    private void clampScroll() {
        int max = Math.max(0, rows.size() - visibleRows());
        scrollRow = Math.max(0, Math.min(scrollRow, max));
    }

    // ---------- 输入 ----------

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == GLFW.GLFW_KEY_ENTER || keyEvent.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (nameField != null && nameField.isFocused()) {
                doQuery();
                return true;
            }
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= gridTop() && mouseY <= gridBottom()) {
            int max = Math.max(0, rows.size() - visibleRows());
            if (scrollY > 0) {
                scrollRow = Math.max(0, scrollRow - 2);
            } else if (scrollY < 0) {
                scrollRow = Math.min(max, scrollRow + 2);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseEvent, boolean focused) {
        if (super.mouseClicked(mouseEvent, focused)) {
            return true;
        }
        if (mouseEvent.button() == 0) {
            int x = (int) mouseEvent.x();
            int y = (int) mouseEvent.y();
            if (y >= gridTop() && y <= gridBottom()) {
                int rowIdx = scrollRow + (y - gridTop()) / CELL;
                if (rowIdx >= 0 && rowIdx < rows.size()) {
                    Row row = rows.get(rowIdx);
                    if (row.header) {
                        if (row.backRow && boxMode) {
                            backToOverview();
                            return true;
                        }
                        return false;
                    }
                    int col = (x - gridLeft()) / CELL;
                    if (col >= 0 && col < COLS && row.slots != null && col < row.slots.length) {
                        RowItem it = row.slots[col];
                        if (it != null && it.box) {
                            openBox(it);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void openBox(RowItem parent) {
        if (!parent.box || parent.inner.isEmpty()) {
            return;
        }
        boxMode = true;
        String name = parent.custom != null && !parent.custom.isEmpty()
                ? parent.custom : (parent.id + " ×" + parent.count);
        boxTitle = "◀ 返回总览   容器 · " + name + "（" + parent.inner.size() + " 格）";
        boxItems = parent.inner;
        scrollRow = 0;
        rebuildRows();
    }

    private void backToOverview() {
        boxMode = false;
        scrollRow = 0;
        rebuildRows();
    }

    // ---------- 渲染 ----------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (nameField == null) {
            return;
        }

        g.text(this.font, Component.literal("EUN 背包查看（只读）"), 8, 28, COLOR_TITLE);
        g.text(this.font, Component.literal(status), 8, this.height - 38, COLOR_HEADER);

        clampScroll();
        int top = gridTop();
        int left = gridLeft();
        int vis = visibleRows();
        RowItem hoverItem = null;

        frames++;
        int sec = (int) (System.currentTimeMillis() / 1000);
        if (sec != lastMetricSecond) {
            lastMetricSecond = sec;
            if (frames % 100 == 0 || frames <= 5) {
                Runtime rt = Runtime.getRuntime();
                long used = rt.totalMemory() - rt.freeMemory();
                LOG.info("[eun_invsee] memMB={} rows={} frame={}", used / 1048576L, rows.size(), frames);
            }
        }

        for (int i = scrollRow; i < rows.size() && i < scrollRow + vis; i++) {
            int y = top + (i - scrollRow) * CELL;
            Row row = rows.get(i);
            if (row.header) {
                g.text(this.font, Component.literal(row.text), left, y + 4,
                        row.backRow ? COLOR_BACK : COLOR_HEADER);
                continue;
            }
            for (int c = 0; c < COLS && row.slots != null && c < row.slots.length; c++) {
                int x = left + c * CELL;
                RowItem it = row.slots[c];
                boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
                drawSlot(g, x, y, it, hovered);
                if (hovered && it != null) {
                    hoverItem = it;
                }
            }
        }

        // 自绘悬停信息（不依赖每帧注册的原版提示，避免累积）
        if (hoverItem != null) {
            String info = hoverItem.label.isEmpty() ? "" : "[" + hoverItem.label + "] ";
            String name = hoverItem.custom != null && !hoverItem.custom.isEmpty()
                    ? hoverItem.custom : hoverItem.id;
            g.text(this.font, Component.literal(info + name + "  ×" + hoverItem.count
                    + (hoverItem.box ? "  （含" + hoverItem.inner.size() + " 格，点击查看）" : "")
                    + "  ID:" + hoverItem.id), 8, this.height - 16, COLOR_HEADER);
            // 在格子旁显示名字缩略（不跨过屏幕右缘）
            String snip = info + name + " ×" + hoverItem.count;
            if (this.font.width(snip) > this.width - mouseX - 12) {
                snip = this.font.plainSubstrByWidth(snip, this.width - mouseX - 12);
            }
            int ty = mouseY >= this.height - 40 ? mouseY - 10 : mouseY + 12;
            g.text(this.font, Component.literal(snip), mouseX + 6, ty, COLOR_TEXT);
        }
    }

    private void drawSlot(GuiGraphicsExtractor g, int x, int y, RowItem it, boolean hovered) {
        int border = hovered ? COLOR_HOVER : COLOR_SLOT_BORDER;
        g.fill(x, y, x + CELL, y + CELL, border);
        g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, COLOR_SLOT_BG);
        if (it == null) {
            return;
        }
        ItemStack st = stackOf(it);
        if (st.isEmpty()) {
            g.text(this.font, Component.literal("?"), x + 4, y + 3, 0xFF333333);
            return;
        }
        g.item(st, x + 1, y + 1);
        g.itemDecorations(this.font, st, x + 1, y + 1);
        if (it.box) {
            g.text(this.font, Component.literal("箱"), x + 1, y + 1, 0xFFFFFF00);
        }
    }

    // ---------- 物品构造 ----------

    private ItemStack stackOf(RowItem it) {
        if (it.stackBuilt) {
            return it.stack;
        }
        it.stackBuilt = true;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Registry<Item> reg = mc.level.registryAccess().lookupOrThrow(Registries.ITEM);
                Identifier id = Identifier.tryParse(it.id);
                Item item = id == null ? null : reg.getValue(id);
                if (item != null) {
                    ItemStack st = new ItemStack(item);
                    st.setCount(Math.max(1, it.count));
                    it.stack = st;
                    return st;
                }
            }
        } catch (Exception ignored) {
            // 无法解析的物品 → 显示问号
        }
        it.stack = ItemStack.EMPTY;
        return it.stack;
    }

    @Override
    public void onClose() {
        ClientInvseeNet.setScreen(null);
        releaseState();
        super.onClose();
    }

    /** 释放所有持有数据/物品引用，便于 GC（防反复开关/查询造成残留占用）。 */
    private void releaseState() {
        groups = new ArrayList<>();
        boxItems = new ArrayList<>();
        rows = new ArrayList<>();
        scrollRow = 0;
    }

    // ---------- 数据模型 ----------

    private static final class Group {
        String title;
        String kind;
        int cap;
        int filled;
        List<RowItem> rows;
    }

    private static final class RowItem {
        String label = "";
        String code = "";
        String id = "";
        String custom;
        int count = 1;
        boolean sel;
        boolean box;
        List<String> enchKeys = new ArrayList<>();
        List<RowItem> inner = new ArrayList<>();
        boolean stackBuilt;
        ItemStack stack;
    }
}
