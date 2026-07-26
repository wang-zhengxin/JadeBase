package ai.jadebase.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AgentVersionRepository extends JpaRepository<AgentVersion, UUID> {
    Optional<AgentVersion> findByAgentIdAndVersion(UUID agentId, int version);
}
