package com.haramisi98.LingoScape.TranslationManager.Sanititization;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// This class is used to handle dynamic numbers in the UI, the resource texts ( not translated and translated )
// should never hold numbers! Hopefully I have made test for that - If not call me something mean, I deserve it!
public class NumberSanitizer {
    private List<String> numbers; // To store extracted numbers
    public boolean hasNumbers; // Flag to indicate if there are numbers stored

    public NumberSanitizer() {
        this.numbers = new ArrayList<>();
        this.hasNumbers = false;
    }

    /**
     * Replaces all numbers in the provided text with placeholders like {0}, {1}, etc.,
     * and stores these numbers for later reinsertion. Updates the numberFlag.
     * @param text The original text from which numbers are to be sanitized.
     * @return The sanitized text with numbers replaced by placeholders.
     */
    public String sanitizeNumbers(String text) {
        numbers.clear();
        Matcher matcher = Pattern.compile("\\d+").matcher(text);
        StringBuffer buffer = new StringBuffer();
        int index = 0;

        while (matcher.find()) {
            numbers.add(matcher.group()); // Store the number
            matcher.appendReplacement(buffer, "{" + index++ + "}");
        }
        matcher.appendTail(buffer);

        // Update the flag based on the presence of numbers
        hasNumbers = !numbers.isEmpty();

        return buffer.toString();
    }

    /**
     * Reinserts the previously extracted numbers back into the text where placeholders are.
     * @param text The text with placeholders to be replaced with original numbers.
     * @return The text with all placeholders replaced by their corresponding numbers.
     */
    public String reinsertNumbers(String text) {
        if (!hasNumbers) { // Early exit for performance.
            return text;
        }

        Matcher matcher = Pattern.compile("\\{\\d+\\}").matcher(text);
        StringBuffer buffer = new StringBuffer();

        try {
            while (matcher.find()) {
                String placeholder = matcher.group();
                int index = Integer.parseInt(placeholder.replaceAll("[\\{\\}]", ""));
                if (index < numbers.size()) {
                    matcher.appendReplacement(buffer, numbers.get(index));
                } else {
                    // If index is out of bounds, return the original text
                    return text;
                }
            }
            matcher.appendTail(buffer);
        } catch (Exception e) {
            // In case of any other unexpected error, return the original text
            return text;
        }

        hasNumbers = false; // Reset flag after successful insertion
        return buffer.toString();
    }
}

