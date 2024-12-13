package com.haramisi98.LingoScape;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranslationManager {
    public final Map<TranslationType, Map<String, String>> translations = new EnumMap<>(TranslationType.class);
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

    public void loadTranslations(String languageCode) {
        String basePath = "/net/runelite/client/plugins/lingoScape/" + languageCode + "/";

        for (TranslationType type : TranslationType.values()) {
            String fileName = languageCode + "_" + type.name().toLowerCase() + ".csv";
            translations.put(type, loadTranslationFile(basePath + fileName));
        }
    }

    public Map<String, String> loadTranslationFile(String path) {
        Map<String, String> translationMap = new HashMap<>();

        try (InputStream input = getClass().getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(input)))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("~\\|~", -1);
                if (parts.length >= 2) {
                    translationMap.put(parts[0], parts[1]);
                }
            }
        } catch (IOException | NullPointerException e) {
            Logger.getLogger(TranslationManager.class.getName())
                    .warning("Failed to load resource file: " + path);
        }
        return translationMap;
    }

    public String translateTextType(TranslationType type, String text) {
        return translations.getOrDefault(type, Collections.emptyMap()).getOrDefault(text, text);
    }

    public String getTranslationFromUnfilteredText(TranslationType type, String text) {
        Matcher matcher = createMatcher(text);

        if (!matcher.matches()) {
            return text; // Return the original text if no match
        }

        String colorTag = Optional.ofNullable(matcher.group(1)).orElse("");
        String mainText = Optional.ofNullable(matcher.group(2)).orElse("");
        String suffix = Optional.ofNullable(matcher.group(3)).orElse("");
        boolean hasClosingTag = matcher.group(4) != null;

        List<String> numbers = extractNumbers(mainText);
        String processedText = replaceNumbersWithPlaceholders(mainText, numbers);
        processedText = preprocessText(type, processedText);
        String translated = translateTextType(type, processedText);
        String translatedWithNumbers = restoreNumbersInText(translated, numbers);

        // If no translation is found, return original.
        if(translated.equals(processedText))
        {
            return text;
        }

        return buildFinalText(colorTag, translatedWithNumbers, suffix, hasClosingTag);
    }

    private String preprocessText(TranslationType type, String text) {
        return (type == TranslationType.NPC || type == TranslationType.OBJECT)
                ? text.toUpperCase()
                : text;
    }

    private Matcher createMatcher(String text) {
        Pattern pattern = Pattern.compile(
                "(<col=[^>]+>)?(.*?)(\\s\\(Members\\))?(</col>)?",
                Pattern.CASE_INSENSITIVE
        );
        return pattern.matcher(text);
    }

    private List<String> extractNumbers(String text) {
        List<String> numbers = new ArrayList<>();
        Pattern numberPattern = Pattern.compile("\\d+");
        Matcher numberMatcher = numberPattern.matcher(text);

        while (numberMatcher.find()) {
            numbers.add(numberMatcher.group());
        }
        return numbers;
    }

    private String buildFinalText(String colorTag, String translatedText, String suffix, boolean hasClosingTag) {
        return colorTag + translatedText + suffix + (hasClosingTag ? "</col>" : "");
    }


    public String replaceNumbersWithPlaceholders(String text, List<String> numbers) {
        Matcher numberMatcher = Pattern.compile("\\d+").matcher(text);
        StringBuffer buffer = new StringBuffer();
        int placeholderIndex = 0;

        while (numberMatcher.find()) {
            numbers.add(numberMatcher.group());
            numberMatcher.appendReplacement(buffer, "{" + (placeholderIndex++) + "}");
        }
        numberMatcher.appendTail(buffer);
        return buffer.toString();
    }

    public String restoreNumbersInText(String text, List<String> numbers) {
        Matcher placeholderMatcher = Pattern.compile("\\{(\\d+)}").matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (placeholderMatcher.find()) {
            int index = Integer.parseInt(placeholderMatcher.group(1));
            placeholderMatcher.appendReplacement(buffer, numbers.get(index));
        }
        placeholderMatcher.appendTail(buffer);
        return buffer.toString();
    }
}