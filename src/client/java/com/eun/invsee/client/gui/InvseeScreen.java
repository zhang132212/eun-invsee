package com.eun.invsee.client.gui;

import com.eun.invsee.client.net.ClientInvseeNet;
import com.eun.invsee.data.ContainerLayout;
import com.eun.invsee.data.InventorySnapshot;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.eun.invsee.client.gui.InvseeTheme.*;

/** Read-only inventory inspector. Item stacks are rendered directly; no container menu is attached. */
public class InvseeScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long TIMEOUT_NS = 10_000_000_000L;
    private static String lastTarget = "";

    private InvseeLayout layout;
    private EditBox nameField;
    private ActionButton queryButton;
    private ActionButton refreshButton;
    private ActionButton closeButton;
    private ActionButton backButton;
    private ActionButton dismissButton;
    private InventorySnapshot snapshot;
    private List<Slot> slots = List.of();
    private final Deque<Preview> previews = new ArrayDeque<>();
    private final Map<ItemStack, List<Component>> tooltips = new IdentityHashMap<>();
    private Component feedback;
    private boolean error;
    private boolean loading;
    private long pendingId = -1;
    private long requestedAt;
    private int scroll;
    private boolean draggingScroll;

    private record Slot(int x, int y, ItemStack stack, Component label, boolean selected) {}
    private static final class Preview {
        final ItemStack parent;
        final List<ItemStack> slots;
        final ContainerLayout layout;
        int scrollRow;
        Preview(ItemStack stack) {
            parent = stack;
            slots = InventorySnapshot.containerSlots(stack);
            layout = ContainerLayout.of(stack, slots.size());
        }
    }

    public InvseeScreen() {
        super(t("title"));
    }

    private static Component t(String key, Object... args) {
        return Component.translatable("screen.eun_invsee." + key, args);
    }

    @Override
    protected void init() {
        String typed = nameField == null ? lastTarget : nameField.getValue();
        layout = InvseeLayout.create(width, height);
        var p = layout.panel();
        nameField = new EditBox(font, p.x() + 20, p.y() + 42, p.width() - 152, 14, t("player_name"));
        nameField.setBordered(false);
        nameField.setTextColor(TEXT);
        nameField.setTextShadow(false);
        nameField.setMaxLength(32);
        nameField.setHint(t("search_hint"));
        nameField.setValue(typed);
        addRenderableWidget(nameField);
        queryButton = addRenderableWidget(new ActionButton(font, p.right() - 124, p.y() + 36, 58, 24,
                t("query"), true, b -> doQuery(nameField.getValue())));
        refreshButton = addRenderableWidget(new ActionButton(font, p.right() - 60, p.y() + 36, 48, 24,
                t("refresh"), false, b -> {
                    if (snapshot != null) doQuery(snapshot.target());
                }));
        closeButton = addRenderableWidget(new ActionButton(font, p.right() - 32, p.y() + 8, 20, 20,
                Component.literal("×"), false, b -> onClose()));
        backButton = addWidget(new ActionButton(font, 0, 0, 22, 20, Component.literal("‹"), false, b -> popPreview()));
        dismissButton = addWidget(new ActionButton(font, 0, 0, 22, 20, Component.literal("×"), false, b -> {
            previews.clear();
            tooltips.clear();
            syncButtons();
        }));
        rebuildSlots();
        scroll = layout.clampScroll(scroll);
        ClientInvseeNet.setScreen(this);
        syncButtons();
        if (snapshot == null && previews.isEmpty()) setInitialFocus(nameField);
    }

    private void doQuery(String input) {
        if (loading || !previews.isEmpty()) return;
        String target = input.trim();
        if (target.isEmpty()) {
            queryFailed(t("enter_name"));
            setFocused(nameField);
            return;
        }
        if (!ClientInvseeNet.available()) {
            queryFailed(t("unavailable"));
            return;
        }
        lastTarget = target;
        nameField.setValue(target);
        pendingId = ClientInvseeNet.nextRequestId();
        requestedAt = System.nanoTime();
        loading = true;
        error = false;
        feedback = t("loading", target);
        syncButtons();
        try {
            ClientInvseeNet.request(target, pendingId);
        } catch (RuntimeException e) {
            queryFailed(t("unavailable"));
        }
    }

    public void applyResponse(JsonObject root) {
        if (!loading) return;
        if (!root.has("protocol") || root.get("protocol").getAsInt() != InventorySnapshot.PROTOCOL) {
            queryFailed(t("version_error"));
            return;
        }
        if (!root.has("requestId") || root.get("requestId").getAsLong() != pendingId) return;
        if (!root.has("ok") || !root.get("ok").getAsBoolean()) {
            queryFailed(root.has("err") ? Component.literal(root.get("err").getAsString()) : t("decode_error"));
            return;
        }
        if (minecraft.level == null) {
            disconnected();
            return;
        }
        InventorySnapshot next = InventorySnapshot.fromJson(root, minecraft.level.registryAccess());
        boolean sameTarget = snapshot != null && snapshot.target().equalsIgnoreCase(next.target());
        snapshot = next;
        pendingId = -1;
        loading = false;
        feedback = null;
        error = false;
        previews.clear();
        tooltips.clear();
        if (!sameTarget) scroll = 0;
        rebuildSlots();
        syncButtons();
    }

    public void queryFailed(Component message) {
        loading = false;
        pendingId = -1;
        feedback = message;
        error = true;
        syncButtons();
    }

    public void disconnected() {
        snapshot = null;
        slots = List.of();
        previews.clear();
        tooltips.clear();
        queryFailed(t("disconnected"));
    }

    @Override
    public void tick() {
        super.tick();
        if (loading && System.nanoTime() - requestedAt >= TIMEOUT_NS) queryFailed(t("timeout"));
    }

    private void syncButtons() {
        if (queryButton == null) return;
        boolean modal = !previews.isEmpty();
        queryButton.active = !modal && !loading;
        queryButton.setMessage(t(loading ? "querying" : "query"));
        refreshButton.active = !modal && !loading && snapshot != null;
        closeButton.active = !modal;
        nameField.active = !modal;
        nameField.setEditable(!modal);
        backButton.visible = modal;
        backButton.active = modal;
        dismissButton.visible = modal;
        dismissButton.active = modal;
    }

    private void rebuildSlots() {
        if (snapshot == null || layout == null) {
            slots = List.of();
            return;
        }
        List<Slot> out = new ArrayList<>(69);
        var inv = layout.inventory();
        for (int i = 9; i < 36; i++) {
            out.add(new Slot(layout.gridX(inv) + (i - 9) % 9 * InvseeLayout.CELL,
                    inv.y() + 30 + (i - 9) / 9 * InvseeLayout.CELL,
                    snapshot.inventory().get(i), t("inventory_slot", i - 8), false));
        }
        for (int i = 0; i < 9; i++) {
            out.add(new Slot(layout.gridX(inv) + i * InvseeLayout.CELL, inv.y() + 120,
                    snapshot.inventory().get(i), t("hotbar_slot", i + 1), i == snapshot.selected()));
        }
        var eq = layout.equipment();
        int equipmentX = eq.x() + (eq.width() - 182) / 2;
        for (int i = 0; i < 6; i++) {
            out.add(new Slot(equipmentX + i * 32, eq.y() + 28, snapshot.equipment().get(i),
                    t(InventorySnapshot.EQUIPMENT_KEYS.get(i)), false));
        }
        var ender = layout.ender();
        for (int i = 0; i < 27; i++) {
            out.add(new Slot(layout.gridX(ender) + i % 9 * InvseeLayout.CELL,
                    ender.y() + 30 + i / 9 * InvseeLayout.CELL,
                    snapshot.ender().get(i), t("ender_slot", i + 1), false));
        }
        slots = List.copyOf(out);
    }

    private void openPreview(ItemStack stack) {
        if (!InventorySnapshot.isContainer(stack)) return;
        previews.push(new Preview(stack));
        tooltips.clear();
        draggingScroll = false;
        clearFocus();
        syncButtons();
        placePreviewButtons();
        setFocused(backButton);
    }

    private void popPreview() {
        if (!previews.isEmpty()) previews.pop();
        tooltips.clear();
        syncButtons();
        if (previews.isEmpty()) setFocused(queryButton);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!previews.isEmpty()) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                popPreview();
                return true;
            }
            return super.keyPressed(event);
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (nameField.isFocused()) {
                doQuery(nameField.getValue());
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_F5 && snapshot != null) {
            doQuery(snapshot.target());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        if (!previews.isEmpty()) {
            placePreviewButtons();
            if (backButton.mouseClicked(event, focused) || dismissButton.mouseClicked(event, focused)) return true;
            var rect = previewRect();
            if (!rect.contains(event.x(), event.y())) {
                popPreview();
                return true;
            }
            ItemStack stack = hoveredPreviewStack(event.x(), event.y());
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !stack.isEmpty()) openPreview(stack);
            return true;
        }
        if (super.mouseClicked(event, focused)) return true;
        if (snapshot == null || loading) return false;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && scrollbarTrack().contains(event.x(), event.y())
                && layout.maxScroll() > 0) {
            draggingScroll = true;
            moveScrollbar(event.y());
            return true;
        }
        Slot slot = hoveredSlot(event.x(), event.y());
        if (slot != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            openPreview(slot.stack);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScroll) {
            moveScrollbar(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingScroll = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!previews.isEmpty()) {
            Preview preview = previews.peek();
            int max = Math.max(0, preview.layout.rows() - previewRows());
            preview.scrollRow = Math.max(0, Math.min(max, preview.scrollRow - (int) Math.signum(vertical)));
            return true;
        }
        if (layout.viewport().contains(x, y)) {
            scroll = layout.clampScroll(scroll - (int) (vertical * 22));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    private Slot hoveredSlot(double x, double y) {
        var v = layout.viewport();
        if (!v.contains(x, y)) return null;
        double localX = x - v.x();
        double localY = y - v.y() + scroll;
        for (Slot slot : slots) {
            if (localX >= slot.x && localX < slot.x + InvseeLayout.CELL
                    && localY >= slot.y && localY < slot.y + InvseeLayout.CELL) return slot;
        }
        return null;
    }

    private InvseeLayout.Rect scrollbarTrack() {
        var v = layout.viewport();
        return new InvseeLayout.Rect(v.right() - 4, v.y(), 4, v.height());
    }

    private int thumbHeight() {
        var v = layout.viewport();
        return Math.max(18, v.height() * v.height() / layout.contentHeight());
    }

    private void moveScrollbar(double y) {
        var track = scrollbarTrack();
        int travel = track.height() - thumbHeight();
        if (travel > 0) scroll = layout.clampScroll((int) ((y - track.y() - thumbHeight() / 2.0)
                / travel * layout.maxScroll()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // The in-game world remains visible underneath the custom panel.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (layout == null) return;
        var p = layout.panel();
        g.fill(0, 0, width, height, 0xB8091016);
        g.fill(p.x() + 3, p.y() + 5, p.right() + 3, p.bottom() + 5, 0x66000000);
        surface(g, p.x(), p.y(), p.width(), p.height(), PANEL, BORDER);
        g.fill(p.x() + 12, p.y() + 12, p.x() + 15, p.y() + 23, ACCENT);
        g.text(font, title, p.x() + 23, p.y() + 14, TEXT, false);
        Component readOnly = t("read_only");
        g.text(font, readOnly, p.right() - 45 - font.width(readOnly), p.y() + 14, ACCENT, false);
        surface(g, p.x() + 12, p.y() + 36, p.width() - 142, 24, SLOT, nameField.isFocused() ? ACCENT : BORDER);
        renderIdentity(g);
        var v = layout.viewport();
        g.enableScissor(v.x(), v.y(), v.right(), v.bottom());
        if (snapshot == null) renderEmpty(g);
        else renderInventory(g, mouseX, mouseY);
        g.disableScissor();
        if (snapshot != null && layout.maxScroll() > 0) renderScrollbar(g);
        Component footer = feedback == null ? t(snapshot != null && layout.maxScroll() > 0 ? "hint_scroll" : "hint") : feedback;
        textFit(g, footer, p.x() + 12, p.bottom() - 16, p.width() - 24, error ? ERROR : MUTED);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (previews.isEmpty()) {
            if (mouseY >= p.bottom() - 21 && mouseY < p.bottom() && feedback != null) {
                g.setTooltipForNextFrame(font, feedback, mouseX, mouseY);
            } else if (!loading) {
                Slot hovered = hoveredSlot(mouseX, mouseY);
                if (hovered != null) showTooltip(g, hovered.stack, hovered.label, mouseX, mouseY);
            }
        } else {
            renderPreview(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderIdentity(GuiGraphicsExtractor g) {
        var p = layout.panel();
        if (snapshot == null) {
            g.text(font, t("welcome"), p.x() + 14, p.y() + 70, TEXT, false);
            textFit(g, t("welcome_detail"), p.x() + 14, p.y() + 83, p.width() - 28, MUTED);
            return;
        }
        surface(g, p.x() + 12, p.y() + 66, 24, 24, 0xFF233B3B, BORDER);
        String initial = snapshot.target().isEmpty() ? "?" : snapshot.target().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        g.text(font, initial, p.x() + 24 - font.width(initial) / 2, p.y() + 74, ACCENT, false);
        textFit(g, Component.literal(snapshot.target()), p.x() + 44, p.y() + 68, p.width() - 130, TEXT);
        Component state = t(snapshot.online() ? "online" : "offline");
        int stateWidth = font.width(state) + 14;
        int stateX = p.right() - 12 - stateWidth;
        surface(g, stateX, p.y() + 65, stateWidth, 16, CARD, BORDER);
        g.fill(stateX + 5, p.y() + 71, stateX + 8, p.y() + 74, snapshot.online() ? ACCENT : PURPLE);
        g.text(font, state, stateX + 11, p.y() + 69, snapshot.online() ? ACCENT : PURPLE, false);
        long time = snapshot.online() ? snapshot.capturedAt() : snapshot.savedAt();
        String date = time > 0 ? TIME.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())) : t("unknown_time").getString();
        textFit(g, t(snapshot.online() ? "captured_at" : "saved_at", date),
                p.x() + 44, p.y() + 82, p.width() - 56, MUTED);
    }

    private void renderEmpty(GuiGraphicsExtractor g) {
        var v = layout.viewport();
        int cx = v.x() + v.width() / 2;
        int cy = v.y() + Math.max(22, v.height() / 2 - 22);
        surface(g, cx - 18, cy - 10, 36, 27, CARD, BORDER);
        g.fill(cx - 16, cy - 1, cx + 16, cy + 1, BORDER);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 4, ACCENT);
        centeredFit(g, t(loading ? "querying" : "empty_title"), cx, cy + 28, v.width() - 20, TEXT);
        centeredFit(g, t("empty_detail"), cx, cy + 43, v.width() - 20, MUTED);
    }

    private void renderInventory(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        renderCard(g, layout.inventory(), t("inventory"), InventorySnapshot.occupied(snapshot.inventory()), 36, ACCENT);
        renderCard(g, layout.equipment(), t("equipment"), InventorySnapshot.occupied(snapshot.equipment()), 6, ACCENT);
        renderCard(g, layout.ender(), t("ender"), InventorySnapshot.occupied(snapshot.ender()), 27, PURPLE);
        var v = layout.viewport();
        var inv = layout.inventory();
        g.text(font, t("hotbar"), v.x() + layout.gridX(inv), v.y() + inv.y() + 106 - scroll, MUTED, false);
        Slot hovered = previews.isEmpty() && !loading ? hoveredSlot(mouseX, mouseY) : null;
        for (Slot slot : slots) {
            int x = v.x() + slot.x;
            int y = v.y() + slot.y - scroll;
            if (y + InvseeLayout.CELL <= v.y() || y >= v.bottom()) continue;
            drawSlot(g, x, y, slot.stack, slot == hovered, slot.selected);
        }
        var eq = layout.equipment();
        int ex = v.x() + eq.x() + (eq.width() - 182) / 2;
        for (int i = 0; i < 6; i++) {
            Component label = t(InventorySnapshot.EQUIPMENT_KEYS.get(i));
            centeredFit(g, label, ex + i * 32 + 11, v.y() + eq.y() + 55 - scroll, 31, MUTED);
        }
    }

    private void renderCard(GuiGraphicsExtractor g, InvseeLayout.Rect card, Component label, int filled, int cap, int accent) {
        var v = layout.viewport();
        int x = v.x() + card.x(), y = v.y() + card.y() - scroll;
        surface(g, x, y, card.width(), card.height(), CARD, BORDER);
        g.fill(x + 10, y + 11, x + 12, y + 19, accent);
        g.text(font, label, x + 18, y + 11, TEXT, false);
        String count = filled + " / " + cap;
        g.text(font, count, x + card.width() - 11 - font.width(count), y + 11, MUTED, false);
    }

    private void drawSlot(GuiGraphicsExtractor g, int x, int y, ItemStack stack, boolean hover, boolean selected) {
        int size = InvseeLayout.CELL - 1;
        surface(g, x, y, size, size, hover ? 0xFF2F454B : SLOT,
                hover ? ACCENT : selected ? 0xFFE4C783 : BORDER);
        if (!stack.isEmpty()) {
            g.item(stack, x + 2, y + 2);
            g.itemDecorations(font, stack, x + 2, y + 2);
            if (InventorySnapshot.isContainer(stack)) {
                g.fill(x + size - 4, y + 1, x + size - 1, y + 4, PURPLE);
            }
        } else {
            g.fill(x + 9, y + 9, x + 11, y + 11, 0xFF293B43);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor g) {
        var track = scrollbarTrack();
        int thumb = thumbHeight();
        int y = track.y() + scroll * (track.height() - thumb) / layout.maxScroll();
        g.fill(track.x(), track.y(), track.right(), track.bottom(), SLOT);
        g.fill(track.x(), y, track.right(), y + thumb, draggingScroll ? ACCENT : BORDER);
    }

    private int previewRows() {
        if (previews.isEmpty()) return 3;
        return Math.max(1, Math.min(previews.peek().layout.rows(), Math.min(6, (height - 100) / InvseeLayout.CELL)));
    }

    private InvseeLayout.Rect previewRect() {
        int w = Math.min(246, width - 20);
        int h = 76 + previewRows() * InvseeLayout.CELL;
        return new InvseeLayout.Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    private void placePreviewButtons() {
        if (!previews.isEmpty()) {
            Preview current = previews.peek();
            current.scrollRow = Math.min(current.scrollRow, Math.max(0, current.layout.rows() - previewRows()));
        }
        var r = previewRect();
        backButton.setX(r.x() + 10);
        backButton.setY(r.y() + 8);
        dismissButton.setX(r.right() - 32);
        dismissButton.setY(r.y() + 8);
    }

    private int hoveredPreviewIndex(double x, double y) {
        if (previews.isEmpty()) return -1;
        Preview preview = previews.peek();
        var r = previewRect();
        int gridWidth = preview.layout.columns() * InvseeLayout.CELL;
        int left = r.x() + (r.width() - gridWidth) / 2;
        int top = r.y() + 50;
        var grid = new InvseeLayout.Rect(left, top, gridWidth, previewRows() * InvseeLayout.CELL);
        if (!grid.contains(x, y)) return -1;
        int col = (int) (x - left) / InvseeLayout.CELL;
        int row = (int) (y - top) / InvseeLayout.CELL + preview.scrollRow;
        return preview.layout.indexAt(col, row);
    }

    private ItemStack hoveredPreviewStack(double x, double y) {
        int index = hoveredPreviewIndex(x, y);
        return index < 0 ? ItemStack.EMPTY : previews.peek().slots.get(index);
    }

    private void renderPreview(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        placePreviewButtons();
        Preview preview = previews.peek();
        var r = previewRect();
        g.nextStratum();
        g.fill(0, 0, width, height, 0xC0081015);
        surface(g, r.x(), r.y(), r.width(), r.height(), PANEL, 0xFF78658E);
        centeredFit(g, t("container_title"), r.x() + r.width() / 2, r.y() + 14, r.width() - 76, PURPLE);
        centeredFit(g, preview.parent.getHoverName(), r.x() + r.width() / 2, r.y() + 35, r.width() - 24, TEXT);
        int left = r.x() + (r.width() - preview.layout.columns() * InvseeLayout.CELL) / 2;
        int hovered = hoveredPreviewIndex(mouseX, mouseY);
        for (int i = 0; i < preview.slots.size(); i++) {
            var position = preview.layout.positions().get(i);
            int row = position.row() - preview.scrollRow;
            if (row < 0 || row >= previewRows()) continue;
            drawSlot(g, left + position.column() * InvseeLayout.CELL, r.y() + 50 + row * InvseeLayout.CELL,
                    preview.slots.get(i), i == hovered, false);
        }
        if (preview.slots.isEmpty()) {
            centeredFit(g, t("container_no_data"), r.x() + r.width() / 2, r.y() + 57, r.width() - 24, MUTED);
        }
        Component count = preview.layout.exactCapacity()
                ? t("container_count", InventorySnapshot.occupied(preview.slots), preview.slots.size())
                : t("container_recorded", preview.slots.size());
        centeredFit(g, count, r.x() + r.width() / 2, r.bottom() - 19, r.width() - 16, MUTED);
        if (preview.layout.rows() > previewRows()) {
            centeredFit(g, t("container_scroll", preview.scrollRow + 1, preview.layout.rows()),
                    r.x() + r.width() / 2, r.bottom() + 6, r.width(), MUTED);
        }
        backButton.extractRenderState(g, mouseX, mouseY, partialTick);
        dismissButton.extractRenderState(g, mouseX, mouseY, partialTick);
        if (hovered >= 0) {
            String role = preview.layout.positions().get(hovered).role();
            Component label = role.isEmpty() ? t("container_slot", hovered + 1) : t("slot_" + role);
            showTooltip(g, preview.slots.get(hovered), label, mouseX, mouseY);
        }
    }

    private void showTooltip(GuiGraphicsExtractor g, ItemStack stack, Component location, int mouseX, int mouseY) {
        if (stack.isEmpty()) {
            g.setTooltipForNextFrame(font, location.copy().append(t("empty_slot")), mouseX, mouseY);
            return;
        }
        List<Component> lines = new ArrayList<>(tooltips.computeIfAbsent(stack, item -> List.copyOf(getTooltipFromItem(minecraft, item))));
        lines.add(location.copy().withColor(0x97ADB5));
        if (InventorySnapshot.isContainer(stack)) lines.add(t("open_container").copy().withColor(0xC4ACEC));
        g.setTooltipForNextFrame(font, lines, stack.getTooltipImage(), mouseX, mouseY);
    }

    private void textFit(GuiGraphicsExtractor g, Component value, int x, int y, int maxWidth, int color) {
        String text = value.getString();
        if (font.width(text) > maxWidth) text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
        g.text(font, text, x, y, color, false);
    }

    private void centeredFit(GuiGraphicsExtractor g, Component value, int center, int y, int maxWidth, int color) {
        String text = value.getString();
        if (font.width(text) > maxWidth) text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
        g.text(font, text, center - font.width(text) / 2, y, color, false);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        ClientInvseeNet.clearScreen(this);
        loading = false;
        pendingId = -1;
        super.removed();
    }

    @Override
    public void onClose() {
        ClientInvseeNet.clearScreen(this);
        snapshot = null;
        slots = List.of();
        previews.clear();
        tooltips.clear();
        pendingId = -1;
        loading = false;
        super.onClose();
    }
}
