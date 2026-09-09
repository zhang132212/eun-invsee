package com.eun.invsee.client.gui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class InvseeLayoutTest {
    @ParameterizedTest
    @CsvSource({"320,240", "427,240", "480,270", "640,360", "854,480", "1280,720"})
    void everySlotRemainsInsideItsCardAndScrollableViewport(int width, int height) {
        var ui = InvseeLayout.create(width, height);
        assertTrue(ui.panel().x() >= 0 && ui.panel().right() <= width);
        assertTrue(ui.panel().y() >= 0 && ui.panel().bottom() <= height);
        assertTrue(ui.viewport().height() > 0);
        for (var card : new InvseeLayout.Rect[]{ui.inventory(), ui.equipment(), ui.ender()}) {
            assertTrue(card.x() >= 0 && card.right() <= ui.viewport().width());
            assertTrue(card.bottom() <= ui.contentHeight());
            assertTrue(card.width() >= InvseeLayout.GRID_WIDTH);
        }
        assertEquals(0, ui.clampScroll(-100));
        assertEquals(ui.maxScroll(), ui.clampScroll(10000));
        assertTrue(ui.contentHeight() - ui.maxScroll() <= ui.viewport().height());
        var grid = new InvseeLayout.Rect(20, 30, 198, 66);
        assertFalse(grid.contains(19.9, 35));
        assertFalse(grid.contains(218, 35));
        assertFalse(grid.contains(30, 96));
        assertTrue(grid.contains(20, 30));
    }
}
