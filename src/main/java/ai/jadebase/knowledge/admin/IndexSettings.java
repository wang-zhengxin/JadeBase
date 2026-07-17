package ai.jadebase.knowledge.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "index_settings")
public class IndexSettings {

    @Id
    private Long id;
    @Column(name = "chunk_size", nullable = false)
    private int chunkSize;
    @Column(name = "chunk_overlap", nullable = false)
    private int chunkOverlap;
    @Column(name = "candidate_k", nullable = false)
    private int candidateK;
    @Column(name = "rrf_k", nullable = false)
    private int rrfK;
    @Column(name = "rerank_enabled", nullable = false)
    private boolean rerankEnabled;
    @Column(name = "query_rewrite_enabled", nullable = false)
    private boolean queryRewriteEnabled;
    @Column(name = "reindex_required", nullable = false)
    private boolean reindexRequired;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IndexSettings() { }

    public IndexSettings(long id, int candidateK, int rrfK, boolean rerankEnabled, boolean queryRewriteEnabled) {
        this.id = id;
        this.chunkSize = 700;
        this.chunkOverlap = 100;
        this.candidateK = candidateK;
        this.rrfK = rrfK;
        this.rerankEnabled = rerankEnabled;
        this.queryRewriteEnabled = queryRewriteEnabled;
        this.reindexRequired = false;
        this.updatedAt = Instant.now();
    }

    public void update(int chunkSize, int chunkOverlap, int candidateK, int rrfK,
                       boolean rerankEnabled, boolean queryRewriteEnabled) {
        if (this.chunkSize != chunkSize || this.chunkOverlap != chunkOverlap) this.reindexRequired = true;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.candidateK = candidateK;
        this.rrfK = rrfK;
        this.rerankEnabled = rerankEnabled;
        this.queryRewriteEnabled = queryRewriteEnabled;
        this.updatedAt = Instant.now();
    }

    public void markReindexed() {
        this.reindexRequired = false;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public int getChunkSize() { return chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }
    public int getCandidateK() { return candidateK; }
    public int getRrfK() { return rrfK; }
    public boolean isRerankEnabled() { return rerankEnabled; }
    public boolean isQueryRewriteEnabled() { return queryRewriteEnabled; }
    public boolean isReindexRequired() { return reindexRequired; }
    public Instant getUpdatedAt() { return updatedAt; }
}
