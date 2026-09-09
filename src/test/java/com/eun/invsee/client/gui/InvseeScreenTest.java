package com.eun.invsee.client.gui;

import com.eun.invsee.TestItems;
import com.eun.invsee.client.net.ClientInvseeNet;
import com.eun.invsee.data.InventorySnapshot;
import com.google.gson.JsonObject;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Deque;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InvseeScreenTest {
    Minecraft client;
    Font font;
    MockedStatic<Minecraft> minecraft;
    MockedStatic<ClientInvseeNet> network;
    InvseeScreen screen;

    @BeforeAll static void bootstrap() { assertNotNull(TestItems.REGISTRIES); }

    @BeforeEach void setup() throws Exception {
        client = mock(Minecraft.class);
        font = mock(Font.class);
        StringSplitter splitter = new StringSplitter((cp, style) -> cp < 128 ? 5 : 9);
        when(font.getSplitter()).thenReturn(splitter);
        when(font.width(anyString())).thenAnswer(i -> (int) splitter.stringWidth((String) i.getArgument(0)));
        when(font.width(any(FormattedText.class))).thenAnswer(i -> (int) splitter.stringWidth((FormattedText) i.getArgument(0)));
        when(font.width(any(FormattedCharSequence.class))).thenAnswer(i -> (int) splitter.stringWidth((FormattedCharSequence) i.getArgument(0)));
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(i -> splitter.plainHeadByWidth(i.getArgument(0), i.getArgument(1), Style.EMPTY));
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(i ->
                i.getArgument(2, Boolean.class) ? splitter.plainTailByWidth(i.getArgument(0), i.getArgument(1), Style.EMPTY)
                        : splitter.plainHeadByWidth(i.getArgument(0), i.getArgument(1), Style.EMPTY));
        var fontField = Minecraft.class.getField("font");
        fontField.setAccessible(true);
        fontField.set(client, font);
        when(client.getLastInputType()).thenReturn(InputType.KEYBOARD_TAB);
        client.level = mock(ClientLevel.class);
        when(client.level.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        minecraft = mockStatic(Minecraft.class);
        minecraft.when(Minecraft::getInstance).thenReturn(client);
        network = mockStatic(ClientInvseeNet.class);
        screen = new InvseeScreen();
        screen.width = 640;
        screen.height = 360;
        screen.init();
        network.clearInvocations();
    }

    @AfterEach void cleanup() {
        if (network != null) network.close();
        if (minecraft != null) minecraft.close();
    }

    static Object get(Object instance, String name) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    static void set(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    void rebuild() throws Exception {
        var method = net.minecraft.client.gui.screens.Screen.class.getDeclaredMethod("rebuildWidgets");
        method.setAccessible(true);
        method.invoke(screen);
    }

    static InventorySnapshot sample() {
        var inventory = InventorySnapshot.emptySlots(36);
        inventory.set(0, new ItemStack(Items.NETHERITE_SWORD));
        inventory.set(1, new ItemStack(Items.NETHERITE_PICKAXE));
        inventory.get(1).setDamageValue(510);
        inventory.set(4, new ItemStack(Items.GOLDEN_CARROT, 64));
        inventory.set(8, new ItemStack(Items.FIREWORK_ROCKET, 48));
        inventory.set(9, new ItemStack(Items.DIAMOND, 32));
        inventory.set(10, new ItemStack(Items.EMERALD, 16));
        inventory.set(14, new ItemStack(Items.IRON_INGOT, 64));
        inventory.set(18, new ItemStack(Items.ENDER_PEARL, 16));
        var inside = InventorySnapshot.emptySlots(27);
        inside.set(0, new ItemStack(Items.DIAMOND, 64));
        inside.set(4, new ItemStack(Items.NETHERITE_PICKAXE));
        inside.get(4).set(DataComponents.CUSTOM_NAME, Component.literal("备用工具"));
        inside.get(4).setDamageValue(321);
        inside.set(13, new ItemStack(Items.EMERALD, 64));
        inside.set(26, new ItemStack(Items.SHULKER_BOX));
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CUSTOM_NAME, Component.literal("远行物资"));
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
        inventory.set(20, box);
        var equipment = InventorySnapshot.emptySlots(6);
        equipment.set(0, new ItemStack(Items.NETHERITE_HELMET));
        equipment.set(1, new ItemStack(Items.ELYTRA));
        equipment.set(2, new ItemStack(Items.NETHERITE_LEGGINGS));
        equipment.set(3, new ItemStack(Items.NETHERITE_BOOTS));
        equipment.set(5, new ItemStack(Items.TOTEM_OF_UNDYING));
        var ender = InventorySnapshot.emptySlots(27);
        ender.set(0, new ItemStack(Items.DIAMOND, 64));
        ender.set(4, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 8));
        ender.set(9, new ItemStack(Items.SHULKER_BOX));
        ender.set(26, new ItemStack(Items.NETHERITE_INGOT, 12));
        return new InventorySnapshot("Alex", false, 1788948000000L, 1788948600000L, 1, inventory, equipment, ender);
    }

    void respond(InventorySnapshot snapshot, long id) throws Exception {
        set(screen, "loading", true);
        set(screen, "pendingId", id);
        JsonObject response = snapshot.toJson(TestItems.REGISTRIES);
        response.addProperty("requestId", id);
        screen.applyResponse(response);
    }

    @Test void oneOccupiedSlotCannotHangAndEmptySlotsAreKept() throws Exception {
        var inventory = InventorySnapshot.emptySlots(36);
        inventory.set(8, new ItemStack(Items.DIAMOND));
        var snapshot = new InventorySnapshot("One", true, -1, 1, 8, inventory,
                InventorySnapshot.emptySlots(6), InventorySnapshot.emptySlots(27));
        assertTimeout(Duration.ofSeconds(2), () -> respond(snapshot, 1));
        assertEquals(69, ((java.util.List<?>) get(screen, "slots")).size());
    }

    @Test void rightClickOpensExactContainerLeftClickDoesNotAndEscapeReturns() throws Exception {
        respond(sample(), 1);
        var layout = (InvseeLayout) get(screen, "layout");
        int x = layout.viewport().x() + layout.gridX(layout.inventory()) + 2 * InvseeLayout.CELL + 5;
        int y = layout.viewport().y() + 30 + InvseeLayout.CELL + 5;
        screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0)), false);
        assertTrue(((Deque<?>) get(screen, "previews")).isEmpty());
        screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0)), false);
        var previews = (Deque<?>) get(screen, "previews");
        assertEquals(1, previews.size());
        var contents = (java.util.List<?>) get(previews.peek(), "slots");
        assertEquals(27, contents.size());
        assertTrue(((ItemStack) contents.get(1)).isEmpty());
        assertEquals(Items.EMERALD, ((ItemStack) contents.get(13)).getItem());
        screen.keyPressed(new KeyEvent(256, 0, 0));
        assertTrue(previews.isEmpty());
        assertEquals("Alex", ((InventorySnapshot) get(screen, "snapshot")).target());
        assertFalse(screen.isPauseScreen());
        network.verifyNoInteractions();
    }

    @Test void staleResponseCannotReplaceCurrentPlayerAndResizeKeepsTheQuery() throws Exception {
        respond(sample(), 10);
        set(screen, "loading", true);
        set(screen, "pendingId", 12L);
        JsonObject stale = sample().toJson(TestItems.REGISTRIES);
        stale.addProperty("requestId", 11L);
        stale.addProperty("target", "Stale");
        screen.applyResponse(stale);
        assertEquals("Alex", ((InventorySnapshot) get(screen, "snapshot")).target());
        ((EditBox) get(screen, "nameField")).setValue("OfflinePlayer");
        screen.width = 320;
        screen.height = 240;
        rebuild();
        assertEquals("OfflinePlayer", ((EditBox) get(screen, "nameField")).getValue());
        assertEquals(69, ((java.util.List<?>) get(screen, "slots")).size());
        assertFalse(((InvseeLayout) get(screen, "layout")).wide());
    }

    @Test void unavailableServerAndTimeoutProduceRecoverableErrors() throws Exception {
        ((EditBox) get(screen, "nameField")).setValue("Alex");
        screen.keyPressed(new KeyEvent(257, 0, 0));
        assertFalse((boolean) get(screen, "loading"));
        assertTrue((boolean) get(screen, "error"));
        network.verify(() -> ClientInvseeNet.request(anyString(), anyLong()), never());
        set(screen, "loading", true);
        set(screen, "pendingId", 5L);
        set(screen, "requestedAt", System.nanoTime() - 11_000_000_000L);
        screen.tick();
        assertFalse((boolean) get(screen, "loading"));
        assertEquals(-1L, get(screen, "pendingId"));
    }

    static Stream<Arguments> containerShapes() {
        return Stream.of(
                Arguments.of(Items.HOPPER, "漏斗", "hopper", new int[][]{{0, 1, 2, 3, 4}}),
                Arguments.of(Items.DROPPER, "投掷器", "dropper", new int[][]{{0, 1, 2}, {3, 4, 5}, {6, 7, 8}}),
                Arguments.of(Items.FURNACE, "熔炉", "furnace", new int[][]{{0, -1, -1, -1}, {-1, -1, -1, 2}, {1, -1, -1, -1}}),
                Arguments.of(Items.BREWING_STAND, "酿造台", "brewing", new int[][]{{4, -1, 3, -1}, {-1, -1, -1, -1}, {-1, 0, 1, 2}}),
                Arguments.of(Items.CHISELED_BOOKSHELF, "雕纹书架", "bookshelf", new int[][]{{0, 1, 2}, {3, 4, 5}}),
                Arguments.of(Items.DECORATED_POT, "饰纹陶罐", "pot", new int[][]{{0}}));
    }

    void inspectContainer(ItemStack container) throws Exception {
        var inventory = InventorySnapshot.emptySlots(36);
        inventory.set(20, container);
        respond(new InventorySnapshot("Alex", false, 1, 1, -1, inventory,
                InventorySnapshot.emptySlots(6), InventorySnapshot.emptySlots(27)), 1);
        var layout = (InvseeLayout) get(screen, "layout");
        screen.mouseClicked(new MouseButtonEvent(layout.viewport().x() + layout.gridX(layout.inventory()) + 49,
                layout.viewport().y() + 57, new MouseButtonInfo(1, 0)), false);
    }

    Object call(String name, Class<?>[] types, Object... args) throws Exception {
        var method = InvseeScreen.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(screen, args);
    }

    @ParameterizedTest @MethodSource("containerShapes")
    void popupDrawsAndHitsOnlyActualSlotsAndCanOpenTheLastSlot(Item item, String name, String file, int[][] cells) throws Exception {
        int capacity = (int) java.util.Arrays.stream(cells).flatMapToInt(java.util.Arrays::stream).filter(i -> i >= 0).count();
        var inside = InventorySnapshot.emptySlots(capacity);
        inside.set(0, new ItemStack(Items.DIAMOND, 32));
        inside.set(capacity - 1, new ItemStack(Items.SHULKER_BOX));
        ItemStack container = new ItemStack(item);
        container.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
        inspectContainer(container);
        var previews = (Deque<?>) get(screen, "previews");
        assertEquals(capacity, ((java.util.List<?>) get(previews.peek(), "slots")).size());
        var rect = (InvseeLayout.Rect) call("previewRect", new Class<?>[]{});
        int left = rect.x() + (rect.width() - cells[0].length * InvseeLayout.CELL) / 2;
        int top = rect.y() + 50;
        int lastX = 0, lastY = 0;
        for (int row = 0; row < cells.length; row++) {
            for (int col = 0; col < cells[row].length; col++) {
                int x = left + col * InvseeLayout.CELL + 5, y = top + row * InvseeLayout.CELL + 5;
                assertEquals(cells[row][col], call("hoveredPreviewIndex", new Class<?>[]{double.class, double.class}, (double) x, (double) y));
                if (cells[row][col] == capacity - 1) { lastX = x; lastY = y; }
                else {
                    screen.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0)), false);
                    assertEquals(1, previews.size(), "Empty slots and layout gaps must not open a container");
                }
            }
        }
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        call("renderPreview", new Class<?>[]{GuiGraphicsExtractor.class, int.class, int.class, float.class}, graphics, -1, -1, 0f);
        verify(graphics, times(capacity * 2)).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(InvseeTheme.SLOT));
        try (var renderer = new SoftwarePreview(640, 360)) {
            renderer.installLanguage();
            screen.extractRenderState(renderer.graphics(), -1, -1, 0);
            renderer.save(file + ".png");
        }
        screen.mouseClicked(new MouseButtonEvent(lastX, lastY, new MouseButtonInfo(1, 0)), false);
        assertEquals(2, previews.size());
        assertEquals(27, ((java.util.List<?>) get(previews.peek(), "slots")).size());
        screen.keyPressed(new KeyEvent(256, 0, 0));
        assertEquals(1, previews.size());
        network.verifyNoInteractions();
    }

    @Test void scrollingPartialLastRowAndResizingKeepsLastItemAccessible() throws Exception {
        ItemStack container = new ItemStack(Items.STONE);
        var inside = InventorySnapshot.emptySlots(56);
        inside.set(55, new ItemStack(Items.SHULKER_BOX));
        container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
        inspectContainer(container);
        screen.width = 320;
        screen.height = 240;
        rebuild();
        for (int i = 0; i < 10; i++) screen.mouseScrolled(160, 120, 0, -1);
        var previews = (Deque<?>) get(screen, "previews");
        assertEquals(1, get(previews.peek(), "scrollRow"));
        var rect = (InvseeLayout.Rect) call("previewRect", new Class<?>[]{});
        int left = rect.x() + (rect.width() - InvseeLayout.GRID_WIDTH) / 2;
        double y = rect.y() + 50 + 5 * InvseeLayout.CELL + 5;
        assertEquals(55, call("hoveredPreviewIndex", new Class<?>[]{double.class, double.class}, (double) left + 27, y));
        assertEquals(-1, call("hoveredPreviewIndex", new Class<?>[]{double.class, double.class}, (double) left + 49, y));
        screen.mouseClicked(new MouseButtonEvent(left + 49, y, new MouseButtonInfo(1, 0)), false);
        assertEquals(1, previews.size());
        screen.mouseClicked(new MouseButtonEvent(left + 27, y, new MouseButtonInfo(1, 0)), false);
        assertEquals(2, previews.size());
        screen.keyPressed(new KeyEvent(256, 0, 0));
        assertEquals(1, get(previews.peek(), "scrollRow"));
    }

    @Test void exportJavaRenderedLayoutPreviews() throws Exception {
        respond(sample(), 1);
        ((EditBox) get(screen, "nameField")).setValue("Alex");
        screen.clearFocus();
        try (var renderer = new SoftwarePreview(640, 360)) {
            renderer.installLanguage();
            screen.extractRenderState(renderer.graphics(), -1, -1, 0);
            renderer.save("overview.png");
        }
        var layout = (InvseeLayout) get(screen, "layout");
        screen.mouseClicked(new MouseButtonEvent(layout.viewport().x() + layout.gridX(layout.inventory()) + 49,
                layout.viewport().y() + 57, new MouseButtonInfo(1, 0)), false);
        try (var renderer = new SoftwarePreview(640, 360)) {
            renderer.installLanguage();
            screen.extractRenderState(renderer.graphics(), -1, -1, 0);
            renderer.save("container.png");
        }
        screen.keyPressed(new KeyEvent(256, 0, 0));
        screen.width = 320;
        screen.height = 240;
        rebuild();
        try (var renderer = new SoftwarePreview(320, 240)) {
            renderer.installLanguage();
            screen.extractRenderState(renderer.graphics(), -1, -1, 0);
            renderer.save("compact.png");
        }
    }
}
