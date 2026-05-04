package com.optima.api.modules.business.repository;

import com.optima.api.modules.business.model.Business;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {
    boolean existsBySlug(String slug);
    boolean existsByEmail(String email);
    Optional<Business> findBySlug(String slug);
    Page<Business> findByIsActiveTrue(Pageable pageable);
}
