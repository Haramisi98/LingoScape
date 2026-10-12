package com.haramisi98.LingoScape;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.ConfigSection;
import java.awt.event.KeyEvent;

@ConfigGroup(TranslationConfig.GROUP)
public interface TranslationConfig extends Config {
    String GROUP = "lingoscape";

    @ConfigItem(
            keyName = "language",
            name = "Language",
            description = "Choose Danish translations or keep the original English text"
    )
    default Language language() {
        return Language.DANISH;
    }

    @ConfigItem(keyName = "collectMissing", name = "Collect missing translations",
            description = "Save missing game text locally, up to 10,000 entries / 10 MB. Disabled for English.")
    default boolean collectMissing() { return false; }

    @ConfigSection(name = "Preview English while learning",
            description = "Check original English using the hotkey. Choose toggle or hold behavior below. Your saved language stays unchanged.",
            position = 10)
    String englishPreview = "englishPreview";

    @ConfigItem(keyName = "englishOverrideKey", name = "English preview / resume learning",
            section = englishPreview,
            description = "Default: Tab. Toggle English with a press, or enable Hold to preview for English only while held. Open menus update when reopened. Your saved language is unchanged.")
    default Keybind englishOverrideKey() { return new Keybind(KeyEvent.VK_TAB, 0); }

    @ConfigItem(keyName = "holdToPreview", name = "Hold to preview", section = englishPreview,
            description = "Hold the hotkey for English; release it to resume learning. When off, press to toggle. Changing this setting resets preview.")
    default boolean holdToPreview() { return false; }

    @ConfigItem(keyName = "showLearningWidget", name = "Show learning widget", section = englishPreview,
            description = "Show the current language and hotkey in the top-left of the game view.")
    default boolean showLearningWidget() { return true; }

    enum Language {
        DANISH("dk", "Dansk (Danish)"),
        ENGLISH("en", "English (original)");

        private final String code;
        private final String label;

        Language(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String getCode() {
            return code;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
