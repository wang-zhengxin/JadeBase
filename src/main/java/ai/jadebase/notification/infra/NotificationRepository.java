package ai.jadebase.notification.infra;

import ai.jadebase.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findAllByOrderByCreatedAtDesc();
    long countByReadFalse();
}
