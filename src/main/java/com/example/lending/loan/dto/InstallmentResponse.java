package com.example.lending.loan.dto;

import com.example.lending.loan.Installment;
import java.math.BigDecimal;
import java.time.LocalDate;

public record InstallmentResponse(
        int sequence,
        LocalDate dueDate,
        BigDecimal openingBalance,
        BigDecimal principal,
        BigDecimal interest,
        BigDecimal total,
        BigDecimal closingBalance) {

    public static InstallmentResponse from(Installment installment) {
        return new InstallmentResponse(
                installment.getSequence(),
                installment.getDueDate(),
                installment.getOpeningBalance(),
                installment.getPrincipal(),
                installment.getInterest(),
                installment.getTotal(),
                installment.getClosingBalance());
    }
}
