package com.example.lending.borrower.dto;

import com.example.lending.borrower.BorrowerStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BorrowerRequest(
        @NotBlank(message = "firstName is required")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        String firstName,

        @NotBlank(message = "lastName is required")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        String lastName,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        @NotBlank(message = "phone is required")
        @Size(max = 20, message = "phone must be at most 20 characters")
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "phone must be a valid phone number")
        String phone,

        @NotNull(message = "dateOfBirth is required")
        @Past(message = "dateOfBirth must be in the past")
        LocalDate dateOfBirth,

        @NotNull(message = "annualIncome is required")
        @DecimalMin(value = "0.00", message = "annualIncome must not be negative")
        @Digits(integer = 17, fraction = 2, message = "annualIncome must have at most 2 decimals")
        BigDecimal annualIncome,

        @NotNull(message = "status is required")
        BorrowerStatus status) {
}
