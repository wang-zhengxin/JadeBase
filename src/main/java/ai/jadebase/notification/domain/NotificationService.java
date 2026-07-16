package ai.jadebase.notification.domain;

import ai.jadebase.notification.infra.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Notification> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByReadFalse();
    }

    @Transactional
    public void markAllRead() {
        List<Notification> notifications = repository.findAllByOrderByCreatedAtDesc();
        notifications.forEach(Notification::markRead);
        repository.saveAll(notifications);
    }
}
