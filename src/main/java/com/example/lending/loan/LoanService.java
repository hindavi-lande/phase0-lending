package com.example.lending.loan;

import com.example.lending.common.ResourceNotFoundException;
import com.example.lending.loan.RepaymentScheduleCalculator.ScheduleRow;
import com.example.lending.loan.dto.LoanResponse;
import com.example.lending.loanapplication.LoanApplication;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LoanService {

    private final LoanRepository loanRepository;
    private final InstallmentRepository installmentRepository;
    private final RepaymentScheduleCalculator repaymentScheduleCalculator;

    public LoanService(
            LoanRepository loanRepository,
            InstallmentRepository installmentRepository,
            RepaymentScheduleCalculator repaymentScheduleCalculator) {
        this.loanRepository = loanRepository;
        this.installmentRepository = installmentRepository;
        this.repaymentScheduleCalculator = repaymentScheduleCalculator;
    }

    @Transactional(readOnly = true)
    public LoanResponse getById(UUID id) {
        Loan loan = loanRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + id));
        List<Installment> installments =
                installmentRepository.findAllByLoan_IdOrderBySequenceAsc(loan.getId());
        return LoanResponse.from(loan, installments);
    }

    /**
     * Creates the loan and its complete schedule from an already locked application. Callers are
     * expected to flip the application status in the same transaction, so a failure here rolls the
     * whole decision back.
     */
    public LoanResponse createLoan(
            LoanApplication application, BigDecimal annualInterestRate, LocalDate firstDueDate) {
        BigDecimal principal = application.getRequestedAmount().setScale(2, java.math.RoundingMode.HALF_UP);
        int termMonths = application.getTermMonths();

        List<ScheduleRow> schedule = repaymentScheduleCalculator.generate(
                principal, annualInterestRate, termMonths, firstDueDate);

        BigDecimal totalPayable = schedule.stream()
                .map(ScheduleRow::total)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal totalInterest = totalPayable.subtract(principal);

        Loan loan = new Loan(
                application,
                principal,
                annualInterestRate.setScale(2, java.math.RoundingMode.HALF_UP),
                termMonths,
                schedule.get(0).total(),
                totalPayable,
                totalInterest,
                firstDueDate,
                schedule.get(schedule.size() - 1).dueDate(),
                LoanStatus.PENDING_DISBURSEMENT);

        Loan persisted = loanRepository.saveAndFlush(loan);

        List<Installment> installments = schedule.stream()
                .map(row -> new Installment(
                        persisted,
                        row.sequence(),
                        row.dueDate(),
                        row.openingBalance(),
                        row.principal(),
                        row.interest(),
                        row.total(),
                        row.closingBalance()))
                .toList();

        List<Installment> savedInstallments = installmentRepository.saveAllAndFlush(installments)
                .stream()
                .sorted(Comparator.comparingInt(Installment::getSequence))
                .toList();
        return LoanResponse.from(persisted, savedInstallments);
    }
}
