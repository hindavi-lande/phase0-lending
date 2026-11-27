package com.example.lending.loan.dto;

import com.example.lending.loan.Installment;
import com.example.lending.loan.Loan;
import com.example.lending.loan.LoanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LoanResponse(
        UUID loanId,
        UUID applicationId,
        BigDecimal principal,
        BigDecimal annualInterestRate,
        int termMonths,
        BigDecimal monthlyInstalment,
        BigDecimal totalPayable,
        BigDecimal totalInterest,
        LocalDate firstDueDate,
        LocalDate maturityDate,
        LoanStatus status,
        List<InstallmentResponse> schedule) {

    public static LoanResponse from(Loan loan, List<Installment> installments) {
        return new LoanResponse(
                loan.getId(),
                loan.getApplication().getId(),
                loan.getPrincipal(),
                loan.getAnnualInterestRate(),
                loan.getTermMonths(),
                loan.getMonthlyInstalment(),
                loan.getTotalPayable(),
                loan.getTotalInterest(),
                loan.getFirstDueDate(),
                loan.getMaturityDate(),
                loan.getStatus(),
                installments.stream().map(InstallmentResponse::from).toList());
    }
}
