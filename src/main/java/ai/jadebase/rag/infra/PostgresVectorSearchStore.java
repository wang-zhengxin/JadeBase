package ai.jadebase.rag.infra;

import ai.jadebase.rag.application.VectorCodec;
import ai.jadebase.rag.domain.SearchCandidate;
import ai.jadebase.rag.domain.VectorSearchStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "jadebase.retrieval.search-store", havingValue = "postgres")
public class PostgresVectorSearchStore implements VectorSearchStore {

    private static final String SQL = """
            WITH query_vector AS (SELECT CAST(? AS vector(384)) AS value)
            SELECT c.id, c.document_id, c.document_name, c.chunk_index, c.content,
                   1 - (c.embedding_vector <=> q.value) AS score
            FROM document_chunks c CROSS JOIN query_vector q
            WHERE c.knowledge_base_id = ? AND c.embedding_vector IS NOT NULL
            ORDER BY c.embedding_vector <=> q.value
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final VectorCodec vectors;

    public PostgresVectorSearchStore(JdbcTemplate jdbc, VectorCodec vectors) {
        this.jdbc = jdbc;
        this.vectors = vectors;
    }

    @Override
    public List<SearchCandidate> search(UUID knowledgeBaseId, double[] queryVector, int limit) {
        String literal = "[" + vectors.encode(queryVector) + "]";
        return jdbc.query(SQL, (rs, row) -> new SearchCandidate(
                rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
                rs.getString("document_name"), rs.getInt("chunk_index"), rs.getString("content"),
                rs.getDouble("score")), literal, knowledgeBaseId, limit);
    }
}
