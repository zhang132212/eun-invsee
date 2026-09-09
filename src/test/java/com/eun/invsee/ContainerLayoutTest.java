package com.eun.invsee;

import com.eun.invsee.data.ContainerLayout;
import com.eun.invsee.data.InventorySnapshot;
import com.eun.invsee.service.SnapshotReader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ContainerLayoutTest {
    @BeforeAll static void bootstrap() { assertNotNull(TestItems.REGISTRIES); }

    static Stream<Arguments> containers() {
        return Stream.of(
                Arguments.of(Items.HOPPER, 5, 5, 1),
                Arguments.of(Items.DROPPER, 9, 3, 3),
                Arguments.of(Items.DISPENSER, 9, 3, 3),
                Arguments.of(Items.CRAFTER, 9, 3, 3),
                Arguments.of(Items.FURNACE, 3, 4, 3),
                Arguments.of(Items.BLAST_FURNACE, 3, 4, 3),
                Arguments.of(Items.SMOKER, 3, 4, 3),
                Arguments.of(Items.BREWING_STAND, 5, 4, 3),
                Arguments.of(Items.CHEST, 27, 9, 3),
                Arguments.of(Items.TRAPPED_CHEST, 27, 9, 3),
                Arguments.of(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("copper_chest")), 27, 9, 3),
                Arguments.of(Items.BARREL, 27, 9, 3),
                Arguments.of(Items.SHULKER_BOX, 27, 9, 3),
                Arguments.of(Items.CHISELED_BOOKSHELF, 6, 3, 2),
                Arguments.of(Items.DECORATED_POT, 1, 1, 1),
                Arguments.of(Items.CAMPFIRE, 4, 2, 2),
                Arguments.of(Items.SOUL_CAMPFIRE, 4, 2, 2));
    }

    @ParameterizedTest @MethodSource("containers")
    void actualCapacitySurvivesEmptySparseOfflineAndNetworkData(Item item, int capacity, int columns, int rows) {
        ItemStack container = new ItemStack(item);
        container.remove(DataComponents.CONTAINER);
        assertTrue(InventorySnapshot.isContainer(container), "Empty known containers should open");
        assertEquals(capacity, InventorySnapshot.containerSlots(container).size());
        var layout = ContainerLayout.of(container, 0);
        assertTrue(layout.exactCapacity());
        assertEquals(columns, layout.columns());
        assertEquals(rows, layout.rows());
        assertEquals(capacity, layout.positions().stream().distinct().count());

        var inside = InventorySnapshot.emptySlots(capacity);
        inside.set(0, new ItemStack(Items.DIAMOND, 32));
        container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
        assertEquals(capacity, InventorySnapshot.containerSlots(container).size(), "Trimmed trailing empties are still real slots");
        inside.set(capacity - 1, new ItemStack(Items.EMERALD, 16));
        container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
        CompoundTag entry = (CompoundTag) ItemStack.CODEC.encodeStart(
                TestItems.REGISTRIES.createSerializationContext(NbtOps.INSTANCE), container).getOrThrow();
        entry.putByte("Slot", (byte) 35);
        ListTag inventory = new ListTag();
        inventory.add(entry);
        CompoundTag saved = new CompoundTag();
        saved.put("Inventory", inventory);
        var snapshot = SnapshotReader.offline(saved, TestItems.REGISTRIES, 100, "Offline");
        var received = InventorySnapshot.fromJson(snapshot.toJson(TestItems.REGISTRIES), TestItems.REGISTRIES);
        var slots = InventorySnapshot.containerSlots(received.inventory().get(35));
        assertEquals(capacity, slots.size());
        assertEquals(Items.EMERALD, slots.get(capacity - 1).getItem());
        assertEquals(16, slots.get(capacity - 1).getCount());
        for (int i = 1; i < capacity - 1; i++) assertTrue(slots.get(i).isEmpty());
        slots.get(capacity - 1).setCount(1);
        assertEquals(16, InventorySnapshot.containerSlots(container).get(capacity - 1).getCount());
    }

    @Test void furnaceAndBrewingSlotsKeepTheirStorageRolesAndVisualGaps() {
        var furnace = ContainerLayout.of(new ItemStack(Items.FURNACE), 3);
        assertEquals(0, furnace.indexAt(0, 0));
        assertEquals(1, furnace.indexAt(0, 2));
        assertEquals(2, furnace.indexAt(3, 1));
        assertEquals("output", furnace.positions().get(2).role());
        assertEquals(-1, furnace.indexAt(1, 1));
        var brewing = ContainerLayout.of(new ItemStack(Items.BREWING_STAND), 5);
        assertEquals(0, brewing.indexAt(1, 2));
        assertEquals(2, brewing.indexAt(3, 2));
        assertEquals(3, brewing.indexAt(2, 0));
        assertEquals(4, brewing.indexAt(0, 0));
        assertEquals("ingredient", brewing.positions().get(3).role());
        assertEquals(-1, brewing.indexAt(1, 0));
    }

    @Test void unknownAndOversizedContainersPreserveRecordedSlotsWithoutInventingCapacity() {
        ItemStack unknown = new ItemStack(Items.STONE);
        assertFalse(InventorySnapshot.isContainer(unknown));
        unknown.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        assertTrue(InventorySnapshot.isContainer(unknown));
        assertTrue(InventorySnapshot.containerSlots(unknown).isEmpty());
        var inside = InventorySnapshot.emptySlots(56);
        inside.set(55, new ItemStack(Items.DIAMOND));
        unknown.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
        assertEquals(56, InventorySnapshot.containerSlots(unknown).size());
        var layout = ContainerLayout.of(unknown, 56);
        assertFalse(layout.exactCapacity());
        assertEquals(7, layout.rows());
        assertEquals(55, layout.indexAt(1, 6));
        assertEquals(-1, layout.indexAt(2, 6));
        ItemStack hopper = new ItemStack(Items.HOPPER);
        hopper.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
        assertEquals(Items.DIAMOND, InventorySnapshot.containerSlots(hopper).get(55).getItem());
        assertFalse(ContainerLayout.of(hopper, 56).exactCapacity());
        assertFalse(InventorySnapshot.isContainer(new ItemStack(Items.ENDER_CHEST)), "Ender storage belongs to the player");
    }
}
