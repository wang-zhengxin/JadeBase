package ai.jadebase.workspace.domain;

import ai.jadebase.workspace.infra.WorkspaceMemoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceMemoryService {

    private static final int MAX_MEMORIES = 20;
    private final WorkspaceMemoryRepository repository;

    public WorkspaceMemoryService(WorkspaceMemoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemory> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public WorkspaceMemory add(String content) {
        String normalized = normalize(content);
        return repository.findByContent(normalized).orElseGet(() -> {
            if (repository.count() >= MAX_MEMORIES) {
                throw new IllegalArgumentException("最多保存 20 条记忆");
            }
            return repository.save(new WorkspaceMemory(normalized));
        });
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) throw new EntityNotFoundException("记忆不存在");
        repository.deleteById(id);
    }

    @Transactional
    public void capture(String question, boolean enabled) {
        if (!enabled || question == null) return;
        String normalized = question.trim();
        if (normalized.startsWith("记住：") || normalized.startsWith("记住:")) {
            add(normalized.substring(3));
        }
    }

    private String normalize(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("记忆内容不能为空");
        String normalized = content.trim();
        if (normalized.length() > 500) throw new IllegalArgumentException("记忆内容不能超过 500 个字符");
        return normalized;
    }
}
