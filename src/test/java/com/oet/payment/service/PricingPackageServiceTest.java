package com.oet.payment.service;

import com.oet.payment.dto.PricingPackageResponse;
import com.oet.payment.entity.PricingPackage;
import com.oet.payment.repository.PricingPackageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingPackageServiceTest {

    @Mock
    private PricingPackageRepository pricingPackageRepository;

    @InjectMocks
    private PricingPackageService service;

    @Test
    void getActivePackages_mapsCodeToIdAndIncludesAllowedTests() {
        PricingPackage silver = PricingPackage.builder()
                .code("silver").name("Silver").description("Most popular")
                .price(new BigDecimal("39.99")).originalPrice(new BigDecimal("49.99")).currency("USD")
                .noOfAllowedTest(25).accessDays(45).popular(true)
                .build();
        when(pricingPackageRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(silver));

        List<PricingPackageResponse> packages = service.getActivePackages();

        assertThat(packages).singleElement().satisfies(response -> {
            assertThat(response.id()).isEqualTo("silver");
            assertThat(response.noOfAllowedTest()).isEqualTo(25);
            assertThat(response.price()).isEqualByComparingTo("39.99");
            assertThat(response.popular()).isTrue();
        });
    }
}
