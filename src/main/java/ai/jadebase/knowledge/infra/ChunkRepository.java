package ai.jadebase.knowledge.infra;

import ai.jadebase.knowledge.domain.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ChunkRepository extends JpaRepository<Chunk, UUID> {
    List<Chunk> findByKnowledgeBaseId(UUID knowledgeBaseId);
    void deleteByDocumentId(UUID documentId);

    @Query("select chunk from Chunk chunk where not exists "
            + "(select term.id from ChunkTerm term where term.chunkId = chunk.id)")
    List<Chunk> findWithoutTermIndex(Pageable pageable);
}
