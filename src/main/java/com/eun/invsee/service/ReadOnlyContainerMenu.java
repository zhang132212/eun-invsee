package com.eun.invsee.service;

import com.eun.invsee.data.ContainerLayout;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Uses a matching vanilla client screen, with detached slots and no item transactions. */
final class ReadOnlyContainerMenu extends AbstractContainerMenu {
    static MenuType<?> menuType(ContainerLayout layout) {
        if (!layout.exactCapacity()) {
            // A partial/unknown row must not become fictitious empty inventory slots.
            return layout.kind() == ContainerLayout.Kind.UNKNOWN ? chestType(layout.positions().size()) : null;
        }
        return switch (layout.kind()) {
            case CHEST -> MenuType.GENERIC_9x3;
            case HOPPER -> MenuType.HOPPER;
            case DISPENSER, CRAFTER -> MenuType.GENERIC_3x3;
            case FURNACE -> MenuType.FURNACE;
            case BLAST_FURNACE -> MenuType.BLAST_FURNACE;
            case SMOKER -> MenuType.SMOKER;
            case BREWING -> MenuType.BREWING_STAND;
            default -> null;
        };
    }

    private static MenuType<?> chestType(int size) {
        return switch (size) {
            case 9 -> MenuType.GENERIC_9x1;
            case 18 -> MenuType.GENERIC_9x2;
            case 27 -> MenuType.GENERIC_9x3;
            case 36 -> MenuType.GENERIC_9x4;
            case 45 -> MenuType.GENERIC_9x5;
            case 54 -> MenuType.GENERIC_9x6;
            default -> null;
        };
    }

    ReadOnlyContainerMenu(int id, Inventory inventory, Container box, ContainerLayout layout) {
        super(java.util.Objects.requireNonNull(menuType(layout), "No matching vanilla screen"), id);
        checkContainerSize(box, layout.positions().size());
        for (int i = 0; i < layout.positions().size(); i++) {
            var position = layout.positions().get(i);
            addSlot(new Slot(box, i, 8 + position.column() * 18, 18 + position.row() * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        addStandardInventorySlots(inventory, 8, 84);
        // Native furnace/brewing screens expect these data fields; the preview has no running process.
        int dataCount = switch (layout.kind()) {
            case FURNACE, BLAST_FURNACE, SMOKER -> 4;
            case BREWING -> 2;
            default -> 0;
        };
        addDataSlots(new SimpleContainerData(dataCount));
    }

    @Override public void clicked(int slotId, int button, ContainerInput input, Player player) {}
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return true; }
    @Override public boolean canDragTo(Slot slot) { return false; }
}
