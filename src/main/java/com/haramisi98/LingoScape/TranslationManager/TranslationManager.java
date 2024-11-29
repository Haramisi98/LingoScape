package com.haramisi98.LingoScape.TranslationManager;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    public String generalTranslate(TranslationType type, String text) {
        // Return the translated text if it exists; otherwise, return the original text
        return getMapByType(type).getOrDefault(text, text);
    }

    public String translateItemText(TranslationType type, String text) {
        // Regex to extract <col=xxx>, the main text part, and optional "(Members)"
        Pattern pattern = Pattern.compile("(<col=[^>]+>)(.*?)(\\s\\(Members\\))?(</col>)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        if (matcher.matches()) {
            String colorTag = matcher.group(1);
            String mainText = matcher.group(2);
            String suffix = matcher.group(3);

            // Replace numbers with placeholders and store them in order
            List<String> numbers = new ArrayList<>();
            Pattern numberPattern = Pattern.compile("\\d+");
            Matcher numberMatcher = numberPattern.matcher(mainText);

            StringBuffer processedTextBuffer = new StringBuffer();
            int placeholderIndex = 0;
            while (numberMatcher.find()) {
                numbers.add(numberMatcher.group()); // Store the original number
                numberMatcher.appendReplacement(processedTextBuffer, "{" + (placeholderIndex++) + "}");
            }
            numberMatcher.appendTail(processedTextBuffer);
            String processedText = processedTextBuffer.toString();

            // Translate the processed text
            String translated = generalTranslate(type, processedText);

            // Replace placeholders back with original numbers
            Pattern placeholderPattern = Pattern.compile("\\{(\\d+)}");
            Matcher placeholderMatcher = placeholderPattern.matcher(translated);

            StringBuffer translatedWithNumbersBuffer = new StringBuffer();
            while (placeholderMatcher.find()) {
                int index = Integer.parseInt(placeholderMatcher.group(1));
                placeholderMatcher.appendReplacement(translatedWithNumbersBuffer, numbers.get(index));
            }
            placeholderMatcher.appendTail(translatedWithNumbersBuffer);
            String translatedWithNumbers = translatedWithNumbersBuffer.toString();

            // Reconstruct the final translated text
            return colorTag + translatedWithNumbers + (suffix != null ? suffix : "") + "</col>";
        }

        // If the text doesn't match the expected format, return as is
        return text;
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

