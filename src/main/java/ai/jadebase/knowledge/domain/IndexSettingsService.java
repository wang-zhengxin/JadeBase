package ai.jadebase.knowledge.domain;

import ai.jadebase.knowledge.admin.IndexSettings;
import ai.jadebase.knowledge.admin.IndexSettingsRepository;
import ai.jadebase.rag.application.RetrievalProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndexSettingsService {

    private static final long DEFAULT_WORKSPACE = 1L;
    private final IndexSettingsRepository repository;
    private final RetrievalProperties defaults;

    public IndexSettingsService(IndexSettingsRepository repository, RetrievalProperties defaults) {
        this.repository = repository;
        this.defaults = defaults;
    }

    @Transactional
    public IndexSettings get() {
        return repository.findById(DEFAULT_WORKSPACE).orElseGet(() -> repository.save(new IndexSettings(
                DEFAULT_WORKSPACE, defaults.candidateK(), defaults.rrfK(), defaults.rerankEnabled(),
                defaults.queryRewriteEnabled())));
    }

    @Transactional
    public IndexSettings update(int chunkSize, int chunkOverlap, int candidateK, int rrfK,
                                boolean rerankEnabled, boolean queryRewriteEnabled) {
        validate(chunkSize, chunkOverlap, candidateK, rrfK);
        IndexSettings settings = get();
        settings.update(chunkSize, chunkOverlap, candidateK, rrfK, rerankEnabled, queryRewriteEnabled);
        return repository.save(settings);
    }

    @Transactional
    public IndexSettings markReindexed() {
        IndexSettings settings = get();
        settings.markReindexed();
        return repository.save(settings);
    }

    private void validate(int chunkSize, int chunkOverlap, int candidateK, int rrfK) {
        if (chunkSize < 200 || chunkSize > 4000) {
            throw new IllegalArgumentException("分块大小必须在 200 到 4000 个字符之间");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize || chunkOverlap > 1000) {
            throw new IllegalArgumentException("重叠大小必须小于分块大小且不能超过 1000");
        }
        if (candidateK < 10 || candidateK > 200) {
            throw new IllegalArgumentException("候选片段数必须在 10 到 200 之间");
        }
        if (rrfK < 1 || rrfK > 200) {
            throw new IllegalArgumentException("RRF 常数必须在 1 到 200 之间");
        }
    }
}
