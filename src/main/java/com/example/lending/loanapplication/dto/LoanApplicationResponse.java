package com.example.lending.loanapplication.dto;

import com.example.lending.loanapplication.LoanApplication;
import com.example.lending.loanapplication.LoanApplicationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LoanApplicationResponse(
        UUID id,
        UUID borrowerId,
        BigDecimal requestedAmount,
        int termMonths,
        String purpose,
        LoanApplicationStatus status,
        Instant submittedAt) {

    public static LoanApplicationResponse from(LoanApplication application) {
        return new LoanApplicationResponse(
                application.getId(),
                application.getBorrower().getId(),
                application.getRequestedAmount(),
                application.getTermMonths(),
                application.getPurpose(),
                application.getStatus(),
                application.getSubmittedAt());
    }
}
