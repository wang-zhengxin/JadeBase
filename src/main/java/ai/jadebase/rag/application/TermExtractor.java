package ai.jadebase.rag.application;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TermExtractor {

    private static final Pattern LATIN_TOKEN = Pattern.compile("[a-z0-9_\\-]{2,}");

    public List<String> extract(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        Matcher matcher = LATIN_TOKEN.matcher(normalized);
        while (matcher.find()) result.add(matcher.group());

        String chinese = normalized.replaceAll("[^\\p{IsHan}]", "");
        int[] points = chinese.codePoints().toArray();
        for (int i = 0; i < points.length; i++) {
            result.add(codePoint(points[i]));
            if (i + 1 < points.length) result.add(codePoint(points[i]) + codePoint(points[i + 1]));
        }
        return result;
    }

    private String codePoint(int value) {
        return new String(Character.toChars(value));
    }
}
