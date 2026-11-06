package com.oet.payment.repository;

import com.oet.payment.entity.PricingPackage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PricingPackageRepository extends JpaRepository<PricingPackage, Long> {

    Optional<PricingPackage> findByCodeIgnoreCaseAndActiveTrue(String code);

    List<PricingPackage> findByActiveTrueOrderBySortOrderAsc();
}
