package com.example.lending.loan;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallmentRepository extends JpaRepository<Installment, UUID> {

    List<Installment> findAllByLoan_IdOrderBySequenceAsc(UUID loanId);
}
