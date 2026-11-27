package com.example.lending.affordability.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Request payload for the affordability pre-check. Both fields are optional: an absent
 * rate falls back to the configured default and absent obligations mean zero.
 */
public record AffordabilityPreCheckRequest(
        @DecimalMin(value = "0.00", message = "annualInterestRate must be between 0 and 36")
        @DecimalMax(value = "36.00", message = "annualInterestRate must be between 0 and 36")
        BigDecimal annualInterestRate,

        @DecimalMin(value = "0.00", message = "existingMonthlyObligations must not be negative")
        @Digits(integer = 17, fraction = 2, message = "existingMonthlyObligations must have at most 2 decimals")
        BigDecimal existingMonthlyObligations) {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public AffordabilityPreCheckRequest {
        if (existingMonthlyObligations == null) {
            existingMonthlyObligations = ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
