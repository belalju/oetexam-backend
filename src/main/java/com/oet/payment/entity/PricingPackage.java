package com.oet.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Server-side price list. Checkout always charges {@link #price} and rejects any client amount
 * that differs. Retire a package with {@code active = false}; payments and users reference it.
 */
@Entity
@Table(name = "pricing_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable public identifier used by the frontend and checkout, e.g. "gold". */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Pre-discount price shown struck through; null when there is no discount. */
    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Practice test sets the package unlocks per module (Reading, Listening). */
    @Column(name = "no_of_allowed_test", nullable = false)
    private Integer noOfAllowedTest;

    @Column(name = "access_days", nullable = false)
    private Integer accessDays;

    @Column(name = "is_popular", nullable = false)
    @Builder.Default
    private boolean popular = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Compares by value so 24.99 and 24.990 are treated as equal. */
    public boolean matches(BigDecimal amount, String currencyCode) {
        return amount != null
                && price.compareTo(amount) == 0
                && currency.equalsIgnoreCase(currencyCode);
    }
}
