package com.example.lending.loan;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    Optional<Loan> findById(UUID id);

    boolean existsByApplication_Id(UUID applicationId);

    Optional<Loan> findByApplication_Id(UUID applicationId);
}
