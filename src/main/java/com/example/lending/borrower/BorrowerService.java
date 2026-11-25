package com.example.lending.borrower;

import com.example.lending.common.BorrowerHasApplicationsException;
import com.example.lending.common.DuplicateResourceException;
import com.example.lending.common.ResourceNotFoundException;
import com.example.lending.borrower.dto.BorrowerRequest;
import com.example.lending.borrower.dto.BorrowerResponse;
import com.example.lending.loanapplication.LoanApplicationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BorrowerService {

    private final BorrowerRepository borrowerRepository;
    private final LoanApplicationRepository loanApplicationRepository;

    public BorrowerService(
            BorrowerRepository borrowerRepository,
            LoanApplicationRepository loanApplicationRepository) {
        this.borrowerRepository = borrowerRepository;
        this.loanApplicationRepository = loanApplicationRepository;
    }

    public BorrowerResponse create(BorrowerRequest request) {
        if (borrowerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Borrower email is already in use: " + request.email());
        }

        Borrower borrower = new Borrower(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.phone(),
                request.dateOfBirth(),
                request.annualIncome(),
                request.status());

        return BorrowerResponse.from(borrowerRepository.save(borrower));
    }

    @Transactional(readOnly = true)
    public BorrowerResponse getById(UUID id) {
        return BorrowerResponse.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<BorrowerResponse> list() {
        return borrowerRepository.findAll().stream().map(BorrowerResponse::from).toList();
    }

    public BorrowerResponse update(UUID id, BorrowerRequest request) {
        Borrower borrower = findOrThrow(id);

        if (borrowerRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new DuplicateResourceException("Borrower email is already in use: " + request.email());
        }

        borrower.setFirstName(request.firstName());
        borrower.setLastName(request.lastName());
        borrower.setEmail(request.email());
        borrower.setPhone(request.phone());
        borrower.setDateOfBirth(request.dateOfBirth());
        borrower.setAnnualIncome(request.annualIncome());
        borrower.setStatus(request.status());

        return BorrowerResponse.from(borrowerRepository.save(borrower));
    }

    public void delete(UUID id) {
        Borrower borrower = findOrThrow(id);

        if (loanApplicationRepository.existsByBorrower_Id(id)) {
            throw new BorrowerHasApplicationsException(
                    "Borrower has loan applications and cannot be deleted: " + id);
        }

        borrowerRepository.delete(borrower);
    }

    /**
     * Resolves a borrower for another slice, so unknown ids surface as a 404 instead of an
     * opaque foreign-key failure.
     */
    @Transactional(readOnly = true)
    public Borrower findOrThrow(UUID id) {
        return borrowerRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Borrower not found: " + id));
    }
}
