package com.example.lending.loanapplication.dto;

import com.example.lending.loanapplication.LoanApplicationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record LoanApplicationRequest(
        @NotNull(message = "borrowerId is required")
        UUID borrowerId,

        @NotNull(message = "requestedAmount is required")
        @DecimalMin(value = "0.01", message = "requestedAmount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "requestedAmount must have at most 2 decimals")
        BigDecimal requestedAmount,

        @NotNull(message = "termMonths is required")
        @Min(value = 1, message = "termMonths must be at least 1")
        @Max(value = 360, message = "termMonths must be at most 360")
        Integer termMonths,

        @NotBlank(message = "purpose is required")
        @Size(max = 200, message = "purpose must be at most 200 characters")
        String purpose,

        @NotNull(message = "status is required")
        LoanApplicationStatus status) {
}
