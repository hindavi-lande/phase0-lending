package com.example.lending.loanapplication;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    List<LoanApplication> findAllByBorrower_Id(UUID borrowerId);

    boolean existsByBorrower_Id(UUID borrowerId);

    /**
     * Locks the application row for the duration of a decision transaction, so concurrent decisions
     * serialise and the loser observes the already-decided state instead of writing a second loan.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from LoanApplication application where application.id = :id")
    Optional<LoanApplication> findByIdForUpdate(@Param("id") UUID id);
}
