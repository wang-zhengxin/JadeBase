package ai.jadebase.evaluation;

import ai.jadebase.knowledge.infra.KnowledgeBaseRepository;
import ai.jadebase.rag.application.HybridRetriever;
import ai.jadebase.rag.domain.RetrievedChunk;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class EvaluationService {

    private final KnowledgeBaseRepository knowledgeBases;
    private final HybridRetriever retriever;
    private final Timer evaluationTimer;

    public EvaluationService(KnowledgeBaseRepository knowledgeBases, HybridRetriever retriever,
                             MeterRegistry meters) {
        this.knowledgeBases = knowledgeBases;
        this.retriever = retriever;
        this.evaluationTimer = meters.timer("jadebase.evaluation.duration");
    }

    public EvaluationReport evaluate(UUID knowledgeBaseId, int topK, List<EvaluationCase> cases) {
        if (!knowledgeBases.existsById(knowledgeBaseId)) throw new EntityNotFoundException("知识库不存在");
        if (cases == null || cases.isEmpty()) throw new IllegalArgumentException("评测集不能为空");
        return evaluationTimer.record(() -> run(knowledgeBaseId, Math.min(12, Math.max(1, topK)), cases));
    }

    private EvaluationReport run(UUID knowledgeBaseId, int topK, List<EvaluationCase> cases) {
        List<CaseResult> results = cases.stream().map(testCase -> evaluateCase(knowledgeBaseId, topK, testCase)).toList();
        double recall = results.stream().mapToDouble(result -> result.hit() ? 1 : 0).average().orElse(0);
        double mrr = results.stream().mapToDouble(CaseResult::reciprocalRank).average().orElse(0);
        double termCoverage = results.stream().mapToDouble(CaseResult::expectedTermCoverage).average().orElse(0);
        double averageLatency = results.stream().mapToLong(CaseResult::elapsedMillis).average().orElse(0);
        return new EvaluationReport(cases.size(), round(recall), round(mrr), round(termCoverage),
                Math.round(averageLatency), results);
    }

    private CaseResult evaluateCase(UUID knowledgeBaseId, int topK, EvaluationCase testCase) {
        if (testCase.question() == null || testCase.question().isBlank()) {
            throw new IllegalArgumentException("评测问题不能为空");
        }
        HybridRetriever.RetrievalResult result = retriever.retrieveWithDiagnostics(knowledgeBaseId,
                testCase.question().trim(), topK);
        Set<String> expectedDocuments = normalize(testCase.expectedDocuments());
        int rank = 0;
        for (int index = 0; index < result.chunks().size(); index++) {
            if (expectedDocuments.contains(result.chunks().get(index).documentName().toLowerCase(Locale.ROOT))) {
                rank = index + 1;
                break;
            }
        }
        String context = result.chunks().stream().map(RetrievedChunk::content)
                .reduce("", (left, right) -> left + "\n" + right).toLowerCase(Locale.ROOT);
        List<String> expectedTerms = testCase.expectedTerms() == null ? List.of() : testCase.expectedTerms();
        long matchedTerms = expectedTerms.stream().filter(term -> term != null && !term.isBlank())
                .filter(term -> context.contains(term.toLowerCase(Locale.ROOT))).count();
        double coverage = expectedTerms.isEmpty() ? 1 : (double) matchedTerms / expectedTerms.size();
        return new CaseResult(testCase.question(), rank > 0, rank == 0 ? 0 : round(1.0 / rank),
                round(coverage), result.diagnostics().elapsedMillis(),
                result.chunks().stream().map(RetrievedChunk::documentName).toList());
    }

    private Set<String> normalize(List<String> values) {
        if (values == null) return Set.of();
        Set<String> result = new HashSet<>();
        values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT)).forEach(result::add);
        return result;
    }

    private double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    public record EvaluationCase(String question, List<String> expectedDocuments, List<String> expectedTerms) { }
    public record CaseResult(String question, boolean hit, double reciprocalRank, double expectedTermCoverage,
                             long elapsedMillis, List<String> retrievedDocuments) { }
    public record EvaluationReport(int caseCount, double recallAtK, double mrr,
                                   double expectedTermCoverage, long averageLatencyMillis,
                                   List<CaseResult> cases) { }
}
