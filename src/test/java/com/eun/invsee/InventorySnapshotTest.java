package com.eun.invsee;

import com.eun.invsee.data.InventorySnapshot;
import com.eun.invsee.service.SnapshotReader;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InventorySnapshotTest {
    static HolderLookup.Provider registries;

    @BeforeAll static void bootstrap() {
        registries = TestItems.REGISTRIES;
    }

    static ItemStack detailedTool() {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        tool.setDamageValue(321);
        tool.set(DataComponents.CUSTOM_NAME, Component.literal("离线玩家的镐子"));
        tool.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY), 5);
        return tool;
    }

    static CompoundTag encode(ItemStack stack) {
        return (CompoundTag) ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    }

    @Test void offlineSaveAndNetworkRoundTripPreserveSlotsAndEveryItemComponent() {
        ItemStack tool = detailedTool();
        var inside = InventorySnapshot.emptySlots(27);
        inside.set(20, tool);
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
        CompoundTag root = new CompoundTag();
        ListTag inventory = new ListTag();
        CompoundTag entry = encode(box);
        entry.putByte("Slot", (byte) 35);
        inventory.add(entry);
        root.put("Inventory", inventory);
        root.putInt("SelectedItemSlot", 8);
        CompoundTag equipment = new CompoundTag();
        equipment.put("head", encode(new ItemStack(Items.DIAMOND_HELMET)));
        equipment.put("offhand", encode(new ItemStack(Items.TOTEM_OF_UNDYING)));
        root.put("equipment", equipment);
        ListTag ender = new ListTag();
        CompoundTag last = encode(new ItemStack(Items.DIAMOND, 64));
        last.putByte("Slot", (byte) 26);
        ender.add(last);
        root.put("EnderItems", ender);
        CompoundTag before = root.copy();

        var saved = SnapshotReader.offline(root, registries, 123456L, "OfflinePlayer");
        var client = InventorySnapshot.fromJson(saved.toJson(registries), registries);
        assertFalse(client.online());
        assertEquals(123456L, client.savedAt());
        assertEquals("OfflinePlayer", client.target());
        assertEquals(8, client.selected());
        assertEquals(36, client.inventory().size());
        assertTrue(client.inventory().get(0).isEmpty());
        assertEquals(1, InventorySnapshot.occupied(client.inventory()));
        assertEquals(Items.SHULKER_BOX, client.inventory().get(35).getItem());
        assertEquals(Items.DIAMOND_HELMET, client.equipment().get(0).getItem());
        assertTrue(client.equipment().get(4).isEmpty());
        assertEquals(Items.TOTEM_OF_UNDYING, client.equipment().get(5).getItem());
        assertEquals(64, client.ender().get(26).getCount());
        var contents = InventorySnapshot.containerSlots(client.inventory().get(35));
        assertEquals(27, contents.size());
        assertTrue(contents.get(0).isEmpty());
        assertTrue(contents.get(26).isEmpty());
        assertTrue(ItemStack.matches(tool, contents.get(20)));
        assertEquals(before, root, "Inspection must not change saved NBT");
    }

    @Test void emptyShulkerStillOpensAsTwentySevenEmptySlots() {
        ItemStack emptyBox = new ItemStack(Items.SHULKER_BOX);
        assertTrue(InventorySnapshot.isContainer(emptyBox));
        assertEquals(27, InventorySnapshot.containerSlots(emptyBox).size());
        assertEquals(0, InventorySnapshot.occupied(InventorySnapshot.containerSlots(emptyBox)));
        assertFalse(InventorySnapshot.isContainer(new ItemStack(Items.STONE)));
    }

    @Test void copiedContentsKeepNestedContainersAndDoNotModifyTheSource() {
        var innermost = InventorySnapshot.emptySlots(27);
        innermost.set(26, detailedTool());
        ItemStack inner = new ItemStack(Items.SHULKER_BOX);
        inner.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(innermost));
        var outerSlots = InventorySnapshot.emptySlots(27);
        outerSlots.set(8, inner);
        ItemStack outer = new ItemStack(Items.SHULKER_BOX);
        outer.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(outerSlots));
        var preview = InventorySnapshot.containerSlots(outer);
        var nested = InventorySnapshot.containerSlots(preview.get(8));
        assertTrue(ItemStack.matches(detailedTool(), nested.get(26)));
        nested.get(26).setCount(0);
        preview.get(8).setCount(0);
        var reopened = InventorySnapshot.containerSlots(outer);
        assertFalse(reopened.get(8).isEmpty());
        assertFalse(InventorySnapshot.containerSlots(reopened.get(8)).get(26).isEmpty());
    }

    @Test void invalidAndEmptyOfflineEntriesCannotShiftOtherSlots() {
        CompoundTag root = new CompoundTag();
        ListTag inv = new ListTag();
        for (int slot : new int[]{0, 2, 35, 99, 255}) {
            CompoundTag item = encode(new ItemStack(Items.STONE));
            item.putByte("Slot", (byte) slot);
            if (slot == 2) item.putInt("count", 0);
            inv.add(item);
        }
        root.put("Inventory", inv);
        var snapshot = SnapshotReader.offline(root, registries, -1, "Test");
        assertEquals(2, InventorySnapshot.occupied(snapshot.inventory()));
        assertTrue(snapshot.inventory().get(2).isEmpty());
        assertFalse(snapshot.inventory().get(35).isEmpty());
        assertEquals(-1, snapshot.selected());
    }

    @Test void snapshotDoesNotRetainLiveStackReferencesAndRejectsTruncatedPackets() {
        ItemStack live = new ItemStack(Items.DIAMOND, 64);
        var inv = InventorySnapshot.emptySlots(36);
        inv.set(0, live);
        var snapshot = new InventorySnapshot("Live", true, -1, 20, 0, inv,
                InventorySnapshot.emptySlots(6), InventorySnapshot.emptySlots(27));
        live.setCount(1);
        assertEquals(64, snapshot.inventory().get(0).getCount());
        snapshot.inventory().get(0).setCount(12);
        assertEquals(1, live.getCount());
        JsonObject packet = snapshot.toJson(registries);
        packet.getAsJsonArray("inventory").remove(35);
        assertThrows(IllegalArgumentException.class, () -> InventorySnapshot.fromJson(packet, registries));
    }

    @Test void liveReaderCopiesInventoryEquipmentAndEnderChestWithoutSaving() {
        var player = mock(net.minecraft.server.level.ServerPlayer.class);
        var inventory = mock(net.minecraft.world.entity.player.Inventory.class);
        var ender = mock(net.minecraft.world.inventory.PlayerEnderChestContainer.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getEnderChestInventory()).thenReturn(ender);
        when(player.getGameProfile()).thenReturn(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "OnlinePlayer"));
        when(inventory.getItem(anyInt())).thenReturn(ItemStack.EMPTY);
        when(player.getItemBySlot(any())).thenReturn(ItemStack.EMPTY);
        when(ender.getItem(anyInt())).thenReturn(ItemStack.EMPTY);
        when(ender.getContainerSize()).thenReturn(27);
        when(inventory.getSelectedSlot()).thenReturn(8);
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 64);
        when(inventory.getItem(35)).thenReturn(diamonds);
        when(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND)).thenReturn(new ItemStack(Items.TOTEM_OF_UNDYING));
        when(ender.getItem(26)).thenReturn(new ItemStack(Items.EMERALD, 32));
        var snapshot = SnapshotReader.online(player);
        assertTrue(snapshot.online());
        assertEquals("OnlinePlayer", snapshot.target());
        assertEquals(8, snapshot.selected());
        assertEquals(-1L, snapshot.savedAt());
        assertEquals(Items.TOTEM_OF_UNDYING, snapshot.equipment().get(5).getItem());
        assertEquals(32, snapshot.ender().get(26).getCount());
        snapshot.inventory().get(35).setCount(1);
        assertEquals(64, diamonds.getCount());
        verify(inventory, never()).setItem(anyInt(), any());
        verify(ender, never()).setItem(anyInt(), any());
    }

    @Test void commandLocationCodesPreserveEquipmentNamesAndRejectInvalidIndices() {
        var inventory = InventorySnapshot.emptySlots(36);
        inventory.set(8, new ItemStack(Items.DIAMOND));
        inventory.set(35, new ItemStack(Items.EMERALD));
        var eq = InventorySnapshot.emptySlots(6);
        eq.set(0, new ItemStack(Items.DIAMOND_HELMET));
        var snapshot = new InventorySnapshot("Codes", false, -1, 1, -1, inventory, eq, InventorySnapshot.emptySlots(27));
        assertEquals(Items.DIAMOND, SnapshotReader.itemAt(snapshot, "h8").getItem());
        assertEquals(Items.EMERALD, SnapshotReader.itemAt(snapshot, "m26").getItem());
        assertEquals(Items.DIAMOND_HELMET, SnapshotReader.itemAt(snapshot, "head").getItem());
        assertTrue(SnapshotReader.itemAt(snapshot, "e26").isEmpty());
        for (String invalid : new String[]{"h9", "m27", "e-1", "x0", "h", "", null}) {
            assertThrows(IllegalArgumentException.class, () -> SnapshotReader.itemAt(snapshot, invalid));
        }
    }
}
