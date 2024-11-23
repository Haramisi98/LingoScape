package com.haramisi98.LingoScape.TranslationManager.Sanititization;

import java.util.ArrayList;
import java.util.List;

public class SanitizationStrategyManager {
    private final List<SanitizationStrategy> strategies;

    public SanitizationStrategyManager() {
        this.strategies = new ArrayList<>();
    }

    public void addStrategy(SanitizationStrategy strategy) {
        strategies.add(strategy);
    }

    /**
     * Applies all sanitization strategies in sequence and handles reapplication of modifications.
     * @param text The original text.
     * @return The fully sanitized and modified text.
     */
    public String applySanitization(String text) {
        // Step 1: Apply all sanitization strategies
        String sanitizedText = text;
        for (SanitizationStrategy strategy : strategies) {
            sanitizedText = strategy.sanitize(sanitizedText);
        }

        // Step 2: Reapply modifications using onAfterSanitize
        for (SanitizationStrategy strategy : strategies) {
            sanitizedText = strategy.onAfterSanitize(sanitizedText);
        }

        return sanitizedText;
    }
}


