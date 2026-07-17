package ai.jadebase.knowledge.domain;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChunkingService {

    static final int TARGET_SIZE = 700;
    static final int OVERLAP = 100;
    private final IndexSettingsService settings;

    public ChunkingService() {
        this.settings = null;
    }

    @Autowired
    public ChunkingService(IndexSettingsService settings) {
        this.settings = settings;
    }

    public List<String> split(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        int targetSize = settings == null ? TARGET_SIZE : settings.get().getChunkSize();
        int overlap = settings == null ? OVERLAP : settings.get().getChunkOverlap();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int idealEnd = Math.min(start + targetSize, normalized.length());
            int end = findBoundary(normalized, start, idealEnd, targetSize);
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end >= normalized.length()) break;
            int next = Math.max(start + 1, end - overlap);
            start = skipWhitespace(normalized, next);
        }
        return chunks;
    }

    private int findBoundary(String text, int start, int idealEnd, int targetSize) {
        if (idealEnd == text.length()) return idealEnd;
        int minimum = Math.min(start + targetSize / 2, idealEnd);
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
