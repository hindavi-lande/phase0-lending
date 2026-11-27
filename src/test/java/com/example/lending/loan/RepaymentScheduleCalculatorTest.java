package com.example.lending.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lending.loan.RepaymentScheduleCalculator.ScheduleRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepaymentScheduleCalculatorTest {

    private final RepaymentScheduleCalculator calculator = new RepaymentScheduleCalculator();

    @Test
    void ticketExampleProducesExpectedInstalmentAndTotals() {
        List<ScheduleRow> rows = calculator.generate(
                new BigDecimal("25000.00"),
                new BigDecimal("12.50"),
                24,
                LocalDate.of(2027, 1, 5));

        assertThat(rows).hasSize(24);
        assertThat(rows.get(0).sequence()).isEqualTo(1);
        assertThat(rows.get(0).total()).isEqualByComparingTo("1182.68");
        assertThat(rows.get(0).openingBalance()).isEqualByComparingTo("25000.00");
        assertThat(rows.get(0).principal()).isEqualByComparingTo("922.26");
        assertThat(rows.get(0).interest()).isEqualByComparingTo("260.42");
        assertThat(rows.get(0).closingBalance()).isEqualByComparingTo("24077.74");

        ScheduleRow last = rows.get(23);
        assertThat(last.sequence()).isEqualTo(24);
        assertThat(last.openingBalance()).isEqualByComparingTo("1170.54");
        assertThat(last.principal()).isEqualByComparingTo("1170.54");
        assertThat(last.interest()).isEqualByComparingTo("12.19");
        assertThat(last.total()).isEqualByComparingTo("1182.73");
        assertThat(last.closingBalance()).isEqualByComparingTo("0.00");

        BigDecimal totalPayable = rows.stream()
                .map(ScheduleRow::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInterest = totalPayable.subtract(new BigDecimal("25000.00"));
        assertThat(totalPayable).isEqualByComparingTo("28384.37");
        assertThat(totalInterest).isEqualByComparingTo("3384.37");
    }

    @Test
    void zeroRateDividesPrincipalByTermWithNoInterest() {
        List<ScheduleRow> rows = calculator.generate(
                new BigDecimal("100.00"), new BigDecimal("0.00"), 3, LocalDate.of(2027, 3, 15));

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).total()).isEqualByComparingTo("33.33");
        assertThat(rows.get(0).principal()).isEqualByComparingTo("33.33");
        assertThat(rows.get(0).interest()).isEqualByComparingTo("0.00");
        assertThat(rows.get(0).closingBalance()).isEqualByComparingTo("66.67");

        assertThat(rows.get(2).principal()).isEqualByComparingTo("33.34");
        assertThat(rows.get(2).interest()).isEqualByComparingTo("0.00");
        assertThat(rows.get(2).total()).isEqualByComparingTo("33.34");
        assertThat(rows.get(2).closingBalance()).isEqualByComparingTo("0.00");

        BigDecimal principalSum = rows.stream()
                .map(ScheduleRow::principal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(principalSum).isEqualByComparingTo("100.00");
    }

    @Test
    void sumOfPrincipalMatchesApprovedAmountExactly() {
        BigDecimal principal = new BigDecimal("25000.00");
        List<ScheduleRow> rows = calculator.generate(
                principal, new BigDecimal("12.50"), 24, LocalDate.of(2027, 1, 5));

        BigDecimal principalSum = rows.stream()
                .map(ScheduleRow::principal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(principalSum).isEqualByComparingTo(principal);
        assertThat(rows.get(rows.size() - 1).closingBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void schedulesHaveExactlyTermMonthsOrderedRowsAndNoNegativeBalances() {
        List<ScheduleRow> rows = calculator.generate(
                new BigDecimal("10000.00"),
                new BigDecimal("36.00"),
                17,
                LocalDate.of(2027, 6, 30));

        assertThat(rows).hasSize(17);
        for (int i = 0; i < rows.size(); i++) {
            ScheduleRow row = rows.get(i);
            assertThat(row.sequence()).isEqualTo(i + 1);
            assertThat(row.openingBalance().signum()).isGreaterThanOrEqualTo(0);
            assertThat(row.principal().signum()).isGreaterThanOrEqualTo(0);
            assertThat(row.interest().signum()).isGreaterThanOrEqualTo(0);
            assertThat(row.total().signum()).isGreaterThanOrEqualTo(0);
            assertThat(row.closingBalance().signum()).isGreaterThanOrEqualTo(0);
        }
        assertThat(rows.get(rows.size() - 1).closingBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void dueDatesClampToTheLastValidDayOfTheMonth() {
        List<ScheduleRow> rows = calculator.generate(
                new BigDecimal("1200.00"),
                new BigDecimal("0.00"),
                4,
                LocalDate.of(2027, 1, 31));

        assertThat(rows).extracting(ScheduleRow::dueDate).containsExactly(
                LocalDate.of(2027, 1, 31),
                LocalDate.of(2027, 2, 28),
                LocalDate.of(2027, 3, 31),
                LocalDate.of(2027, 4, 30));
        assertThat(rows.get(rows.size() - 1).dueDate()).isEqualTo(LocalDate.of(2027, 4, 30));
    }

    @Test
    void leapYearFebruaryClampsToTwentyNineDays() {
        List<ScheduleRow> rows = calculator.generate(
                new BigDecimal("900.00"),
                new BigDecimal("0.00"),
                3,
                LocalDate.of(2028, 1, 31));

        assertThat(rows).extracting(ScheduleRow::dueDate).containsExactly(
                LocalDate.of(2028, 1, 31),
                LocalDate.of(2028, 2, 29),
                LocalDate.of(2028, 3, 31));
    }
}
