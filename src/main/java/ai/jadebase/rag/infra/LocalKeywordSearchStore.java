package ai.jadebase.rag.infra;

import ai.jadebase.knowledge.domain.Chunk;
import ai.jadebase.knowledge.infra.ChunkRepository;
import ai.jadebase.rag.application.TermExtractor;
import ai.jadebase.rag.domain.KeywordSearchStore;
import ai.jadebase.rag.domain.SearchCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "jadebase.retrieval.search-store", havingValue = "local", matchIfMissing = true)
public class LocalKeywordSearchStore implements KeywordSearchStore {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final ChunkRepository chunks;
    private final TermExtractor terms;

    public LocalKeywordSearchStore(ChunkRepository chunks, TermExtractor terms) {
        this.chunks = chunks;
        this.terms = terms;
    }

    @Override
    public List<SearchCandidate> search(UUID knowledgeBaseId, String query, int limit) {
        List<ChunkDocument> documents = chunks.findByKnowledgeBaseId(knowledgeBaseId).stream()
                .map(chunk -> new ChunkDocument(chunk, frequencies(terms.extract(chunk.getContent()))))
                .toList();
        if (documents.isEmpty()) return List.of();
        Set<String> queryTerms = new HashSet<>(terms.extract(query));
        double averageLength = documents.stream().mapToInt(ChunkDocument::length).average().orElse(1);
        Map<String, Long> documentFrequency = new HashMap<>();
        for (String term : queryTerms) {
            documentFrequency.put(term, documents.stream().filter(doc -> doc.frequencies().containsKey(term)).count());
        }
        return documents.stream()
                .map(document -> candidate(document, queryTerms, documentFrequency, averageLength, documents.size()))
                .filter(candidate -> candidate.score() > 0)
                .sorted((left, right) -> Double.compare(right.score(), left.score()))
                .limit(limit)
                .toList();
    }

    private SearchCandidate candidate(ChunkDocument document, Set<String> queryTerms,
                                      Map<String, Long> documentFrequency, double averageLength, int total) {
        double score = 0;
        for (String term : queryTerms) {
            int frequency = document.frequencies().getOrDefault(term, 0);
            if (frequency == 0) continue;
            long df = documentFrequency.getOrDefault(term, 0L);
            double idf = Math.log(1 + (total - df + 0.5) / (df + 0.5));
            double denominator = frequency + K1 * (1 - B + B * document.length() / averageLength);
            score += idf * frequency * (K1 + 1) / denominator;
        }
        Chunk chunk = document.chunk();
        return new SearchCandidate(chunk.getId(), chunk.getDocumentId(), chunk.getDocumentName(),
                chunk.getChunkIndex(), chunk.getContent(), score);
    }

    private Map<String, Integer> frequencies(List<String> values) {
        Map<String, Integer> result = new HashMap<>();
        values.forEach(value -> result.merge(value, 1, Integer::sum));
        return result;
    }

    private record ChunkDocument(Chunk chunk, Map<String, Integer> frequencies) {
        int length() { return Math.max(1, frequencies.values().stream().mapToInt(Integer::intValue).sum()); }
    }
}
