package com.example.lending.affordability;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the affordability pre-check. Every field has a default so the
 * application starts with no affordability properties present.
 */
@Validated
@ConfigurationProperties(prefix = "lending.affordability")
public class AffordabilityProperties {

    @DecimalMin(value = "0.00", message = "defaultAnnualInterestRate must be between 0 and 36")
    @DecimalMax(value = "36.00", message = "defaultAnnualInterestRate must be between 0 and 36")
    private BigDecimal defaultAnnualInterestRate = new BigDecimal("12.50");

    @DecimalMin(value = "0.00", message = "preferredMaxDti must be between 0 and 100")
    @DecimalMax(value = "100.00", message = "preferredMaxDti must be between 0 and 100")
    private BigDecimal preferredMaxDti = new BigDecimal("40.00");

    @DecimalMin(value = "0.00", message = "hardMaxDti must be between 0 and 100")
    @DecimalMax(value = "100.00", message = "hardMaxDti must be between 0 and 100")
    private BigDecimal hardMaxDti = new BigDecimal("50.00");

    public BigDecimal getDefaultAnnualInterestRate() {
        return defaultAnnualInterestRate;
    }

    public void setDefaultAnnualInterestRate(BigDecimal defaultAnnualInterestRate) {
        this.defaultAnnualInterestRate = defaultAnnualInterestRate;
    }

    public BigDecimal getPreferredMaxDti() {
        return preferredMaxDti;
    }

    public void setPreferredMaxDti(BigDecimal preferredMaxDti) {
        this.preferredMaxDti = preferredMaxDti;
    }

    public BigDecimal getHardMaxDti() {
        return hardMaxDti;
    }

    public void setHardMaxDti(BigDecimal hardMaxDti) {
        this.hardMaxDti = hardMaxDti;
    }

    @AssertTrue(message = "preferredMaxDti must be lower than hardMaxDti")
    public boolean isPreferredBelowHard() {
        return preferredMaxDti == null
                || hardMaxDti == null
                || preferredMaxDti.compareTo(hardMaxDti) < 0;
    }
}
