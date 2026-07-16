package ai.jadebase.knowledge.domain;

import ai.jadebase.knowledge.infra.ChunkRepository;
import ai.jadebase.knowledge.infra.ChunkTermRepository;
import ai.jadebase.rag.application.TermExtractor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChunkTermIndexService {

    private static final String EMPTY_SENTINEL = "__jadebase_empty__";

    private final ChunkRepository chunks;
    private final ChunkTermRepository terms;
    private final TermExtractor extractor;

    public ChunkTermIndexService(ChunkRepository chunks, ChunkTermRepository terms, TermExtractor extractor) {
        this.chunks = chunks;
        this.terms = terms;
        this.extractor = extractor;
    }

    public List<ChunkTerm> termsFor(Chunk chunk) {
        List<String> extracted = extractor.extract(chunk.getContent());
        Map<String, Integer> frequencies = new HashMap<>();
        extracted.forEach(term -> frequencies.merge(term, 1, Integer::sum));
        if (frequencies.isEmpty()) frequencies.put(EMPTY_SENTINEL, 1);
        int documentLength = Math.max(1, extracted.size());
        return frequencies.entrySet().stream()
                .map(entry -> new ChunkTerm(chunk.getId(), chunk.getDocumentId(), chunk.getKnowledgeBaseId(),
                        entry.getKey(), entry.getValue(), documentLength))
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int backfillBatch(int batchSize) {
        List<Chunk> missing = chunks.findWithoutTermIndex(PageRequest.of(0, batchSize));
        missing.forEach(chunk -> terms.saveAll(termsFor(chunk)));
        return missing.size();
    }
}
