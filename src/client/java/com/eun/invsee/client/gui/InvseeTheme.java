package com.eun.invsee.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class InvseeTheme {
    static final int PANEL = 0xFF111C23;
    static final int CARD = 0xFF19282F;
    static final int BORDER = 0xFF30434B;
    static final int SLOT = 0xFF101C22;
    static final int TEXT = 0xFFE9F2F0;
    static final int MUTED = 0xFF97ADB5;
    static final int ACCENT = 0xFF80DBC0;
    static final int PURPLE = 0xFFC4ACEC;
    static final int ERROR = 0xFFFFA99B;

    private InvseeTheme() {}

    static void surface(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x + 2, y, x + w - 2, y + h, border);
        g.fill(x, y + 2, x + w, y + h - 2, border);
        g.fill(x + 1, y + 2, x + w - 1, y + h - 2, fill);
        g.fill(x + 2, y + 1, x + w - 2, y + h - 1, fill);
    }

    static final class ActionButton extends Button {
        private final Font font;
        private final boolean primary;

        ActionButton(Font font, int x, int y, int w, int h, Component label, boolean primary, OnPress action) {
            super(x, y, w, h, label, action, DEFAULT_NARRATION);
            this.font = font;
            this.primary = primary;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            boolean hover = isHoveredOrFocused() && active;
            int bg = !active ? CARD : primary ? (hover ? 0xFFA2E8D4 : ACCENT) : (hover ? 0xFF30474F : CARD);
            int fg = !active ? MUTED : primary ? 0xFF142C28 : TEXT;
            surface(g, getX(), getY(), getWidth(), getHeight(), bg, hover ? ACCENT : BORDER);
            g.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2,
                    getY() + (getHeight() - 9) / 2, fg, false);
        }
    }
}
