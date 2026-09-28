package ru.vlsu.marketplace.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import ru.vlsu.marketplace.dto.OrderDto;
import ru.vlsu.marketplace.entities.*;
import ru.vlsu.marketplace.repositories.OrderRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private CartService cartService;
    @Mock private NotificationService notificationService;
    @InjectMocks private OrderService orderService;

    private static User user(int id, User.Role role) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        u.setRole(role);
        return u;
    }

    private static Product product(int id, User seller, String price, Product.Status status) {
        Product p = new Product();
        p.setId(id);
        p.setTitle("Товар " + id);
        p.setSeller(seller);
        p.setPrice(new BigDecimal(price));
        p.setStatus(status);
        return p;
    }

    private static OrderDto dto() {
        OrderDto d = new OrderDto();
        d.setContactName("Иван Иванов");
        d.setContactPhone("+7 900 000-00-00");
        d.setDeliveryAddress("г. Владимир");
        return d;
    }

    private static Order order(int id, User buyer, Order.Status status) {
        Order o = new Order();
        o.setId(id);
        o.setBuyer(buyer);
        o.setStatus(status);
        return o;
    }

    @Test
    @DisplayName("Заказ фиксирует цену на момент покупки, считает сумму и очищает корзину")
    void createOrder_snapshotsPriceAndClearsCart() {
        User buyer = user(1, User.Role.buyer);
        User seller = user(2, User.Role.seller);
        CartItem ci = new CartItem();
        ci.setProduct(product(10, seller, "1500.00", Product.Status.APPROVED));
        ci.setQuantity(2);
        when(cartService.getCartItems(1)).thenReturn(List.of(ci));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(77);
            return o;
        });

        Order saved = orderService.createOrder(buyer, dto());

        assertThat(saved.getTotalAmount()).isEqualByComparingTo("3000.00");
        assertThat(saved.getItems()).singleElement()
                .satisfies(i -> assertThat(i.getPrice()).isEqualByComparingTo("1500.00"));
        assertThat(saved.getStatus()).isEqualTo(Order.Status.NEW);
        verify(cartService).clearCart(1);
        verify(notificationService).notify(eq(seller), eq(Notification.Type.ORDER), any(), any());
    }

    @Test
    @DisplayName("Нельзя оформить заказ с товаром, снятым с продажи")
    void createOrder_rejectsRemovedProduct() {
        CartItem ci = new CartItem();
        ci.setProduct(product(10, user(2, User.Role.seller), "100", Product.Status.REMOVED));
        ci.setQuantity(1);
        when(cartService.getCartItems(1)).thenReturn(List.of(ci));

        assertThatThrownBy(() -> orderService.createOrder(user(1, User.Role.buyer), dto()))
                .isInstanceOf(IllegalStateException.class);
        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clearCart(any());
    }

    @Test
    @DisplayName("Пустая корзина — ошибка")
    void createOrder_emptyCart() {
        when(cartService.getCartItems(1)).thenReturn(List.of());
        assertThatThrownBy(() -> orderService.createOrder(user(1, User.Role.buyer), dto()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Корзина пуста");
    }

    @Test
    @DisplayName("Продавец переводит свой заказ NEW → CONFIRMED, покупатель получает уведомление")
    void updateStatusBySeller_validTransition() {
        User seller = user(2, User.Role.seller);
        User buyer = user(1, User.Role.buyer);
        when(orderRepository.findSellersOfOrder(5)).thenReturn(List.of(seller));
        when(orderRepository.findById(5)).thenReturn(Optional.of(order(5, buyer, Order.Status.NEW)));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.updateStatusBySeller(seller, 5, Order.Status.CONFIRMED);

        assertThat(result.getStatus()).isEqualTo(Order.Status.CONFIRMED);
        verify(notificationService).notify(eq(buyer), eq(Notification.Type.ORDER), any(), any());
    }

    @Test
    @DisplayName("Переход NEW → COMPLETED запрещён диаграммой состояний")
    void updateStatusBySeller_invalidTransition() {
        User seller = user(2, User.Role.seller);
        when(orderRepository.findSellersOfOrder(5)).thenReturn(List.of(seller));
        when(orderRepository.findById(5)).thenReturn(Optional.of(order(5, user(1, User.Role.buyer), Order.Status.NEW)));

        assertThatThrownBy(() -> orderService.updateStatusBySeller(seller, 5, Order.Status.COMPLETED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Недопустимый переход");
    }

    @Test
    @DisplayName("Продавец не может менять статус чужого заказа")
    void updateStatusBySeller_foreignOrder() {
        when(orderRepository.findSellersOfOrder(5)).thenReturn(List.of(user(3, User.Role.seller)));
        assertThatThrownBy(() -> orderService.updateStatusBySeller(user(2, User.Role.seller), 5, Order.Status.CONFIRMED))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Покупатель отменяет только новый заказ")
    void cancelByBuyer_onlyNew() {
        User buyer = user(1, User.Role.buyer);
        when(orderRepository.findById(5)).thenReturn(Optional.of(order(5, buyer, Order.Status.CONFIRMED)));
        assertThatThrownBy(() -> orderService.cancelByBuyer(buyer, 5))
                .isInstanceOf(IllegalStateException.class);
    }
}
