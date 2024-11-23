package com.haramisi98.LingoScape.TranslationManager.Sanititization;

public interface SanitizationStrategy {
    /**
     * Sanitizes the input text by applying the specific strategy logic.
     * @param text The original text to be sanitized.
     * @return The sanitized text after applying this strategy.
     */
    String sanitize(String text);

    /**
     * Executes logic after sanitization is complete, allowing reapplication of modifications.
     * @param text The sanitized text.
     * @return The modified text with reinserted elements, if applicable.
     */
    default String onAfterSanitize(String text) {
        return text;
    }
}

