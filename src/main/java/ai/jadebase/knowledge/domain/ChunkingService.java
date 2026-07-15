package ai.jadebase.knowledge.domain;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChunkingService {

    static final int TARGET_SIZE = 700;
    static final int OVERLAP = 100;

    public List<String> split(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int idealEnd = Math.min(start + TARGET_SIZE, normalized.length());
            int end = findBoundary(normalized, start, idealEnd);
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end >= normalized.length()) break;
            int next = Math.max(start + 1, end - OVERLAP);
            start = skipWhitespace(normalized, next);
        }
        return chunks;
    }

    private int findBoundary(String text, int start, int idealEnd) {
        if (idealEnd == text.length()) return idealEnd;
        int minimum = Math.min(start + TARGET_SIZE / 2, idealEnd);
        for (int i = idealEnd; i > minimum; i--) {
            char value = text.charAt(i - 1);
            if (value == '\n' || value == '。' || value == '！' || value == '？'
                    || value == '.' || value == '!' || value == '?') return i;
        }
        return idealEnd;
    }

    private int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        return index;
    }
}
