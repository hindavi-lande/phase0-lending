package com.example.lending.loanapplication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectionRequest(
        @NotBlank(message = "reviewerId is required")
        @Size(min = 1, max = 100, message = "reviewerId must be between 1 and 100 characters")
        String reviewerId,

        @NotBlank(message = "reason is required")
        @Size(min = 1, max = 200, message = "reason must be between 1 and 200 characters")
        String reason) {
}
