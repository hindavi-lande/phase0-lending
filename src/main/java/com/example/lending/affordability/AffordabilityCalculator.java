package com.example.lending.affordability;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Pure BigDecimal affordability maths. All money is reported at scale 2 with HALF_UP;
 * intermediate divisions use DECIMAL128 so the final rounding is the only rounding that
 * matters.
 */
@Component
public class AffordabilityCalculator {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal TWELVE = new BigDecimal("12");
    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE = BigDecimal.ONE;

    private final AffordabilityProperties properties;

    public AffordabilityCalculator(AffordabilityProperties properties) {
        this.properties = properties;
    }

    public Result calculate(
            BigDecimal principal,
            int termMonths,
            BigDecimal annualInterestRate,
            BigDecimal annualIncome,
            BigDecimal existingMonthlyObligations) {

        BigDecimal obligations = existingMonthlyObligations.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal monthlyIncome = annualIncome.divide(TWELVE, MONEY_SCALE, ROUNDING);
        BigDecimal instalment = instalment(principal, termMonths, annualInterestRate);
        BigDecimal totalPayable = instalment
                .multiply(BigDecimal.valueOf(termMonths))
                .setScale(MONEY_SCALE, ROUNDING);
        BigDecimal totalInterest = totalPayable
                .subtract(principal)
                .setScale(MONEY_SCALE, ROUNDING);
        BigDecimal totalMonthlyOutgo = instalment.add(obligations).setScale(MONEY_SCALE, ROUNDING);

        if (monthlyIncome.signum() == 0) {
            return new Result(
                    monthlyIncome,
                    instalment,
                    totalPayable,
                    totalInterest,
                    totalMonthlyOutgo,
                    null,
                    AffordabilityDecision.INELIGIBLE,
                    List.of(AffordabilityReason.INCOME_NOT_POSITIVE));
        }

        BigDecimal debtToIncomeRatio = totalMonthlyOutgo
                .divide(monthlyIncome, PRECISION)
                .multiply(ONE_HUNDRED)
                .setScale(MONEY_SCALE, ROUNDING);

        AffordabilityDecision decision =
                decide(debtToIncomeRatio, properties.getPreferredMaxDti(), properties.getHardMaxDti());

        return new Result(
                monthlyIncome,
                instalment,
                totalPayable,
                totalInterest,
                totalMonthlyOutgo,
                debtToIncomeRatio,
                decision,
                List.of(reasonFor(decision)));
    }

    private static BigDecimal instalment(
            BigDecimal principal, int termMonths, BigDecimal annualInterestRate) {

        if (annualInterestRate == null || annualInterestRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(termMonths), MONEY_SCALE, ROUNDING);
        }

        BigDecimal monthlyRate = annualInterestRate.divide(TWELVE_HUNDRED, PRECISION);
        BigDecimal growth = ONE.add(monthlyRate).pow(termMonths, PRECISION);
        BigDecimal numerator = principal.multiply(monthlyRate, PRECISION).multiply(growth, PRECISION);
        BigDecimal denominator = growth.subtract(ONE, PRECISION);

        return numerator.divide(denominator, MONEY_SCALE, ROUNDING);
    }

    static AffordabilityDecision decide(
            BigDecimal debtToIncomeRatio, BigDecimal preferredMaxDti, BigDecimal hardMaxDti) {

        if (debtToIncomeRatio == null) {
            return AffordabilityDecision.INELIGIBLE;
        }
        if (debtToIncomeRatio.compareTo(preferredMaxDti) <= 0) {
            return AffordabilityDecision.ELIGIBLE;
        }
        if (debtToIncomeRatio.compareTo(hardMaxDti) <= 0) {
            return AffordabilityDecision.REFERRED;
        }
        return AffordabilityDecision.INELIGIBLE;
    }

    private static AffordabilityReason reasonFor(AffordabilityDecision decision) {
        return switch (decision) {
            case ELIGIBLE -> AffordabilityReason.WITHIN_AFFORDABILITY_LIMIT;
            case REFERRED -> AffordabilityReason.ABOVE_PREFERRED_LIMIT;
            case INELIGIBLE -> AffordabilityReason.ABOVE_MAXIMUM_LIMIT;
        };
    }

    public record Result(
            BigDecimal monthlyIncome,
            BigDecimal estimatedMonthlyInstalment,
            BigDecimal totalPayable,
            BigDecimal totalInterest,
            BigDecimal totalMonthlyOutgo,
            BigDecimal debtToIncomeRatio,
            AffordabilityDecision decision,
            List<AffordabilityReason> reasons) {
    }
}
