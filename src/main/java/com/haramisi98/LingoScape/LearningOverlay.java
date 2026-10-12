package com.haramisi98.LingoScape;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.Keybind;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/** A compact, movable reminder of the display mode and its keyboard shortcut. */
final class LearningOverlay extends OverlayPanel {
    private final TranslationPlugin plugin;
    private final TranslationConfig config;
    private final Client client;

    LearningOverlay(TranslationPlugin plugin, TranslationConfig config, Client client) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(210, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        if (!config.showLearningWidget() || client.getGameState() != GameState.LOGGED_IN
                || config.language() == TranslationConfig.Language.ENGLISH) return null;

        boolean preview = plugin.isEnglishOverride();
        String title = preview ? "English preview" : "Learning: " + config.language();
        Keybind key = config.englishOverrideKey();
        String hint = key == null || Keybind.NOT_SET.equals(key) ? "Set preview hotkey in settings"
                : config.holdToPreview()
                ? (preview ? "Release " + key + ": resume learning" : "Hold " + key + ": show English")
                : key + (preview ? ": resume learning" : ": show English");
        panelComponent.getChildren().add(LineComponent.builder().left(title)
                .leftColor(preview ? Color.ORANGE : Color.CYAN).build());
        panelComponent.getChildren().add(LineComponent.builder().left(hint).build());
        return super.render(graphics);
    }
}
