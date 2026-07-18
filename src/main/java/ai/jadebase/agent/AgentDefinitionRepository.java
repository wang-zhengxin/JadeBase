package ai.jadebase.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentDefinitionRepository extends JpaRepository<AgentDefinition, UUID> {
    List<AgentDefinition> findAllByOrderByUpdatedAtDesc();
}
