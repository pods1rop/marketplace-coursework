package ru.vlsu.marketplace.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vlsu.marketplace.entities.Product;
import ru.vlsu.marketplace.entities.User;

import java.math.BigDecimal;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    List<Product> findBySeller(User seller);

    List<Product> findBySellerIdAndStatus(Integer sellerId, Product.Status status);

    Page<Product> findByStatus(Product.Status status, Pageable pageable);

    // Категория, бренд и продавец подгружаются тем же запросом (JOIN FETCH) — без N+1 при выводе каталога
    @EntityGraph(attributePaths = {"category", "brand", "seller"})
    @Query("SELECT p FROM Product p WHERE p.status = 'APPROVED' " +
           "AND (:categoryId IS NULL OR p.category.id = :categoryId) " +
           "AND (:brandId IS NULL OR p.brand.id = :brandId) " +
           "AND (:minPrice IS NULL OR p.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR p.price <= :maxPrice) " +
           "AND (:condition IS NULL OR p.condition = :condition) " +
           "AND (:gender IS NULL OR p.gender = :gender) " +
           "AND (:season IS NULL OR p.season = :season) " +
           "AND (:color IS NULL OR LOWER(p.color) = LOWER(:color)) " +
           "AND (:material IS NULL OR LOWER(p.material) = LOWER(:material)) " +
           "AND (:size IS NULL OR LOWER(p.size) = LOWER(:size)) " +
           "AND (:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<Product> findWithFilters(@Param("categoryId") Integer categoryId,
                                  @Param("brandId") Integer brandId,
                                  @Param("minPrice") BigDecimal minPrice,
                                  @Param("maxPrice") BigDecimal maxPrice,
                                  @Param("condition") Product.Condition condition,
                                  @Param("gender") Product.Gender gender,
                                  @Param("season") Product.Season season,
                                  @Param("color") String color,
                                  @Param("material") String material,
                                  @Param("size") String size,
                                  @Param("search") String search,
                                  Pageable pageable);

    @Query("SELECT DISTINCT p.color FROM Product p WHERE p.status = 'APPROVED' AND p.color IS NOT NULL ORDER BY p.color")
    List<String> findDistinctColors();

    @Query("SELECT DISTINCT p.material FROM Product p WHERE p.status = 'APPROVED' AND p.material IS NOT NULL ORDER BY p.material")
    List<String> findDistinctMaterials();

    @Query("SELECT DISTINCT p.size FROM Product p WHERE p.status = 'APPROVED' AND p.size IS NOT NULL ORDER BY p.size")
    List<String> findDistinctSizes();

    @Query("SELECT p FROM Product p WHERE p.status = 'APPROVED' ORDER BY p.createdAt DESC")
    List<Product> findNewest(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 'APPROVED' AND p.category.id = :categoryId AND p.id <> :excludeId ORDER BY p.createdAt DESC")
    List<Product> findSimilar(@Param("categoryId") Integer categoryId, @Param("excludeId") Integer excludeId, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.status = 'APPROVED' ORDER BY SIZE(p.orderItems) DESC")
    List<Product> findPopular(Pageable pageable);

    List<Product> findByStatusOrderByCreatedAtDesc(Product.Status status);

    long countByStatus(Product.Status status);
}
