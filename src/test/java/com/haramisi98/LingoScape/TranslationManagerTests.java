package com.haramisi98.LingoScape;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TranslationManagerTests {

    private TranslationManager translationManager;

    @BeforeEach
    void setUp() {
        translationManager = new TranslationManager("en");
    }

    @Test
    void testSetLanguageLoadsTranslations() {
        TranslationManager spyManager = Mockito.spy(translationManager);
        doNothing().when(spyManager).loadTranslations("fr");

        spyManager.setLanguage("fr");

        verify(spyManager).loadTranslations("fr");
    }

    @Test
    void testTranslateTextTypeExistingTranslation() {
        Map<String, String> mockTranslations = new HashMap<>();
        mockTranslations.put("Hello", "Bonjour");

        translationManager.translations.put(TranslationType.MENU, mockTranslations);

        String result = translationManager.translateTextType(TranslationType.MENU, "Hello");
        assertEquals("Bonjour", result);
    }

    @Test
    void testTranslateTextTypeNonExistingTranslation() {
        String result = translationManager.translateTextType(TranslationType.MENU, "NonExistentText");
        assertEquals("NonExistentText", result);
    }

    @Test
    void testGetTranslationFromUnfilteredTextWithNumbers() {
        Map<String, String> mockTranslations = new HashMap<>();
        mockTranslations.put("Item {0}", "Article {0}");

        translationManager.translations.put(TranslationType.ITEM, mockTranslations);

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.ITEM, "Item 123");
        assertEquals("Article 123", result);
    }

    @Test
    void testGetTranslationFromUnfilteredTextWithColorTags() {
        Map<String, String> mockTranslations = new HashMap<>();
        mockTranslations.put("Sword", "Épée");

        translationManager.translations.put(TranslationType.ITEM, mockTranslations);

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.ITEM, "<col=ffff00>Sword</col>");
        assertEquals("<col=ffff00>Épée</col>", result);
    }

    @Test
    void testRestoreNumbersInText() {
        String translatedText = "Item {0} found at {1}";
        String[] numbers = {"100", "42"};

        String result = translationManager.restoreNumbersInText(translatedText, java.util.List.of(numbers));

        assertEquals("Item 100 found at 42", result);
    }

    @Test
    void testReplaceNumbersWithPlaceholders() {
        String text = "Item 100 found at 42";
        java.util.List<String> numbers = new java.util.ArrayList<>();

        String result = translationManager.replaceNumbersWithPlaceholders(text, numbers);

        assertEquals("Item {0} found at {1}", result);
        assertEquals("100", numbers.get(0));
        assertEquals("42", numbers.get(1));
    }

    @Test
    void testBasicTranslation() {
        TranslationManager translationManager = new TranslationManager("en");
        Map<String, String> translations = new HashMap<>();
        translations.put("Hello", "Bonjour");
        translationManager.translations.put(TranslationType.MENU, translations);

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.MENU, "Hello");
        assertEquals("Bonjour", result, "Should translate 'Hello' to 'Bonjour'");
    }

    @Test
    void testTranslationWithColorTag() {
        TranslationManager translationManager = new TranslationManager("en");
        Map<String, String> translations = new HashMap<>();
        translations.put("Sword", "Épée");
        translationManager.translations.put(TranslationType.MENU, translations);

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.MENU, "<col=ffff00>Sword</col>");
        assertEquals("<col=ffff00>Épée</col>", result, "Should translate 'Sword' and keep the color tags intact");
    }

    @Test
    void testTranslationWithNumbers() {
        TranslationManager translationManager = new TranslationManager("en");
        Map<String, String> translations = new HashMap<>();
        translations.put("Item {0}", "Article {0}");
        translationManager.translations.put(TranslationType.MENU, translations);

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.MENU, "Item 123");
        assertEquals("Article 123", result, "Should translate 'Item {0}' and replace the placeholder with '123'");
    }

    @Test
    void testTranslationWithMembersSuffix() {
        TranslationManager translationManager = new TranslationManager("en");
        Map<String, String> translations = new HashMap<>();
        translations.put("Hello", "Bonjour");
        translationManager.translations.put(TranslationType.MENU, translations);

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.MENU, "Hello (Members)");
        assertEquals("Bonjour (Members)", result, "Should translate 'Hello' and retain the '(Members)' suffix");
    }

    @Test
    void testTranslationWithColorTagAndMembersSuffix() {
        TranslationManager translationManager = new TranslationManager("en");
        Map<String, String> translations = new HashMap<>();
        translations.put("Hello", "Bonjour");
        translationManager.translations.put(TranslationType.MENU, translations);

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.MENU, "<col=00ff00>Hello (Members)</col>");
        assertEquals("<col=00ff00>Bonjour (Members)</col>", result, "Should translate 'Hello', keep color tags, and retain '(Members)'");
    }

    @Test
    void testTranslationWithoutMatch() {
        TranslationManager translationManager = new TranslationManager("en");

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.MENU, "NonExistentText");
        assertEquals("NonExistentText", result, "Should return the original text if no translation is found");
    }

    @Test
    void testTranslationWithNumbersAndColorTag() {
        TranslationManager translationManager = new TranslationManager("en");
        Map<String, String> translations = new HashMap<>();
        translations.put("Item {0}", "Article {0}");
        translationManager.translations.put(TranslationType.MENU, translations);

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.MENU, "<col=ff0000>Item 456</col>");
        assertEquals("<col=ff0000>Article 456</col>", result, "Should translate 'Item {0}' and replace placeholder with '456', preserving color tag");
    }

    @Test
    void testTranslationWithOnlyColorTag() {
        TranslationManager translationManager = new TranslationManager("en");
        Map<String, String> translations = new HashMap<>();
        translations.put("Sword", "Épée");
        translationManager.translations.put(TranslationType.MENU, translations);

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.MENU, "<col=123456>Sword</col>");
        assertEquals("<col=123456>Épée</col>", result, "Should translate 'Sword' and retain color tag");
    }

    @Test
    void testTranslationWithOnlyMembersSuffix() {
        TranslationManager translationManager = new TranslationManager("en");
        Map<String, String> translations = new HashMap<>();
        translations.put("Sword", "Épée");
        translationManager.translations.put(TranslationType.MENU, translations);

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.MENU, "Sword (Members)");
        assertEquals("Épée (Members)", result, "Should translate 'Sword' and retain '(Members)' suffix");
    }

    @Test
    void testTranslationWithMultipleNumbers() {
        TranslationManager translationManager = new TranslationManager("en");
        Map<String, String> translations = new HashMap<>();
        translations.put("Item {0} and {1}", "Article {0} et {1}");
        translationManager.translations.put(TranslationType.MENU, translations);

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.MENU, "Item 123 and 456");
        assertEquals("Article 123 et 456", result, "Should translate 'Item {0} and {1}' and retain all numbers in the text");
    }

    @Test
    void testTranslationWithUnmatchedPattern() {
        TranslationManager translationManager = new TranslationManager("en");

        String result = translationManager.getTranslationFromUnfilteredText(TranslationType.MENU, "<invalid>123</invalid>");
        assertEquals("<invalid>123</invalid>", result, "Should return the original text if the pattern does not match");
    }
}
