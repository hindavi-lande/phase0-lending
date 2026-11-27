package com.example.lending.loan;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds a reducing-balance repayment schedule. All money values are date-only, scaled to two
 * decimals and rounded HALF_UP, matching the rest of the lending domain.
 */
@Component
public class RepaymentScheduleCalculator {

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal MONTHS_PER_YEAR_TIMES_PERCENT = BigDecimal.valueOf(1200);

    public List<ScheduleRow> generate(
            BigDecimal principal,
            BigDecimal annualInterestRate,
            int termMonths,
            LocalDate firstDueDate) {
        if (principal == null) {
            throw new IllegalArgumentException("principal is required");
        }
        if (annualInterestRate == null) {
            throw new IllegalArgumentException("annualInterestRate is required");
        }
        if (firstDueDate == null) {
            throw new IllegalArgumentException("firstDueDate is required");
        }
        if (termMonths < 1) {
            throw new IllegalArgumentException("termMonths must be at least 1");
        }

        BigDecimal openingPrincipal = principal.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal monthlyRate = annualInterestRate.divide(
                MONTHS_PER_YEAR_TIMES_PERCENT, CALCULATION_CONTEXT);

        BigDecimal instalment = monthlyInstalment(openingPrincipal, monthlyRate, termMonths);

        List<ScheduleRow> rows = new ArrayList<>(termMonths);
        BigDecimal openingBalance = openingPrincipal;

        for (int i = 1; i <= termMonths; i++) {
            BigDecimal interest = openingBalance
                    .multiply(monthlyRate)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            BigDecimal principalPart;
            if (i == termMonths) {
                principalPart = openingBalance;
            } else {
                principalPart = instalment.subtract(interest);
                if (principalPart.signum() < 0) {
                    principalPart = BigDecimal.ZERO.setScale(MONEY_SCALE);
                }
                if (principalPart.compareTo(openingBalance) > 0) {
                    principalPart = openingBalance;
                }
            }
            principalPart = principalPart.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            BigDecimal total = principalPart.add(interest).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal closingBalance =
                    openingBalance.subtract(principalPart).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (closingBalance.signum() < 0) {
                closingBalance = BigDecimal.ZERO.setScale(MONEY_SCALE);
            }

            rows.add(new ScheduleRow(
                    i,
                    firstDueDate.plusMonths(i - 1L),
                    openingBalance,
                    principalPart,
                    interest,
                    total,
                    closingBalance));

            openingBalance = closingBalance;
        }

        return rows;
    }

    private BigDecimal monthlyInstalment(
            BigDecimal principal, BigDecimal monthlyRate, int termMonths) {
        if (monthlyRate.signum() == 0) {
            return principal.divide(
                    BigDecimal.valueOf(termMonths), MONEY_SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(termMonths, CALCULATION_CONTEXT);
        return principal
                .multiply(monthlyRate, CALCULATION_CONTEXT)
                .multiply(growth, CALCULATION_CONTEXT)
                .divide(
                        growth.subtract(BigDecimal.ONE, CALCULATION_CONTEXT),
                        MONEY_SCALE,
                        RoundingMode.HALF_UP);
    }

    public record ScheduleRow(
            int sequence,
            LocalDate dueDate,
            BigDecimal openingBalance,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal total,
            BigDecimal closingBalance) {
    }
}
