package ru.vlsu.marketplace.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vlsu.marketplace.entities.SellerRequest;

import java.util.List;
import java.util.Optional;

public interface SellerRequestRepository extends JpaRepository<SellerRequest, Integer> {

    @Query("SELECT r FROM SellerRequest r JOIN FETCH r.user WHERE r.status = :status ORDER BY r.createdAt ASC")
    List<SellerRequest> findByStatusWithUser(@Param("status") SellerRequest.Status status);

    Optional<SellerRequest> findFirstByUserIdOrderByCreatedAtDesc(Integer userId);

    boolean existsByUserIdAndStatus(Integer userId, SellerRequest.Status status);
}
