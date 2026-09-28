package ru.vlsu.marketplace.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import ru.vlsu.marketplace.entities.Notification;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.entities.Warning;
import ru.vlsu.marketplace.repositories.UserRepository;
import ru.vlsu.marketplace.repositories.WarningRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserModerationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WarningRepository warningRepository;
    @Mock private NotificationService notificationService;
    @InjectMocks private UserModerationService service;

    private static User user(int id, User.Role role) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        u.setRole(role);
        u.setActive(true);
        return u;
    }

    @Test
    @DisplayName("Модератор блокирует покупателя")
    void moderatorBlocksBuyer() {
        User buyer = user(2, User.Role.buyer);
        when(userRepository.findById(2)).thenReturn(Optional.of(buyer));
        service.setBlocked(user(1, User.Role.moderator), 2, true);
        assertThat(buyer.isActive()).isFalse();
        verify(userRepository).save(buyer);
    }

    @Test
    @DisplayName("Администратора заблокировать нельзя")
    void cannotBlockAdmin() {
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, User.Role.admin)));
        assertThatThrownBy(() -> service.setBlocked(user(1, User.Role.admin), 2, true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Модератора блокирует только администратор")
    void moderatorCannotBlockModerator() {
        User other = user(2, User.Role.moderator);
        when(userRepository.findById(2)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.setBlocked(user(1, User.Role.moderator), 2, true))
                .isInstanceOf(AccessDeniedException.class);

        service.setBlocked(user(3, User.Role.admin), 2, true);
        assertThat(other.isActive()).isFalse();
    }

    @Test
    @DisplayName("Предупреждение сохраняется и отправляется уведомление")
    void warnCreatesWarningAndNotification() {
        User buyer = user(2, User.Role.buyer);
        when(userRepository.findById(2)).thenReturn(Optional.of(buyer));

        Warning w = service.warn(user(1, User.Role.moderator), 2, Warning.Type.SPAM, "реклама в отзывах");

        assertThat(w.getType()).isEqualTo(Warning.Type.SPAM);
        verify(warningRepository).save(any(Warning.class));
        verify(notificationService).notify(eq(buyer), eq(Notification.Type.WARNING), any(), any());
    }

    @Test
    @DisplayName("Менять роли может только администратор")
    void onlyAdminChangesRole() {
        assertThatThrownBy(() -> service.changeRole(user(1, User.Role.moderator), 2, User.Role.seller))
                .isInstanceOf(AccessDeniedException.class);

        User buyer = user(2, User.Role.buyer);
        when(userRepository.findById(2)).thenReturn(Optional.of(buyer));
        service.changeRole(user(1, User.Role.admin), 2, User.Role.seller);
        assertThat(buyer.getRole()).isEqualTo(User.Role.seller);
    }
}
