package ru.vlsu.marketplace.config;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import ru.vlsu.marketplace.repositories.UserRepository;
import ru.vlsu.marketplace.services.NotificationService;

/**
 * Общие атрибуты модели для всех страниц: количество непрочитанных
 * уведомлений выводится в шапке рядом с иконкой профиля.
 */
@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @ModelAttribute("unreadNotifications")
    public long unreadNotifications() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return 0;
        }
        return userRepository.findByUsername(auth.getName())
                .map(u -> notificationService.countUnread(u.getId()))
                .orElse(0L);
    }
}
