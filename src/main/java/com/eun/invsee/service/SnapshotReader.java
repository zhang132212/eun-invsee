package com.eun.invsee.service;

import com.eun.invsee.data.InventorySnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** All reads use detached stacks. Offline data is decoded with the same vanilla codec as live items. */
public final class SnapshotReader {
    private SnapshotReader() {}

    public static ItemStack itemAt(InventorySnapshot snapshot, String code) {
        if (code == null || code.length() < 2) throw new IllegalArgumentException("无效的槽位。");
        int equipment = InventorySnapshot.EQUIPMENT_KEYS.indexOf(code);
        if (equipment >= 0) return snapshot.equipment().get(equipment).copy();
        int index;
        try {
            index = Integer.parseInt(code.substring(1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的槽位。", e);
        }
        int limit = code.charAt(0) == 'h' ? 9 : 27;
        if (index < 0 || index >= limit) throw new IllegalArgumentException("槽位超出范围。");
        return switch (code.charAt(0)) {
            case 'h' -> snapshot.inventory().get(index).copy();
            case 'm' -> snapshot.inventory().get(9 + index).copy();
            case 'e' -> snapshot.ender().get(index).copy();
            default -> throw new IllegalArgumentException("未知的槽位类型。");
        };
    }

    public static InventorySnapshot read(TargetResolver.Resolved target, MinecraftServer server) {
        if (target.online() != null) {
            return online(target.online());
        }
        if (target.data() == null) {
            throw new IllegalArgumentException("玩家没有可读存档。");
        }
        return offline(target.data(), server.registryAccess(), target.savedAtMs(), target.canonName());
    }

    public static InventorySnapshot online(ServerPlayer player) {
        List<ItemStack> inventory = InventorySnapshot.emptySlots(36);
        List<ItemStack> equipment = InventorySnapshot.emptySlots(6);
        List<ItemStack> ender = InventorySnapshot.emptySlots(27);
        for (int i = 0; i < inventory.size(); i++) {
            inventory.set(i, player.getInventory().getItem(i));
        }
        for (int i = 0; i < equipment.size(); i++) {
            equipment.set(i, player.getItemBySlot(EquipmentSlot.byName(InventorySnapshot.EQUIPMENT_KEYS.get(i))));
        }
        for (int i = 0; i < Math.min(27, player.getEnderChestInventory().getContainerSize()); i++) {
            ender.set(i, player.getEnderChestInventory().getItem(i));
        }
        return new InventorySnapshot(player.getGameProfile().name(), true, -1, System.currentTimeMillis(),
                player.getInventory().getSelectedSlot(), inventory, equipment, ender);
    }

    public static InventorySnapshot offline(CompoundTag root, HolderLookup.Provider registries, long savedAt, String name) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        List<ItemStack> inventory = readSlots(root.getListOrEmpty("Inventory"), 36, ops);
        List<ItemStack> ender = readSlots(root.getListOrEmpty("EnderItems"), 27, ops);
        List<ItemStack> equipment = InventorySnapshot.emptySlots(6);
        CompoundTag eq = root.getCompoundOrEmpty("equipment");
        for (int i = 0; i < equipment.size(); i++) {
            String key = InventorySnapshot.EQUIPMENT_KEYS.get(i);
            equipment.set(i, decode(eq.getCompoundOrEmpty(key), ops));
        }
        return new InventorySnapshot(name, false, savedAt, System.currentTimeMillis(),
                root.getIntOr("SelectedItemSlot", -1), inventory, equipment, ender);
    }

    private static List<ItemStack> readSlots(ListTag tags, int size, RegistryOps<Tag> ops) {
        List<ItemStack> stacks = InventorySnapshot.emptySlots(size);
        for (int i = 0; i < tags.size(); i++) {
            if (!(tags.get(i) instanceof CompoundTag entry)) {
                continue;
            }
            int slot = entry.contains("Slot") ? entry.getByteOr("Slot", (byte) -1) & 255 : i;
            if (slot >= 0 && slot < size) {
                stacks.set(slot, decode(entry, ops));
            }
        }
        return stacks;
    }

    private static ItemStack decode(CompoundTag tag, RegistryOps<Tag> ops) {
        if (tag.getStringOr("id", "minecraft:air").equals("minecraft:air") || tag.getIntOr("count", 0) <= 0) {
            return ItemStack.EMPTY;
        }
        return ItemStack.CODEC.parse(ops, tag).getOrThrow();
    }
}
