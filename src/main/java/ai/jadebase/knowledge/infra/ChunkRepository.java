package ai.jadebase.knowledge.infra;

import ai.jadebase.knowledge.domain.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChunkRepository extends JpaRepository<Chunk, UUID> {
    List<Chunk> findByKnowledgeBaseId(UUID knowledgeBaseId);
    void deleteByDocumentId(UUID documentId);
}
