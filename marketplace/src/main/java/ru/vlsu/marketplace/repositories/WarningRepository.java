package ru.vlsu.marketplace.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vlsu.marketplace.entities.Warning;

import java.util.List;

public interface WarningRepository extends JpaRepository<Warning, Integer> {

    @Query("SELECT w FROM Warning w JOIN FETCH w.moderator WHERE w.user.id = :userId ORDER BY w.createdAt DESC")
    List<Warning> findByUserIdWithModerator(@Param("userId") Integer userId);

    long countByUserId(Integer userId);
}
