package ru.vlsu.marketplace.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vlsu.marketplace.entities.Notification;
import ru.vlsu.marketplace.entities.SellerRequest;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.repositories.SellerRequestRepository;
import ru.vlsu.marketplace.repositories.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Заявки покупателей на получение роли продавца. */
@Service
@RequiredArgsConstructor
public class SellerRequestService {

    private final SellerRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public SellerRequest submit(User user, String shopName, String description) {
        if (user.getRole() != User.Role.buyer) {
            throw new IllegalStateException("Заявку может подать только покупатель");
        }
        if (requestRepository.existsByUserIdAndStatus(user.getId(), SellerRequest.Status.PENDING)) {
            throw new IllegalStateException("Заявка уже находится на рассмотрении");
        }
        if (shopName == null || shopName.isBlank()) {
            throw new IllegalStateException("Укажите название магазина");
        }
        SellerRequest r = new SellerRequest();
        r.setUser(user);
        r.setShopName(shopName.trim());
        r.setDescription(description);
        r.setStatus(SellerRequest.Status.PENDING);
        r.setCreatedAt(Instant.now());
        return requestRepository.save(r);
    }

    public List<SellerRequest> getPending() {
        return requestRepository.findByStatusWithUser(SellerRequest.Status.PENDING);
    }

    public Optional<SellerRequest> getLast(Integer userId) {
        return requestRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void decide(Integer requestId, boolean approve) {
        SellerRequest r = requestRepository.findById(requestId).orElseThrow();
        if (r.getStatus() != SellerRequest.Status.PENDING) return;
        r.setStatus(approve ? SellerRequest.Status.APPROVED : SellerRequest.Status.REJECTED);
        User user = r.getUser();
        if (approve && user.getRole() == User.Role.buyer) {
            user.setRole(User.Role.seller);
            userRepository.save(user);
        }
        requestRepository.save(r);
        notificationService.notify(user, Notification.Type.ROLE,
                approve ? "Заявка на роль продавца одобрена. Войдите в аккаунт заново, чтобы открыть кабинет продавца."
                        : "Заявка на роль продавца отклонена администратором.",
                approve ? "/seller/products" : "/profile");
    }
}
