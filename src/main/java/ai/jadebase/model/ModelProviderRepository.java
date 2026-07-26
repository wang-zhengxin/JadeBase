package ai.jadebase.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModelProviderRepository extends JpaRepository<ModelProvider, UUID> {
    List<ModelProvider> findAllByOrderByCreatedAtAsc();
}
