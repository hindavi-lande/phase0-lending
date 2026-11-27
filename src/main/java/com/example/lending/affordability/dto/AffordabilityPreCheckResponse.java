package com.example.lending.affordability.dto;

import com.example.lending.affordability.AffordabilityDecision;
import com.example.lending.affordability.AffordabilityReason;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read-only affordability pre-check result. Field order mirrors the documented example
 * response so the decision stays explainable to the caller.
 */
public record AffordabilityPreCheckResponse(
        UUID applicationId,
        UUID borrowerId,
        BigDecimal requestedAmount,
        int termMonths,
        BigDecimal annualInterestRate,
        BigDecimal monthlyIncome,
        BigDecimal estimatedMonthlyInstalment,
        BigDecimal existingMonthlyObligations,
        BigDecimal totalMonthlyOutgo,
        BigDecimal totalPayable,
        BigDecimal totalInterest,
        BigDecimal debtToIncomeRatio,
        AffordabilityDecision decision,
        List<AffordabilityReason> reasons,
        DtiThresholds thresholds) {

    public record DtiThresholds(BigDecimal preferredMaxDti, BigDecimal hardMaxDti) {
    }
}
