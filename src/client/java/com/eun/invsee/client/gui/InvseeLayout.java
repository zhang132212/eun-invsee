package com.eun.invsee.client.gui;

/** GUI-scaled coordinates, independent of Minecraft rendering and item counts. */
public record InvseeLayout(Rect panel, Rect viewport, Rect inventory, Rect equipment, Rect ender,
                           int contentHeight, boolean wide) {
    public static final int CELL = 22;
    public static final int GRID_WIDTH = CELL * 9;

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    public static InvseeLayout create(int width, int height) {
        int w = Math.min(520, width - 16);
        int h = Math.min(354, height - 16);
        Rect panel = new Rect((width - w) / 2, (height - h) / 2, w, h);
        Rect viewport = new Rect(panel.x + 12, panel.y + 96, w - 24, h - 120);
        boolean wide = viewport.width >= 444;
        int cardWidth = wide ? (viewport.width - 12) / 2 : viewport.width - 8;
        Rect inventory = new Rect(0, 0, cardWidth, 154);
        Rect ender = new Rect(wide ? cardWidth + 12 : 0, wide ? 0 : 254, cardWidth, 110);
        Rect equipment = new Rect(wide ? cardWidth + 12 : 0, wide ? 122 : 166, cardWidth, 76);
        return new InvseeLayout(panel, viewport, inventory, equipment, ender,
                wide ? 198 : 364, wide);
    }

    public int maxScroll() { return Math.max(0, contentHeight - viewport.height); }
    public int clampScroll(int value) { return Math.max(0, Math.min(value, maxScroll())); }
    public int gridX(Rect card) { return card.x + (card.width - GRID_WIDTH) / 2; }
}
