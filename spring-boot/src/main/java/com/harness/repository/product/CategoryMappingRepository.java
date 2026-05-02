package com.harness.repository.product;

import com.harness.domain.product.CategoryMapping;
import com.harness.domain.product.PlatformRegistration.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryMappingRepository extends JpaRepository<CategoryMapping, Long> {

    Optional<CategoryMapping> findByCafe24CategoryAndPlatform(String cafe24Category, Platform platform);

    List<CategoryMapping> findByCafe24Category(String cafe24Category);

    List<CategoryMapping> findByConfirmedFalse();
}
