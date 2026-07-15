package ai.jadebase.rag.application;

import ai.jadebase.knowledge.domain.Chunk;
import ai.jadebase.knowledge.infra.ChunkRepository;
import ai.jadebase.rag.domain.EmbeddingClient;
import ai.jadebase.rag.domain.RetrievedChunk;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HybridRetriever {

    private static final Pattern LATIN_TOKEN = Pattern.compile("[a-z0-9_\\-]{2,}");

    private final ChunkRepository chunks;
    private final EmbeddingClient embeddingClient;
    private final VectorCodec vectorCodec;
    private final RetrievalProperties properties;

    public HybridRetriever(ChunkRepository chunks, EmbeddingClient embeddingClient,
                           VectorCodec vectorCodec, RetrievalProperties properties) {
        this.chunks = chunks;
        this.embeddingClient = embeddingClient;
        this.vectorCodec = vectorCodec;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<RetrievedChunk> retrieve(UUID knowledgeBaseId, String query) {
        double[] queryVector = embeddingClient.embed(query);
        Set<String> queryTerms = terms(query);
        List<RetrievedChunk> ranked = new ArrayList<>();
        for (Chunk chunk : chunks.findByKnowledgeBaseId(knowledgeBaseId)) {
            double vectorScore = Math.max(0, cosine(queryVector, vectorCodec.decode(chunk.getEmbedding())));
            double keywordScore = keywordScore(queryTerms, terms(chunk.getContent()));
            double score = properties.vectorWeight() * vectorScore + properties.keywordWeight() * keywordScore;
            ranked.add(new RetrievedChunk(chunk.getId(), chunk.getDocumentId(), chunk.getDocumentName(),
                    chunk.getChunkIndex(), chunk.getContent(), score));
        }
        return ranked.stream()
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .filter(item -> item.score() > 0.01)
                .limit(properties.topK())
                .toList();
    }

    double cosine(double[] left, double[] right) {
        if (left.length == 0 || left.length != right.length) return 0;
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) return 0;
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double keywordScore(Set<String> query, Set<String> content) {
        if (query.isEmpty()) return 0;
        long matches = query.stream().filter(content::contains).count();
        return (double) matches / query.size();
    }

    Set<String> terms(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        Set<String> result = new HashSet<>();
        Matcher matcher = LATIN_TOKEN.matcher(normalized);
        while (matcher.find()) result.add(matcher.group());

        String chinese = normalized.replaceAll("[^\\p{IsHan}]", "");
        int[] points = chinese.codePoints().toArray();
        for (int i = 0; i < points.length; i++) {
            result.add(new String(Character.toChars(points[i])));
            if (i + 1 < points.length) {
                result.add(new String(Character.toChars(points[i])) + new String(Character.toChars(points[i + 1])));
            }
        }
        return result;
    }
}
