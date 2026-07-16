package ai.jadebase.notification;

import ai.jadebase.notification.domain.Notification;
import ai.jadebase.notification.infra.NotificationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class NotificationBootstrap implements CommandLineRunner {

    private final NotificationRepository repository;

    public NotificationBootstrap(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() == 0) {
            repository.save(new Notification("欢迎使用 JadeBase", "第一阶段知识库问答工作台已准备就绪。"));
            repository.save(new Notification("本地演示模式", "配置模型密钥后即可启用大模型生成回答。"));
        }
    }
}
