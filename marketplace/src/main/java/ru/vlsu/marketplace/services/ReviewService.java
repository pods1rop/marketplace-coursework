package ru.vlsu.marketplace.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.vlsu.marketplace.entities.Product;
import ru.vlsu.marketplace.entities.Review;
import ru.vlsu.marketplace.entities.User;
import ru.vlsu.marketplace.repositories.ReviewRepository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    /** Корни запрещённых слов: отзыв с ними не публикуется. */
    private static final List<String> FORBIDDEN_ROOTS = List.of(
            "хуй", "хуе", "хуё", "пизд", "ебат", "ебан", "ёбан", "бляд", "блят", "сука", "мудак", "долбоёб", "долбоеб",
            "казино", "ставки на спорт", "t.me/", "http://", "https://"
    );

    private final ReviewRepository reviewRepository;

    /**
     * Добавление отзыва покупателем: товар опубликован, не принадлежит автору,
     * отзыв от этого пользователя ещё не оставлен, текст прошёл фильтр.
     */
    public Review addReview(User author, Product product, Byte rating, String text) {
        if (product.getStatus() != Product.Status.APPROVED) {
            throw new IllegalStateException("Отзыв можно оставить только на опубликованный товар");
        }
        if (product.getSeller().getId().equals(author.getId())) {
            throw new IllegalStateException("Нельзя оставить отзыв на собственный товар");
        }
        if (reviewExists(author.getId(), product.getId())) {
            throw new IllegalStateException("Вы уже оставили отзыв на этот товар");
        }
        if (containsForbidden(text)) {
            throw new IllegalStateException("Отзыв содержит запрещённые слова или ссылки");
        }
        Review review = new Review();
        review.setProduct(product);
        review.setAuthor(author);
        review.setRating(rating);
        review.setText(text == null ? null : text.trim());
        review.setCreatedAt(Instant.now());
        return reviewRepository.save(review);
    }

    public static boolean containsForbidden(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return FORBIDDEN_ROOTS.stream().anyMatch(lower::contains);
    }

    public Review save(Review review) {
        if (review.getCreatedAt() == null) {
            review.setCreatedAt(Instant.now());
        }
        return reviewRepository.save(review);
    }

    public void delete(Review review) {
        reviewRepository.delete(review);
    }

    public Optional<Review> findById(Integer id) {
        return reviewRepository.findById(id);
    }

    public List<Review> getByProduct(Integer productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    public List<Review> getByAuthor(Integer authorId) {
        return reviewRepository.findByAuthorId(authorId);
    }

    public boolean reviewExists(Integer authorId, Integer productId) {
        return reviewRepository.existsByAuthorIdAndProductId(authorId, productId);
    }

    public Double getAverageRating(Integer productId) {
        return reviewRepository.getAverageRatingByProductId(productId);
    }
}
