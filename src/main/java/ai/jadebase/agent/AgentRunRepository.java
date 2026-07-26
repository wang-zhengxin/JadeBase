package ai.jadebase.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
    List<AgentRun> findTop20ByAgentIdOrderByStartedAtDesc(UUID agentId);
}
