package com.haramisi98.LingoScape;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Standalone smoke test: the classpath must contain the packaged plugin only. */
public final class ArtifactSmoke {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        require(TranslationManager.class.getResource("TranslationManager.class").getProtocol().equals("jar"),
                "Production classes must load from the artifact");
        try (Stream<Path> languages = Files.list(root.resolve("translations"))) {
            for (Path language : (Iterable<Path>) languages.filter(Files::isDirectory)::iterator) {
                TranslationManager manager = new TranslationManager(language.getFileName().toString());
                for (TranslationType type : TranslationType.values()) {
                    String resource = "/translations/" + manager.getLanguage() + "/"
                            + type.name().toLowerCase(Locale.ROOT) + ".json";
                    require(TranslationManager.class.getResource(resource) != null, "Missing " + resource);
                    require(TranslationManager.class.getResource(resource).getProtocol().equals("jar"), resource);
                    try (java.io.InputStream input = TranslationManager.class.getResourceAsStream(resource)) {
                        int size = new JsonParser().parse(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                                .getAsJsonObject().size();
                        require(manager.translations.get(type).size() == size, "Dictionary did not load: " + resource);
                    }
                    for (Map.Entry<String, String> entry : manager.translations.get(type).entrySet()) {
                        require(manager.translateTextType(type, entry.getKey()).equals(entry.getValue()), resource);
                    }
                }
            }
        }
        KnownTextIndex index = KnownTextIndex.load();
        require(KnownTextIndex.class.getResource("/translations/known-text.bin").getProtocol().equals("jar"),
                "Index must load from the artifact");
        for (TranslationType type : TranslationType.values()) {
            try (Stream<String> lines = Files.lines(root.resolve("data/english/"
                    + type.name().toLowerCase(Locale.ROOT) + ".jsonl"))) {
                String text = new JsonParser().parse(lines.findFirst().orElseThrow()).getAsJsonObject().get("text").getAsString();
                require(index.contains(type, text), "Known text not found: " + type);
            }
        }
        Path directory = Files.createTempDirectory("lingoscape-artifact-smoke-");
        try {
            MissingTextCollector collector = new MissingTextCollector(directory);
            TranslationManager manager = new TranslationManager("dk");
            manager.onMissing((type, text) -> collector.record("dk", type, text,
                    index.contains(type, text) ? "KNOWN_UNTRANSLATED" : "UNKNOWN_TEXT"));
            String missing = "Artifact smoke unknown text 6d9209";
            require(!index.contains(TranslationType.MENU, missing), "Fixture unexpectedly known");
            require(manager.translateTextType(TranslationType.MENU, missing).equals(missing), "Fallback failed");
            collector.flush();
            MissingTextCollector reloaded = new MissingTextCollector(directory);
            reloaded.load();
            require(reloaded.status().startsWith("1 candidates"), "Collection did not persist");
            require(Files.readString(reloaded.export()).contains("UNKNOWN_TEXT"), "Missing classification");
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
            }
        }
        System.out.println("Artifact smoke passed: JAR dictionaries, known-text lookup, and missing-text persistence");
    }
}
