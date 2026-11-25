package com.example.lending.borrower.dto;

import com.example.lending.borrower.Borrower;
import com.example.lending.borrower.BorrowerStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BorrowerResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        BigDecimal annualIncome,
        BorrowerStatus status) {

    public static BorrowerResponse from(Borrower borrower) {
        return new BorrowerResponse(
                borrower.getId(),
                borrower.getFirstName(),
                borrower.getLastName(),
                borrower.getEmail(),
                borrower.getPhone(),
                borrower.getDateOfBirth(),
                borrower.getAnnualIncome(),
                borrower.getStatus());
    }
}
