package ai.jadebase.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LanguageModelRepository extends JpaRepository<LanguageModel, UUID> {
    List<LanguageModel> findByProviderIdOrderByCreatedAtAsc(UUID providerId);
    Optional<LanguageModel> findByProviderIdAndModelId(UUID providerId, String modelId);
    Optional<LanguageModel> findFirstByDefaultModelTrueAndEnabledTrueOrderByCreatedAtAsc();
    Optional<LanguageModel> findFirstByEnabledTrueOrderByCreatedAtAsc();
}
