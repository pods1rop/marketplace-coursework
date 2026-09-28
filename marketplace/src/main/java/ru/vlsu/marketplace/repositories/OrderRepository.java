package ru.vlsu.marketplace.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vlsu.marketplace.entities.Order;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    List<Order> findByBuyerIdOrderByCreatedAtDesc(Integer buyerId);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.items i WHERE i.product.seller.id = :sellerId ORDER BY o.createdAt DESC")
    List<Order> findBySellerIdOrderByCreatedAtDesc(@Param("sellerId") Integer sellerId);

    long countByStatus(Order.Status status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status <> :excluded")
    java.math.BigDecimal sumTotalExcludingStatus(@Param("excluded") Order.Status excluded);

    @Query("SELECT DISTINCT i.product.seller FROM OrderItem i WHERE i.order.id = :orderId")
    List<ru.vlsu.marketplace.entities.User> findSellersOfOrder(@Param("orderId") Integer orderId);
}
