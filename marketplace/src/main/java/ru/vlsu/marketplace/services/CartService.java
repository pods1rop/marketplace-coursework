package ru.vlsu.marketplace.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vlsu.marketplace.entities.CartItem;
import ru.vlsu.marketplace.entities.Product;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.repositories.CartItemRepository;
import ru.vlsu.marketplace.repositories.ProductRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    public void addToCart(User user, Integer productId) {
        Product product = productRepository.findById(productId).orElseThrow();
        // В корзину попадают только опубликованные товары, причём не свои собственные
        if (product.getStatus() != Product.Status.APPROVED) {
            throw new IllegalStateException("Товар недоступен для покупки");
        }
        if (product.getSeller().getId().equals(user.getId())) {
            throw new IllegalStateException("Нельзя купить собственный товар");
        }
        Optional<CartItem> existing = cartItemRepository.findByUserIdAndProductId(user.getId(), productId);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + 1);
            cartItemRepository.save(item);
        } else {
            CartItem item = new CartItem();
            item.setUser(user);
            item.setProduct(product);
            item.setQuantity(1);
            cartItemRepository.save(item);
        }
    }

    public void removeFromCart(Integer userId, Integer productId) {
        cartItemRepository.findByUserIdAndProductId(userId, productId)
                .ifPresent(cartItemRepository::delete);
    }

    public List<CartItem> getCartItems(Integer userId) {
        return cartItemRepository.findByUserId(userId);
    }

    public BigDecimal getCartTotal(Integer userId) {
        return cartItemRepository.findByUserId(userId).stream()
                .map(i -> i.getProduct().getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public void clearCart(Integer userId) {
        cartItemRepository.deleteByUserId(userId);
    }
}
