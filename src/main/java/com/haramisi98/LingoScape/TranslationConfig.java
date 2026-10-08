package com.haramisi98.LingoScape;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
