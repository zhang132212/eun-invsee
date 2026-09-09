package com.eun.invsee.client.gui;

import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.mockito.Mockito;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

/** Software layout proof from the production screen's draw calls, not an in-game GPU screenshot. */
final class SoftwarePreview implements AutoCloseable {
    private final BufferedImage image;
    private final Graphics2D canvas;
    private final GuiGraphicsExtractor graphics;
    private final Language previousLanguage = Language.getInstance();
    private final Map<String, BufferedImage> textures = new HashMap<>();
    private final ZipFile assets;

    SoftwarePreview(int width, int height) throws Exception {
        image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_ARGB);
        canvas = image.createGraphics();
        canvas.scale(2, 2);
        canvas.setColor(new Color(0x1C2C31));
        canvas.fillRect(0, 0, width, height);
        canvas.setFont(new java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, 9));
        canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Path jar = Path.of(System.getProperty("invsee.assetsJar", "missing"));
        assets = Files.isRegularFile(jar) ? new ZipFile(jar.toFile()) : null;
        graphics = Mockito.mock(GuiGraphicsExtractor.class, invocation -> {
            Object[] a = invocation.getArguments();
            switch (invocation.getMethod().getName()) {
                case "fill" -> {
                    if (a.length == 5 && a[0] instanceof Integer) {
                        canvas.setColor(new Color((int) a[4], true));
                        canvas.fillRect((int) a[0], (int) a[1], (int) a[2] - (int) a[0], (int) a[3] - (int) a[1]);
                    }
                }
                case "text" -> {
                    if (a.length >= 5) {
                        canvas.setColor(new Color((int) a[4], true));
                        canvas.drawString(string(a[1]), (int) a[2], (int) a[3] + 8);
                    }
                }
                case "enableScissor" -> canvas.setClip((int) a[0], (int) a[1], (int) a[2] - (int) a[0], (int) a[3] - (int) a[1]);
                case "disableScissor" -> canvas.setClip(null);
                case "item" -> drawItem((ItemStack) a[0], (int) a[1], (int) a[2]);
                case "itemDecorations" -> decorations((ItemStack) a[1], (int) a[2], (int) a[3]);
                default -> { return Mockito.RETURNS_DEEP_STUBS.answer(invocation); }
            }
            return null;
        });
    }

    GuiGraphicsExtractor graphics() { return graphics; }

    void installLanguage() throws Exception {
        Map<String, String> translations = new HashMap<>();
        try (InputStream in = getClass().getResourceAsStream("/assets/eun_invsee/lang/zh_cn.json")) {
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
                    .entrySet().forEach(e -> translations.put(e.getKey(), e.getValue().getAsString()));
        }
        Language.inject(new Language() {
            @Override public String getOrDefault(String key, String fallback) {
                return translations.getOrDefault(key, previousLanguage.getOrDefault(key, fallback));
            }
            @Override public boolean has(String key) { return translations.containsKey(key) || previousLanguage.has(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.forward(text.getString(), Style.EMPTY);
            }
        });
    }

    static String string(Object value) {
        if (value instanceof FormattedText text) return text.getString();
        if (value instanceof FormattedCharSequence sequence) {
            StringBuilder result = new StringBuilder();
            sequence.accept((index, style, cp) -> { result.appendCodePoint(cp); return true; });
            return result.toString();
        }
        return String.valueOf(value);
    }

    private void drawItem(ItemStack stack, int x, int y) throws Exception {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (id.contains("shulker_box")) {
            canvas.setColor(new Color(0xA788B6));
            canvas.fill(new Polygon(new int[]{x + 1, x + 8, x + 15, x + 8}, new int[]{y + 4, y + 1, y + 4, y + 7}, 4));
            canvas.setColor(new Color(0x775785));
            canvas.fill(new Polygon(new int[]{x + 1, x + 8, x + 8, x + 1}, new int[]{y + 4, y + 7, y + 16, y + 12}, 4));
            canvas.setColor(new Color(0x9971A6));
            canvas.fill(new Polygon(new int[]{x + 8, x + 15, x + 15, x + 8}, new int[]{y + 7, y + 4, y + 12, y + 16}, 4));
            canvas.setColor(new Color(0xC6A7D5));
            canvas.drawLine(x + 2, y + 7, x + 7, y + 9);
            return;
        }
        if (!textures.containsKey(id)) {
            BufferedImage icon = null;
            String path = "assets/minecraft/textures/item/" + id + ".png";
            if (assets != null && assets.getEntry(path) != null) {
                try (InputStream in = assets.getInputStream(assets.getEntry(path))) { icon = ImageIO.read(in); }
            }
            textures.put(id, icon);
        }
        BufferedImage icon = textures.get(id);
        if (icon != null) canvas.drawImage(icon, x, y, 16, 16, null);
        else {
            canvas.setColor(new Color(0x819BA2));
            canvas.fillRect(x + 3, y + 3, 10, 10);
        }
    }

    private void decorations(ItemStack stack, int x, int y) {
        if (stack.getCount() > 1) {
            String count = Integer.toString(stack.getCount());
            canvas.setColor(Color.BLACK);
            canvas.drawString(count, x + 17 - count.length() * 5, y + 17);
            canvas.setColor(Color.WHITE);
            canvas.drawString(count, x + 16 - count.length() * 5, y + 16);
        }
        if (stack.isBarVisible()) {
            canvas.setColor(Color.BLACK);
            canvas.fillRect(x + 2, y + 13, 13, 2);
            canvas.setColor(new Color(stack.getBarColor()));
            canvas.fillRect(x + 2, y + 13, stack.getBarWidth(), 1);
        }
    }

    void save(String name) throws Exception {
        Path directory = Path.of("../preview");
        Files.createDirectories(directory);
        ImageIO.write(image, "png", directory.resolve(name).toFile());
    }

    @Override public void close() throws Exception {
        Language.inject(previousLanguage);
        canvas.dispose();
        if (assets != null) assets.close();
    }
}
