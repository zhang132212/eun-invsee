package com.eun.invsee.service;

import com.eun.invsee.TestItems;
import com.eun.invsee.data.ContainerLayout;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReadOnlyContainerMenuTest {
    @BeforeAll static void bootstrap() { assertNotNull(TestItems.REGISTRIES); }

    static Stream<Arguments> menus() {
        return Stream.of(Arguments.of(Items.HOPPER, 5, MenuType.HOPPER),
                Arguments.of(Items.DROPPER, 9, MenuType.GENERIC_3x3),
                Arguments.of(Items.DISPENSER, 9, MenuType.GENERIC_3x3),
                Arguments.of(Items.CRAFTER, 9, MenuType.GENERIC_3x3),
                Arguments.of(Items.SHULKER_BOX, 27, MenuType.GENERIC_9x3),
                Arguments.of(Items.FURNACE, 3, MenuType.FURNACE),
                Arguments.of(Items.BLAST_FURNACE, 3, MenuType.BLAST_FURNACE),
                Arguments.of(Items.SMOKER, 3, MenuType.SMOKER),
                Arguments.of(Items.BREWING_STAND, 5, MenuType.BREWING_STAND));
    }

    @ParameterizedTest @MethodSource("menus")
    void vanillaMenuMatchesSlotCountAndRejectsEveryClick(Item item, int capacity, MenuType<?> type) {
        var layout = ContainerLayout.of(new ItemStack(item), 0);
        var copy = new SimpleContainer(capacity);
        copy.setItem(capacity - 1, new ItemStack(Items.DIAMOND, 64));
        Inventory inventory = mock(Inventory.class);
        Player player = mock(Player.class);
        var menu = new ReadOnlyContainerMenu(1, inventory, copy, layout);
        assertSame(type, menu.getType());
        assertEquals(capacity + 36, menu.slots.size());
        assertEquals(64, menu.getSlot(capacity - 1).getItem().getCount());
        for (ContainerInput input : ContainerInput.values()) {
            for (int slot : new int[]{-999, 0, capacity - 1, capacity, capacity + 35}) {
                menu.clicked(slot, 0, input, player);
                menu.clicked(slot, 1, input, player);
            }
        }
        assertTrue(menu.quickMoveStack(player, capacity - 1).isEmpty());
        assertFalse(menu.getSlot(0).mayPickup(player));
        assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.STONE)));
        assertEquals(64, copy.getItem(capacity - 1).getCount());
        assertTrue(menu.getCarried().isEmpty());
        verifyNoInteractions(inventory, player);
    }

    @Test void unsupportedVanillaShapesRequireCustomPreviewInsteadOfAddingFakeSlots() {
        assertNull(ReadOnlyContainerMenu.menuType(ContainerLayout.of(new ItemStack(Items.CHISELED_BOOKSHELF), 0)));
        assertNull(ReadOnlyContainerMenu.menuType(ContainerLayout.of(new ItemStack(Items.DECORATED_POT), 0)));
        assertNull(ReadOnlyContainerMenu.menuType(ContainerLayout.of(new ItemStack(Items.HOPPER), 8)));
        assertNull(ReadOnlyContainerMenu.menuType(ContainerLayout.of(new ItemStack(Items.STONE), 56)));
        assertSame(MenuType.GENERIC_9x6, ReadOnlyContainerMenu.menuType(ContainerLayout.of(new ItemStack(Items.STONE), 54)));
    }
}
