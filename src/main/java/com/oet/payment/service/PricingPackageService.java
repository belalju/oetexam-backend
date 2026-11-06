package com.oet.payment.service;

import com.oet.payment.dto.PricingPackageResponse;
import com.oet.payment.repository.PricingPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PricingPackageService {

    private final PricingPackageRepository pricingPackageRepository;

    @Transactional(readOnly = true)
    public List<PricingPackageResponse> getActivePackages() {
        return pricingPackageRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(PricingPackageResponse::from)
                .toList();
    }
}
