package com.eun.invsee.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;

import java.util.List;
import java.util.stream.IntStream;

/** Actual container slots, in storage order. Gaps in a layout are not inventory slots. */
public record ContainerLayout(Kind kind, int columns, int rows, List<Position> positions) {
    public record Position(int column, int row, String role) {
        public Position(int column, int row) { this(column, row, ""); }
    }

    public enum Kind {
        UNKNOWN(0, 9),
        CHEST(27, 9), HOPPER(5, 5), DISPENSER(9, 3), CRAFTER(9, 3),
        FURNACE(3, 4), BLAST_FURNACE(3, 4), SMOKER(3, 4),
        BREWING(5, 4), BOOKSHELF(6, 3), POT(1, 1), CAMPFIRE(4, 2);

        private final int capacity;
        private final int columns;

        Kind(int capacity, int columns) {
            this.capacity = capacity;
            this.columns = columns;
        }

        public int capacity() { return capacity; }
    }

    public ContainerLayout {
        positions = List.copyOf(positions);
    }

    public static Kind kind(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return Kind.UNKNOWN;
        Block block = item.getBlock();
        // A modded subclass may change capacity; do not invent its missing trailing slots.
        if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")) return Kind.UNKNOWN;
        if (block instanceof HopperBlock) return Kind.HOPPER;
        if (block instanceof DispenserBlock) return Kind.DISPENSER; // Includes droppers.
        if (block instanceof CrafterBlock) return Kind.CRAFTER;
        if (block instanceof BlastFurnaceBlock) return Kind.BLAST_FURNACE;
        if (block instanceof SmokerBlock) return Kind.SMOKER;
        if (block instanceof AbstractFurnaceBlock) return Kind.FURNACE;
        if (block instanceof BrewingStandBlock) return Kind.BREWING;
        if (block instanceof ChiseledBookShelfBlock) return Kind.BOOKSHELF;
        if (block instanceof DecoratedPotBlock) return Kind.POT;
        if (block instanceof CampfireBlock) return Kind.CAMPFIRE;
        if (block instanceof ShulkerBoxBlock || block instanceof ChestBlock || block instanceof BarrelBlock) {
            return Kind.CHEST;
        }
        return Kind.UNKNOWN;
    }

    public static ContainerLayout of(ItemStack stack, int storedSlots) {
        Kind kind = kind(stack);
        int size = Math.max(storedSlots, kind.capacity);
        if (kind == Kind.UNKNOWN || storedSlots > kind.capacity) {
            int columns = Math.min(9, Math.max(1, size));
            return new ContainerLayout(kind, columns, Math.max(1, (size + columns - 1) / columns), grid(size, columns));
        }
        List<Position> positions = switch (kind) {
            case FURNACE, BLAST_FURNACE, SMOKER -> List.of(
                    new Position(0, 0, "input"), new Position(0, 2, "fuel"), new Position(3, 1, "output"));
            case BREWING -> List.of(new Position(1, 2, "bottle"), new Position(2, 2, "bottle"),
                    new Position(3, 2, "bottle"), new Position(2, 0, "ingredient"), new Position(0, 0, "fuel"));
            default -> grid(size, kind.columns);
        };
        int rows = positions.stream().mapToInt(Position::row).max().orElse(0) + 1;
        return new ContainerLayout(kind, kind.columns, rows, positions);
    }

    public boolean exactCapacity() { return kind != Kind.UNKNOWN && positions.size() == kind.capacity; }

    /** Returns the storage index, or -1 for a visual gap or an unrecorded slot. */
    public int indexAt(int column, int row) {
        for (int i = 0; i < positions.size(); i++) {
            Position position = positions.get(i);
            if (position.column == column && position.row == row) return i;
        }
        return -1;
    }

    private static List<Position> grid(int size, int columns) {
        return IntStream.range(0, size).mapToObj(i -> new Position(i % columns, i / columns)).toList();
    }
}
