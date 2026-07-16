package ai.jadebase.connector.feishu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeishuConnectionRepository extends JpaRepository<FeishuConnection, UUID> { }
