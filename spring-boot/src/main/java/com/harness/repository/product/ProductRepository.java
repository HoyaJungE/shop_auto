package com.harness.repository.product;

import com.harness.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByCafe24ProductId(String cafe24ProductId);

    boolean existsByCafe24ProductId(String cafe24ProductId);

    Page<Product> findByStatus(Product.Status status, Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.images
        LEFT JOIN FETCH p.options
        WHERE p.id = :id
        """)
    Optional<Product> findByIdWithDetails(@Param("id") Long id);

    @Query("""
        SELECT COUNT(p) FROM Product p
        WHERE p.status = :status
        """)
    long countByStatus(@Param("status") Product.Status status);
}
