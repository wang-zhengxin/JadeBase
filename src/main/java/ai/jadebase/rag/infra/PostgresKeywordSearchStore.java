package ai.jadebase.rag.infra;

import ai.jadebase.rag.application.TermExtractor;
import ai.jadebase.rag.domain.KeywordSearchStore;
import ai.jadebase.rag.domain.SearchCandidate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "jadebase.retrieval.search-store", havingValue = "postgres")
public class PostgresKeywordSearchStore implements KeywordSearchStore {

    private static final String SQL = """
            WITH corpus AS (
                SELECT COUNT(*)::double precision AS total,
                       COALESCE(AVG(lengths.document_length), 1)::double precision AS avg_length
                FROM (SELECT DISTINCT chunk_id, document_length FROM document_chunk_terms
                      WHERE knowledge_base_id = :knowledgeBaseId) lengths
            ), frequencies AS (
                SELECT term, COUNT(DISTINCT chunk_id)::double precision AS document_frequency
                FROM document_chunk_terms
                WHERE knowledge_base_id = :knowledgeBaseId AND term IN (:terms)
                GROUP BY term
            ), scores AS (
                SELECT ct.chunk_id,
                       SUM(LN(1 + (corpus.total - f.document_frequency + 0.5) / (f.document_frequency + 0.5))
                         * (ct.term_frequency * 2.2)
                         / (ct.term_frequency + 1.2 * (0.25 + 0.75 * ct.document_length / corpus.avg_length))) AS score
                FROM document_chunk_terms ct
                JOIN frequencies f ON f.term = ct.term
                CROSS JOIN corpus
                WHERE ct.knowledge_base_id = :knowledgeBaseId AND ct.term IN (:terms)
                GROUP BY ct.chunk_id
            )
            SELECT c.id, c.document_id, c.document_name, c.chunk_index, c.content, scores.score
            FROM scores JOIN document_chunks c ON c.id = scores.chunk_id
            ORDER BY scores.score DESC
            LIMIT :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TermExtractor termExtractor;

    public PostgresKeywordSearchStore(NamedParameterJdbcTemplate jdbc, TermExtractor termExtractor) {
        this.jdbc = jdbc;
        this.termExtractor = termExtractor;
    }

    @Override
    public List<SearchCandidate> search(UUID knowledgeBaseId, String query, int limit) {
        List<String> terms = new HashSet<>(termExtractor.extract(query)).stream().limit(64).toList();
        if (terms.isEmpty()) return List.of();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("knowledgeBaseId", knowledgeBaseId)
                .addValue("terms", terms)
                .addValue("limit", limit);
        return jdbc.query(SQL, parameters, (rs, row) -> new SearchCandidate(
                rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
                rs.getString("document_name"), rs.getInt("chunk_index"), rs.getString("content"),
                rs.getDouble("score")));
    }
}
