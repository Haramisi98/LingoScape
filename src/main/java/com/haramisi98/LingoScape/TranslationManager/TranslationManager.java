package com.haramisi98.LingoScape.TranslationManager;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranslationManager {
    private Map<String, String> itemsMap;
    private Map<String, String> npcMap;
    private Map<String, String> objectMap;
    private Map<String, String> dialogueMap;
    private Map<String, String> menuElementsMap;
    private String currentLanguage;

    public TranslationManager(String initialLanguage) {
        setLanguage(initialLanguage);
    }

    public void setLanguage(String languageCode) {
        if (!languageCode.equals(currentLanguage)) {
            currentLanguage = languageCode;
            loadTranslations(languageCode);
        }
    }

    private void loadTranslations(String languageCode) {
        String basePath = "/net/runelite/client/plugins/lingoScape/" + languageCode + "/";
        itemsMap = loadTranslationFile(basePath + languageCode + "_items.csv");
        npcMap = loadTranslationFile(basePath + languageCode + "_npc.csv");
        objectMap = loadTranslationFile(basePath + languageCode + "_object.csv");
        dialogueMap = loadTranslationFile(basePath + languageCode + "_dialogue.csv");
        menuElementsMap = loadTranslationFile(basePath + languageCode + "_menu.csv");
    }

    private Map<String, String> loadTranslationFile(String path) {
        Map<String, String> translations = new HashMap<>();
        try (InputStream input = getClass().getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            if (input == null) {
                Logger.getLogger(TranslationManager.class.getName()).warning("Resource file not found: " + path);
                return translations; // Early return on file not found
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("~\\|~", -1);
                if (parts.length >= 2) {
                    translations.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            Logger.getLogger(TranslationManager.class.getName()).severe("Failed to load file: " + e.getMessage());
        }
        return translations;
    }

    public String translate(TranslationType type, String text) {
        // Regex to identify parts to translate or preserve, including spaces
        Pattern pattern = Pattern.compile("(<[^>]+>|\\([^\\)]+\\)|\\d+|\\b[\\w\\s]+\\b)");
        Matcher matcher = pattern.matcher(text);

        return buildTranslatedText(matcher, type);
    }

    private String buildTranslatedText(Matcher matcher, TranslationType type) {
        StringBuilder translatedText = new StringBuilder();

        while (matcher.find()) {
            String match = matcher.group().trim();
            if (match.isEmpty()) {
                continue; // Skip empty matches to avoid unnecessary spaces
            }

            if (isTranslatable(match)) {
                translateAndAppend(match, translatedText, type);
            } else {
                translatedText.append(match); // Append tags and numbers unchanged
            }
            translatedText.append(" "); // Maintain spaces after each segment
        }

        return translatedText.toString().trim(); // Return the cleaned-up translated string
    }

    private boolean isTranslatable(String text) {
        return !text.startsWith("<") && !text.startsWith("(") && !text.matches("\\d+");
    }

    private void translateAndAppend(String text, StringBuilder translatedText, TranslationType type) {
        Map<String, String> translationMap = getMapByType(type);
        if (translationMap.containsKey(text)) {
            translatedText.append(translationMap.get(text));
        } else {
            // Handle untranslatable text or partial translations
            translatedText.append(text);
        }
    }

    private Map<String, String> getMapByType(TranslationType type) {
        switch (type) {
            case ITEM: return itemsMap;
            case NPC: return npcMap;
            case OBJECT: return objectMap;
            case DIALOGUE: return dialogueMap;
            case MENU: return menuElementsMap;
            default: return new HashMap<>();
        }
    }
}

