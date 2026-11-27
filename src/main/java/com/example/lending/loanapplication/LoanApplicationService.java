package com.example.lending.loanapplication;

import com.example.lending.borrower.Borrower;
import com.example.lending.borrower.BorrowerService;
import com.example.lending.common.ApplicationStateException;
import com.example.lending.common.RequestValidationException;
import com.example.lending.common.ResourceNotFoundException;
import com.example.lending.loan.LoanRepository;
import com.example.lending.loan.LoanService;
import com.example.lending.loan.dto.LoanResponse;
import com.example.lending.loanapplication.dto.ApprovalRequest;
import com.example.lending.loanapplication.dto.LoanApplicationRequest;
import com.example.lending.loanapplication.dto.LoanApplicationResponse;
import com.example.lending.loanapplication.dto.RejectionRequest;
import com.example.lending.loanapplication.dto.ReviewRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LoanApplicationService {

    private static final String ALREADY_DECIDED = "Application has already been decided";
    private static final String DECIDED_CANNOT_BE_MODIFIED = "Decided applications cannot be modified";

    private final LoanApplicationRepository loanApplicationRepository;
    private final BorrowerService borrowerService;
    private final LoanRepository loanRepository;
    private final LoanService loanService;

    public LoanApplicationService(
            LoanApplicationRepository loanApplicationRepository,
            BorrowerService borrowerService,
            LoanRepository loanRepository,
            LoanService loanService) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.borrowerService = borrowerService;
        this.loanRepository = loanRepository;
        this.loanService = loanService;
    }

    public LoanApplicationResponse create(LoanApplicationRequest request) {
        Borrower borrower = borrowerService.findOrThrow(request.borrowerId());

        LoanApplication application = new LoanApplication(
                borrower,
                request.requestedAmount(),
                request.termMonths(),
                request.purpose(),
                request.status());

        return LoanApplicationResponse.from(loanApplicationRepository.save(application));
    }

    @Transactional(readOnly = true)
    public LoanApplicationResponse getById(UUID id) {
        return LoanApplicationResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<LoanApplicationResponse> list(UUID borrowerId) {
        List<LoanApplication> applications = borrowerId == null
                ? loanApplicationRepository.findAll()
                : loanApplicationRepository.findAllByBorrower_Id(borrowerId);

        return applications.stream().map(LoanApplicationResponse::from).toList();
    }

    public LoanApplicationResponse update(UUID id, LoanApplicationRequest request) {
        LoanApplication application = findOrThrow(id);

        if (isDecided(application.getStatus())) {
            throw new ApplicationStateException(DECIDED_CANNOT_BE_MODIFIED);
        }

        if (request.status() != application.getStatus()
                && !(application.getStatus() == LoanApplicationStatus.SUBMITTED
                        && request.status() == LoanApplicationStatus.UNDER_REVIEW)) {
            throw invalidTransition(application.getStatus(), request.status());
        }

        Borrower borrower = borrowerService.findOrThrow(request.borrowerId());

        application.setBorrower(borrower);
        application.setRequestedAmount(request.requestedAmount());
        application.setTermMonths(request.termMonths());
        application.setPurpose(request.purpose());
        application.setStatus(request.status());

        return LoanApplicationResponse.from(loanApplicationRepository.save(application));
    }

    public void delete(UUID id) {
        loanApplicationRepository.delete(findOrThrow(id));
    }

    public LoanApplicationResponse startReview(UUID id, ReviewRequest request) {
        LoanApplication application = findForUpdateOrThrow(id);
        rejectDecided(application);

        if (application.getStatus() != LoanApplicationStatus.SUBMITTED) {
            throw invalidTransition(application.getStatus(), LoanApplicationStatus.UNDER_REVIEW);
        }

        application.setStatus(LoanApplicationStatus.UNDER_REVIEW);
        application.setReviewerId(request.reviewerId());
        application.setReviewedAt(Instant.now());

        return LoanApplicationResponse.from(loanApplicationRepository.save(application));
    }

    public LoanResponse approve(UUID id, ApprovalRequest request) {
        LoanApplication application = findForUpdateOrThrow(id);
        rejectDecided(application);

        if (application.getStatus() != LoanApplicationStatus.UNDER_REVIEW) {
            throw invalidTransition(application.getStatus(), LoanApplicationStatus.APPROVED);
        }

        validateFirstDueDate(request.firstDueDate());

        if (loanRepository.existsByApplication_Id(application.getId())) {
            throw new ApplicationStateException(ALREADY_DECIDED);
        }

        LoanResponse loan;
        try {
            loan = loanService.createLoan(
                    application, request.annualInterestRate(), request.firstDueDate());
        } catch (DataIntegrityViolationException ex) {
            throw new ApplicationStateException(ALREADY_DECIDED);
        }

        Instant now = Instant.now();
        application.setStatus(LoanApplicationStatus.APPROVED);
        application.setReviewerId(request.reviewerId());
        application.setDecidedAt(now);
        application.setDecisionNote(request.note());
        if (application.getReviewedAt() == null) {
            application.setReviewedAt(now);
        }

        loanApplicationRepository.save(application);
        return loan;
    }

    public LoanApplicationResponse reject(UUID id, RejectionRequest request) {
        LoanApplication application = findForUpdateOrThrow(id);
        rejectDecided(application);

        if (application.getStatus() != LoanApplicationStatus.UNDER_REVIEW) {
            throw invalidTransition(application.getStatus(), LoanApplicationStatus.REJECTED);
        }

        application.setStatus(LoanApplicationStatus.REJECTED);
        application.setReviewerId(request.reviewerId());
        application.setDecidedAt(Instant.now());
        application.setDecisionReason(request.reason());

        return LoanApplicationResponse.from(loanApplicationRepository.save(application));
    }

    private void validateFirstDueDate(LocalDate firstDueDate) {
        LocalDate today = LocalDate.now();
        LocalDate earliest = today.plusDays(1);
        LocalDate latest = today.plusDays(90);

        if (firstDueDate.isBefore(earliest) || firstDueDate.isAfter(latest)) {
            throw new RequestValidationException(
                    "firstDueDate", "firstDueDate must be between 1 and 90 days in the future");
        }
    }

    private void rejectDecided(LoanApplication application) {
        if (isDecided(application.getStatus())) {
            throw new ApplicationStateException(ALREADY_DECIDED);
        }
    }

    private static boolean isDecided(LoanApplicationStatus status) {
        return status == LoanApplicationStatus.APPROVED || status == LoanApplicationStatus.REJECTED;
    }

    private static ApplicationStateException invalidTransition(
            LoanApplicationStatus current, LoanApplicationStatus target) {
        return new ApplicationStateException(
                "Application status cannot change from " + current + " to " + target);
    }

    private LoanApplication findOrThrow(UUID id) {
        return loanApplicationRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found: " + id));
    }

    private LoanApplication findForUpdateOrThrow(UUID id) {
        return loanApplicationRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found: " + id));
    }
}
