package ai.jadebase.notification.api;

import ai.jadebase.notification.domain.Notification;
import ai.jadebase.notification.domain.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public NotificationList list() {
        List<NotificationItem> items = service.list().stream().map(NotificationItem::from).toList();
        return new NotificationList(service.unreadCount(), items);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        service.markAllRead();
    }

    public record NotificationList(long unreadCount, List<NotificationItem> items) { }
    public record NotificationItem(UUID id, String title, String message, boolean read, Instant createdAt) {
        static NotificationItem from(Notification notification) {
            return new NotificationItem(notification.getId(), notification.getTitle(), notification.getMessage(),
                    notification.isRead(), notification.getCreatedAt());
        }
    }
}
