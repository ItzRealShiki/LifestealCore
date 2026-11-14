package de.leon.provisuals;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ProVisualsScreen extends Screen {
    private int panelLeft;
    private int panelTop;

    public ProVisualsScreen() {
        super(Component.literal("ＰＲＯＶＩＳＵＡＬＳ"));
    }

    @Override
    protected void init() {
        panelLeft = (width - 420) / 2;
        panelTop = (height - 250) / 2;
        int x = panelLeft + 24;
        int y = panelTop + 82;
        Button placeButton = Button.builder(placeLabel(), button -> {
            ProVisualsClient.toggleFastPlace();
            button.setMessage(placeLabel());
        }).bounds(x, y, 178, 30).build();
        addRenderableWidget(placeButton);
        Button breakButton = Button.builder(breakLabel(), button -> {
            ProVisualsClient.toggleFastBreak();
            button.setMessage(breakLabel());
        }).bounds(x + 190, y, 178, 30).build();
        addRenderableWidget(breakButton);
        addRenderableWidget(Button.builder(Component.literal("ＣＬＯＳＥ"), button -> onClose())
                .bounds(panelLeft + 145, panelTop + 195, 130, 28).build());
    }

    private static Component placeLabel() {
        return Component.literal("ＦＡＳＴＰＬＡＣＥ  " + (ProVisualsClient.isFastPlaceEnabled() ? "ＯＮ" : "ＯＦＦ"));
    }
    private static Component breakLabel() {
        return Component.literal("ＦＡＳＴＢＲＥＡＫ  " + (ProVisualsClient.isFastBreakEnabled() ? "ＯＮ" : "ＯＦＦ"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int right = panelLeft + 420;
        int bottom = panelTop + 250;
        graphics.fill(panelLeft, panelTop, right, bottom, 0xEE11141D);
        graphics.fill(panelLeft, panelTop, right, panelTop + 3, 0xFF9B7BFF);
        graphics.text(font, "ＰＲＯＶＩＳＵＡＬＳ", panelLeft + 24, panelTop + 24, 0xFFFFFFFF);
        graphics.text(font, "ＷＯＲＫＳＰＡＣＥ  /  ＭＯＤＵＬＥＳ", panelLeft + 24, panelTop + 48, 0xFFAAAFC2, false);
        graphics.text(font, ProVisualsClient.isServerControlled()
                ? "ＳＥＲＶＥＲ  ＭＡＮＡＧＥＤ" : "ＳＩＮＧＬＥＰＬＡＹＥＲ  /  ＬＯＣＡＬ", panelLeft + 24, panelTop + 132, 0xFFB9A6FF, false);
        graphics.text(font, ProVisualsClient.isServerControlled()
                ? "ＵＳＥ  /ＳＥＴＴＩＮＧＳ  ＴＯ  ＣＨＡＮＧＥ  ＳＥＲＶＥＲ  ＳＴＡＴＥ"
                : "ＦＡＳＴＢＲＥＡＫ  ＩＳ  ＣＲＥＡＴＩＶＥ  ＯＮＬＹ", panelLeft + 24, panelTop + 151, 0xFF8F96AA, false);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
