package com.example.lending.loanapplication;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    List<LoanApplication> findAllByBorrower_Id(UUID borrowerId);

    boolean existsByBorrower_Id(UUID borrowerId);
}
