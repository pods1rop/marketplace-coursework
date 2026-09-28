package ru.vlsu.marketplace.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vlsu.marketplace.entities.Notification;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.entities.Warning;
import ru.vlsu.marketplace.repositories.UserRepository;
import ru.vlsu.marketplace.repositories.WarningRepository;

import java.time.Instant;
import java.util.List;

/**
 * Блокировка, предупреждения и смена ролей пользователей.
 * Правила: администратора заблокировать нельзя, модератора может
 * заблокировать только администратор, к себе действия не применяются.
 */
@Service
@RequiredArgsConstructor
public class UserModerationService {

    private final UserRepository userRepository;
    private final WarningRepository warningRepository;
    private final NotificationService notificationService;

    @Transactional
    public void setBlocked(User actor, Integer targetId, boolean blocked) {
        User target = userRepository.findById(targetId).orElseThrow();
        checkCanModerate(actor, target);
        target.setActive(!blocked);
        userRepository.save(target);
    }

    @Transactional
    public Warning warn(User actor, Integer targetId, Warning.Type type, String comment) {
        User target = userRepository.findById(targetId).orElseThrow();
        checkCanModerate(actor, target);
        Warning w = new Warning();
        w.setUser(target);
        w.setModerator(actor);
        w.setType(type);
        w.setComment(comment);
        w.setCreatedAt(Instant.now());
        warningRepository.save(w);
        notificationService.notify(target, Notification.Type.WARNING,
                "Вам вынесено предупреждение: " + describe(type)
                        + (comment != null && !comment.isBlank() ? ". Комментарий модератора: " + comment : ""),
                "/profile?tab=notifications");
        return w;
    }

    @Transactional
    public void changeRole(User actor, Integer targetId, User.Role role) {
        if (actor.getRole() != User.Role.admin) {
            throw new AccessDeniedException("Менять роли может только администратор");
        }
        User target = userRepository.findById(targetId).orElseThrow();
        if (target.getId().equals(actor.getId())) {
            throw new AccessDeniedException("Нельзя изменить собственную роль");
        }
        target.setRole(role);
        userRepository.save(target);
        notificationService.notify(target, Notification.Type.ROLE,
                "Ваша роль изменена на «" + roleTitle(role) + "»", "/profile");
    }

    public List<Warning> getWarnings(Integer userId) {
        return warningRepository.findByUserIdWithModerator(userId);
    }

    private void checkCanModerate(User actor, User target) {
        if (actor.getRole() != User.Role.admin && actor.getRole() != User.Role.moderator) {
            throw new AccessDeniedException("Недостаточно прав");
        }
        if (actor.getId().equals(target.getId())) {
            throw new AccessDeniedException("Нельзя применить действие к самому себе");
        }
        if (target.getRole() == User.Role.admin) {
            throw new AccessDeniedException("Действие к администратору неприменимо");
        }
        if (target.getRole() == User.Role.moderator && actor.getRole() != User.Role.admin) {
            throw new AccessDeniedException("Модератора может заблокировать только администратор");
        }
    }

    public static String describe(Warning.Type type) {
        return switch (type) {
            case SPAM -> "спам";
            case FRAUD -> "подозрение на мошенничество";
            case RUDENESS -> "некорректное поведение";
            case FAKE_PRODUCT -> "размещение подделки";
            case OTHER -> "нарушение правил площадки";
        };
    }

    public static String roleTitle(User.Role role) {
        return switch (role) {
            case buyer -> "Покупатель";
            case seller -> "Продавец";
            case moderator -> "Модератор";
            case admin -> "Администратор";
        };
    }
}
