package com.haramisi98.LingoScape;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.util.function.BiConsumer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TranslationManager {
    public final Map<TranslationType, Map<String, String>> translations = new EnumMap<>(TranslationType.class);
    private String currentLanguage;
    private BiConsumer<TranslationType, String> missing = (type, text) -> {};
    private static final Pattern NUMBERS = Pattern.compile("\\d+");
    private static final Pattern PLACEHOLDERS = Pattern.compile("\\{(\\d+)}");
    private static final Pattern FORMATTED = Pattern.compile("(<col=[^>]+>)?(.*?)(\\s\\(Members\\))?(</col>)?", Pattern.CASE_INSENSITIVE);
    private final Map<TranslationType, Set<String>> normalizedKeys = new EnumMap<>(TranslationType.class);
    private final Map<TranslationType, Set<String>> translatedValues = new EnumMap<>(TranslationType.class);

    public void onMissing(BiConsumer<TranslationType, String> listener) { missing = listener; }
    public String getLanguage() { return currentLanguage; }
    public boolean resemblesTranslatedKey(TranslationType type, String text) {
        return normalizedKeys.getOrDefault(type, Collections.emptySet()).contains(KnownTextIndex.normalize(text));
    }

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
        normalizedKeys.clear();
        translatedValues.clear();
        if ("en".equals(languageCode)) {
            translations.clear(); // English is the original game text; no dictionary is required.
            return;
        }
        String basePath = "/translations/" + languageCode + "/";

        for (TranslationType type : TranslationType.values()) {
            String fileName = type.name().toLowerCase(Locale.ROOT) + ".json";
            Map<String, String> dictionary = loadTranslationFile(basePath + fileName);
            translations.put(type, dictionary);
            Set<String> keys = new HashSet<>();
            dictionary.keySet().forEach(key -> keys.add(KnownTextIndex.normalize(key)));
            normalizedKeys.put(type, keys);
            translatedValues.put(type, new HashSet<>(dictionary.values()));
        }
    }

    public Map<String, String> loadTranslationFile(String path) {
        Map<String, String> translationMap = new HashMap<>();

        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                return translationMap;
            }
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return readDictionary(reader);
            }
        } catch (IOException | com.google.gson.JsonParseException e) {
            Logger.getLogger(TranslationManager.class.getName())
                    .warning("Failed to load translation file: " + path);
        }
        return translationMap;
    }

    static Map<String, String> readDictionary(Reader reader) throws IOException {
        Map<String, String> result = new HashMap<>();
        JsonReader json = new JsonReader(reader);
        json.beginObject();
        while (json.hasNext()) {
            String key = json.nextName();
            if (json.peek() == JsonToken.NULL) { json.nextNull(); continue; }
            String value = json.nextString();
            if (!value.isBlank() && !"translationMissing".equals(value.trim())) result.put(key, value);
        }
        json.endObject();
        return result;
    }

    public String translateTextType(TranslationType type, String text) {
        String translated = translations.getOrDefault(type, Collections.emptyMap()).get(text);
        if (translated == null || translated.isBlank() || "translationMissing".equals(translated.trim())) {
            if (text != null && !"en".equals(currentLanguage) &&
                    !translatedValues.getOrDefault(type, Collections.emptySet()).contains(text)) missing.accept(type, text);
            return text;
        }
        return translated;
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

        List<String> numbers = new ArrayList<>();
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
                ? text.toUpperCase(Locale.ROOT)
                : text;
    }

    private Matcher createMatcher(String text) {
        return FORMATTED.matcher(text);
    }

    private String buildFinalText(String colorTag, String translatedText, String suffix, boolean hasClosingTag) {
        return colorTag + translatedText + suffix + (hasClosingTag ? "</col>" : "");
    }


    public String replaceNumbersWithPlaceholders(String text, List<String> numbers) {
        Matcher numberMatcher = NUMBERS.matcher(text);
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
        Matcher placeholderMatcher = PLACEHOLDERS.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (placeholderMatcher.find()) {
            int index = Integer.parseInt(placeholderMatcher.group(1));
            placeholderMatcher.appendReplacement(buffer, numbers.get(index));
        }
        placeholderMatcher.appendTail(buffer);
        return buffer.toString();
    }
}
