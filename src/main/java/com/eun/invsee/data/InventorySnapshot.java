package com.eun.invsee.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/** A detached, fixed-slot snapshot. Full item components survive the network round trip. */
public record InventorySnapshot(String target, boolean online, long savedAt, long capturedAt,
                                int selected, List<ItemStack> inventory,
                                List<ItemStack> equipment, List<ItemStack> ender) {
    public static final int PROTOCOL = 2;
    public static final List<String> EQUIPMENT_KEYS = List.of("head", "chest", "legs", "feet", "body", "offhand");

    public InventorySnapshot {
        inventory = detached(inventory, 36);
        equipment = detached(equipment, 6);
        ender = detached(ender, 27);
        selected = selected >= 0 && selected < 9 ? selected : -1;
    }

    private static List<ItemStack> detached(List<ItemStack> stacks, int size) {
        if (stacks.size() != size) {
            throw new IllegalArgumentException("Expected " + size + " slots, got " + stacks.size());
        }
        return stacks.stream().map(ItemStack::copy).toList();
    }

    public static List<ItemStack> emptySlots(int size) {
        return new ArrayList<>(java.util.Collections.nCopies(size, ItemStack.EMPTY));
    }

    public static int occupied(List<ItemStack> stacks) {
        return (int) stacks.stream().filter(stack -> !stack.isEmpty()).count();
    }

    public static boolean isContainer(ItemStack stack) {
        return !stack.isEmpty() && (stack.get(DataComponents.CONTAINER) != null
                || ContainerLayout.kind(stack) != ContainerLayout.Kind.UNKNOWN);
    }

    /** Fill only the real capacity, preserving holes and any extra recorded slots without data loss. */
    public static List<ItemStack> containerSlots(ItemStack stack) {
        if (!isContainer(stack)) return List.of();
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        List<ItemStack> items = contents == null ? List.of() : contents.allItemsCopyStream().toList();
        int size = Math.max(ContainerLayout.kind(stack).capacity(), items.size());
        List<ItemStack> slots = emptySlots(size);
        for (int i = 0; i < items.size(); i++) {
            slots.set(i, items.get(i));
        }
        return List.copyOf(slots);
    }

    public JsonObject toJson(HolderLookup.Provider registries) {
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        JsonObject root = new JsonObject();
        root.addProperty("protocol", PROTOCOL);
        root.addProperty("ok", true);
        root.addProperty("target", target);
        root.addProperty("online", online);
        root.addProperty("savedAt", savedAt);
        root.addProperty("capturedAt", capturedAt);
        root.addProperty("selected", selected);
        root.add("inventory", encode(inventory, ops));
        root.add("equipment", encode(equipment, ops));
        root.add("ender", encode(ender, ops));
        return root;
    }

    public static InventorySnapshot fromJson(JsonObject root, HolderLookup.Provider registries) {
        if (!root.has("protocol") || root.get("protocol").getAsInt() != PROTOCOL) {
            throw new IllegalArgumentException("Incompatible inventory protocol");
        }
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        return new InventorySnapshot(root.get("target").getAsString(), root.get("online").getAsBoolean(),
                root.get("savedAt").getAsLong(), root.get("capturedAt").getAsLong(),
                root.get("selected").getAsInt(), decode(root.getAsJsonArray("inventory"), 36, ops),
                decode(root.getAsJsonArray("equipment"), 6, ops), decode(root.getAsJsonArray("ender"), 27, ops));
    }

    private static JsonArray encode(List<ItemStack> stacks, RegistryOps<JsonElement> ops) {
        JsonArray out = new JsonArray();
        for (ItemStack stack : stacks) {
            out.add(stack.isEmpty() ? JsonNull.INSTANCE : ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
        }
        return out;
    }

    private static List<ItemStack> decode(JsonArray array, int size, RegistryOps<JsonElement> ops) {
        if (array == null || array.size() != size) {
            throw new IllegalArgumentException("Invalid inventory slot count");
        }
        List<ItemStack> out = emptySlots(size);
        for (int i = 0; i < size; i++) {
            if (!array.get(i).isJsonNull()) {
                out.set(i, ItemStack.CODEC.parse(ops, array.get(i)).getOrThrow());
            }
        }
        return out;
    }
}
