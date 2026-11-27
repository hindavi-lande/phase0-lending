package com.example.lending.affordability;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AffordabilityCalculatorTest {

    private static final BigDecimal PREFERRED = new BigDecimal("40.00");
    private static final BigDecimal HARD = new BigDecimal("50.00");

    private final AffordabilityProperties properties = new AffordabilityProperties();
    private final AffordabilityCalculator calculator = new AffordabilityCalculator(properties);

    @Test
    void standardRateMatchesTicketExample() {
        AffordabilityCalculator.Result result = calculator.calculate(
                new BigDecimal("25000.00"),
                24,
                new BigDecimal("12.50"),
                new BigDecimal("120000.00"),
                new BigDecimal("1500.00"));

        assertThat(result.monthlyIncome()).isEqualByComparingTo("10000.00");
        assertThat(result.estimatedMonthlyInstalment()).isEqualByComparingTo("1182.68");
        assertThat(result.totalPayable()).isEqualByComparingTo("28384.32");
        assertThat(result.totalInterest()).isEqualByComparingTo("3384.32");
        assertThat(result.totalMonthlyOutgo()).isEqualByComparingTo("2682.68");
        assertThat(result.debtToIncomeRatio()).isEqualByComparingTo("26.83");
        assertThat(result.decision()).isEqualTo(AffordabilityDecision.ELIGIBLE);
        assertThat(result.reasons()).containsExactly(AffordabilityReason.WITHIN_AFFORDABILITY_LIMIT);
        assertThat(result.estimatedMonthlyInstalment().scale()).isEqualTo(2);
        assertThat(result.totalPayable().scale()).isEqualTo(2);
        assertThat(result.debtToIncomeRatio().scale()).isEqualTo(2);
    }

    @Test
    void zeroRateUsesPrincipalDividedByTermRoundedToTwoDecimals() {
        AffordabilityCalculator.Result result = calculator.calculate(
                new BigDecimal("25000.00"),
                24,
                new BigDecimal("0.00"),
                new BigDecimal("120000.00"),
                new BigDecimal("0.00"));

        assertThat(result.estimatedMonthlyInstalment()).isEqualByComparingTo("1041.67");
        assertThat(result.totalPayable()).isEqualByComparingTo("25000.08");
        assertThat(result.totalInterest()).isEqualByComparingTo("0.08");
        assertThat(result.totalMonthlyOutgo()).isEqualByComparingTo("1041.67");
        assertThat(result.debtToIncomeRatio()).isEqualByComparingTo("10.42");
        assertThat(result.decision()).isEqualTo(AffordabilityDecision.ELIGIBLE);
    }

    @Test
    void monthlyIncomeRoundsHalfUpToTwoDecimals() {
        AffordabilityCalculator.Result result = calculator.calculate(
                new BigDecimal("1000.00"),
                1,
                new BigDecimal("0.00"),
                new BigDecimal("120000.06"),
                new BigDecimal("0.00"));

        assertThat(result.monthlyIncome()).isEqualByComparingTo("10000.01");
        assertThat(result.monthlyIncome().scale()).isEqualTo(2);
    }

    @Test
    void debtToIncomeRatioRoundsHalfUpToTwoDecimals() {
        // 4000.50 outgo / 10000.00 income * 100 = 40.005, which rounds up to 40.01.
        AffordabilityCalculator.Result result = calculator.calculate(
                new BigDecimal("4000.50"),
                1,
                new BigDecimal("0.00"),
                new BigDecimal("120000.00"),
                new BigDecimal("0.00"));

        assertThat(result.debtToIncomeRatio()).isEqualByComparingTo("40.01");
        assertThat(result.decision()).isEqualTo(AffordabilityDecision.REFERRED);
        assertThat(result.reasons()).containsExactly(AffordabilityReason.ABOVE_PREFERRED_LIMIT);
    }

    @Test
    void decisionBandsAreInclusiveAtTheirBoundaries() {
        assertThat(AffordabilityCalculator.decide(new BigDecimal("40.00"), PREFERRED, HARD))
                .isEqualTo(AffordabilityDecision.ELIGIBLE);
        assertThat(AffordabilityCalculator.decide(new BigDecimal("40.01"), PREFERRED, HARD))
                .isEqualTo(AffordabilityDecision.REFERRED);
        assertThat(AffordabilityCalculator.decide(new BigDecimal("50.00"), PREFERRED, HARD))
                .isEqualTo(AffordabilityDecision.REFERRED);
        assertThat(AffordabilityCalculator.decide(new BigDecimal("50.01"), PREFERRED, HARD))
                .isEqualTo(AffordabilityDecision.INELIGIBLE);
        assertThat(AffordabilityCalculator.decide(null, PREFERRED, HARD))
                .isEqualTo(AffordabilityDecision.INELIGIBLE);
    }

    @Test
    void zeroIncomeIsIneligibleWithIncomeNotPositive() {
        AffordabilityCalculator.Result result = calculator.calculate(
                new BigDecimal("25000.00"),
                24,
                new BigDecimal("12.50"),
                new BigDecimal("0.00"),
                new BigDecimal("0.00"));

        assertThat(result.monthlyIncome()).isEqualByComparingTo("0.00");
        assertThat(result.debtToIncomeRatio()).isNull();
        assertThat(result.decision()).isEqualTo(AffordabilityDecision.INELIGIBLE);
        assertThat(result.reasons()).containsExactly(AffordabilityReason.INCOME_NOT_POSITIVE);
    }

    @Test
    void customThresholdsShiftTheDecisionBands() {
        properties.setPreferredMaxDti(new BigDecimal("30.00"));
        properties.setHardMaxDti(new BigDecimal("45.00"));

        assertThat(calculateWithFlatOutgo("3000.00").decision())
                .isEqualTo(AffordabilityDecision.ELIGIBLE);
        assertThat(calculateWithFlatOutgo("3001.00").decision())
                .isEqualTo(AffordabilityDecision.REFERRED);
        assertThat(calculateWithFlatOutgo("4500.00").decision())
                .isEqualTo(AffordabilityDecision.REFERRED);
        assertThat(calculateWithFlatOutgo("4501.00").decision())
                .isEqualTo(AffordabilityDecision.INELIGIBLE);
    }

    /**
     * A one-month zero-rate loan makes the instalment equal to the principal, so the
     * requested amount is also the total monthly outgo against a 10000.00 income.
     */
    private AffordabilityCalculator.Result calculateWithFlatOutgo(String amount) {
        return calculator.calculate(
                new BigDecimal(amount),
                1,
                new BigDecimal("0.00"),
                new BigDecimal("120000.00"),
                new BigDecimal("0.00"));
    }
}
