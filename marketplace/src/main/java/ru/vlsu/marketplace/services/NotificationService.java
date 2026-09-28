package ru.vlsu.marketplace.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vlsu.marketplace.entities.Notification;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.repositories.NotificationRepository;

import java.time.Instant;
import java.util.List;

/**
 * Внутренние уведомления пользователей: новый заказ, смена статуса заказа,
 * результат модерации, предупреждение, изменение роли.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void notify(User user, Notification.Type type, String text, String link) {
        Notification n = new Notification();
        n.setUser(user);
        n.setType(type);
        n.setText(text.length() > 500 ? text.substring(0, 500) : text);
        n.setLink(link);
        n.setCreatedAt(Instant.now());
        notificationRepository.save(n);
    }

    public List<Notification> getLatest(Integer userId) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    public long countUnread(Integer userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markAllRead(Integer userId) {
        notificationRepository.markAllRead(userId);
    }
}
