package ru.vlsu.marketplace.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vlsu.marketplace.dto.OrderDto;
import ru.vlsu.marketplace.entities.*;
import ru.vlsu.marketplace.repositories.OrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderService {

    /** Допустимые переходы жизненного цикла заказа (см. диаграмму состояний «Заказ»). */
    private static final Map<Order.Status, Set<Order.Status>> TRANSITIONS = Map.of(
            Order.Status.NEW, EnumSet.of(Order.Status.CONFIRMED, Order.Status.CANCELLED),
            Order.Status.CONFIRMED, EnumSet.of(Order.Status.IN_DELIVERY, Order.Status.CANCELLED),
            Order.Status.IN_DELIVERY, EnumSet.of(Order.Status.DELIVERED, Order.Status.CANCELLED),
            Order.Status.DELIVERED, EnumSet.of(Order.Status.COMPLETED, Order.Status.CANCELLED),
            Order.Status.COMPLETED, EnumSet.noneOf(Order.Status.class),
            Order.Status.CANCELLED, EnumSet.noneOf(Order.Status.class)
    );

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final NotificationService notificationService;

    @Transactional
    public Order createOrder(User buyer, OrderDto dto) {
        List<CartItem> cartItems = cartService.getCartItems(buyer.getId());
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Корзина пуста");
        }

        Order order = new Order();
        order.setBuyer(buyer);
        order.setStatus(Order.Status.NEW);
        order.setDeliveryAddress(dto.getDeliveryAddress().trim());
        order.setContactName(dto.getContactName().trim());
        order.setContactPhone(dto.getContactPhone().trim());
        order.setCreatedAt(Instant.now());

        List<OrderItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem ci : cartItems) {
            Product product = ci.getProduct();
            // Товар мог быть снят с продажи, пока лежал в корзине
            if (product.getStatus() != Product.Status.APPROVED) {
                throw new IllegalStateException("Товар «" + product.getTitle() + "» больше недоступен — удалите его из корзины");
            }
            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setProduct(product);
            // Снимок цены на момент покупки: последующая смена цены не затронет заказ
            oi.setPrice(product.getPrice());
            oi.setQuantity(ci.getQuantity());
            items.add(oi);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())));
        }

        order.setItems(items);
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        cartService.clearCart(buyer.getId());

        notificationService.notify(buyer, Notification.Type.ORDER,
                "Заказ №" + saved.getId() + " оформлен на сумму " + total + " ₽", "/orders");
        items.stream().map(i -> i.getProduct().getSeller()).distinct().forEach(seller ->
                notificationService.notify(seller, Notification.Type.ORDER,
                        "Новый заказ №" + saved.getId() + " на ваши товары", "/seller/orders"));
        return saved;
    }

    public List<Order> getOrdersByBuyer(Integer buyerId) {
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId);
    }

    public List<Order> getOrdersBySeller(Integer sellerId) {
        return orderRepository.findBySellerIdOrderByCreatedAtDesc(sellerId);
    }

    public Optional<Order> findById(Integer id) {
        return orderRepository.findById(id);
    }

    /** Смена статуса продавцом: разрешена только для заказов с его товарами и по допустимому переходу. */
    @Transactional
    public Order updateStatusBySeller(User seller, Integer orderId, Order.Status status) {
        boolean owns = orderRepository.findSellersOfOrder(orderId).stream()
                .anyMatch(s -> s.getId().equals(seller.getId()));
        if (!owns && seller.getRole() != User.Role.admin) {
            throw new AccessDeniedException("Заказ не содержит ваших товаров");
        }
        return changeStatus(orderId, status);
    }

    /** Отмена заказа покупателем — только пока продавец его не подтвердил. */
    @Transactional
    public Order cancelByBuyer(User buyer, Integer orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new AccessDeniedException("Это не ваш заказ");
        }
        if (order.getStatus() != Order.Status.NEW) {
            throw new IllegalStateException("Заказ уже подтверждён продавцом и не может быть отменён");
        }
        return changeStatus(orderId, Order.Status.CANCELLED);
    }

    private Order changeStatus(Integer orderId, Order.Status status) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (!TRANSITIONS.get(order.getStatus()).contains(status)) {
            throw new IllegalStateException("Недопустимый переход статуса: "
                    + statusTitle(order.getStatus()) + " → " + statusTitle(status));
        }
        order.setStatus(status);
        Order saved = orderRepository.save(order);
        notificationService.notify(order.getBuyer(), Notification.Type.ORDER,
                "Статус заказа №" + order.getId() + " изменён: " + statusTitle(status), "/orders");
        return saved;
    }

    public static String statusTitle(Order.Status s) {
        return switch (s) {
            case NEW -> "Новый";
            case CONFIRMED -> "Подтверждён";
            case IN_DELIVERY -> "В доставке";
            case DELIVERED -> "Доставлен";
            case COMPLETED -> "Завершён";
            case CANCELLED -> "Отменён";
        };
    }

    public long count() {
        return orderRepository.count();
    }
}
