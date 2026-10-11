package com.haramisi98.LingoScape;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Sorted 128-bit SHA-256 prefixes: compact, approximate membership only, never translation lookup. */
public final class KnownTextIndex {
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private final byte[] hashes;

    public KnownTextIndex(byte[] hashes) {
        if (hashes.length % 16 != 0) throw new IllegalArgumentException("Invalid index length");
        this.hashes = hashes;
    }

    public static String normalize(String text) {
        return SPACE.matcher(TAGS.matcher(text.replace("<br>", " ")).replaceAll(""))
                .replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }

    public static byte[] hash(TranslationType type, String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    (type.name().toLowerCase(Locale.ROOT) + "\n" + normalize(text)).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static KnownTextIndex load() throws IOException {
        try (InputStream input = KnownTextIndex.class.getResourceAsStream("/translations/known-text.bin")) {
            if (input == null) throw new IOException("Missing known-text index");
            return new KnownTextIndex(input.readAllBytes());
        }
    }

    public boolean contains(TranslationType type, String text) {
        byte[] target = hash(type, text);
        int low = 0, high = hashes.length / 16 - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int comparison = 0;
            for (int i = 0; i < 16 && comparison == 0; i++)
                comparison = Integer.compare(hashes[middle * 16 + i] & 255, target[i] & 255);
            if (comparison == 0) return true;
            if (comparison < 0) low = middle + 1; else high = middle - 1;
        }
        return false;
    }
}
