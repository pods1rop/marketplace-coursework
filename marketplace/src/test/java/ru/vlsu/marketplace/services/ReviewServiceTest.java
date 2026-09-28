package ru.vlsu.marketplace.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.vlsu.marketplace.entities.Product;
import ru.vlsu.marketplace.entities.Review;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.repositories.ReviewRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @InjectMocks private ReviewService reviewService;

    private static User user(int id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private static Product product(User seller, Product.Status status) {
        Product p = new Product();
        p.setId(10);
        p.setSeller(seller);
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("Отзыв на опубликованный товар сохраняется")
    void addReview_ok() {
        when(reviewRepository.existsByAuthorIdAndProductId(1, 10)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        Review r = reviewService.addReview(user(1), product(user(2), Product.Status.APPROVED), (byte) 5, "  Отличная вещь ");

        assertThat(r.getRating()).isEqualTo((byte) 5);
        assertThat(r.getText()).isEqualTo("Отличная вещь");
        assertThat(r.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Второй отзыв на тот же товар запрещён")
    void addReview_duplicate() {
        when(reviewRepository.existsByAuthorIdAndProductId(1, 10)).thenReturn(true);
        assertThatThrownBy(() -> reviewService.addReview(user(1), product(user(2), Product.Status.APPROVED), (byte) 4, "ok"))
                .isInstanceOf(IllegalStateException.class);
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Отзыв на собственный товар запрещён")
    void addReview_ownProduct() {
        User seller = user(2);
        assertThatThrownBy(() -> reviewService.addReview(seller, product(seller, Product.Status.APPROVED), (byte) 5, "ok"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Фильтр запрещённых слов и ссылок")
    void forbiddenWordsFilter() {
        assertThat(ReviewService.containsForbidden("Заходите на https://spam.example")).isTrue();
        assertThat(ReviewService.containsForbidden("Лучшие ставки на спорт")).isTrue();
        assertThat(ReviewService.containsForbidden("Хорошее качество, рекомендую")).isFalse();
        assertThat(ReviewService.containsForbidden(null)).isFalse();
    }
}
