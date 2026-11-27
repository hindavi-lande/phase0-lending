package com.example.lending.loanapplication.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ApprovalRequest(
        @NotBlank(message = "reviewerId is required")
        @Size(min = 1, max = 100, message = "reviewerId must be between 1 and 100 characters")
        String reviewerId,

        @NotNull(message = "annualInterestRate is required")
        @DecimalMin(value = "0.00", message = "annualInterestRate must be at least 0.00")
        @DecimalMax(value = "36.00", message = "annualInterestRate must be at most 36.00")
        @Digits(integer = 2, fraction = 2, message = "annualInterestRate must have at most 2 decimals")
        BigDecimal annualInterestRate,

        @NotNull(message = "firstDueDate is required")
        LocalDate firstDueDate,

        @Size(max = 200, message = "note must be at most 200 characters")
        String note) {
}
