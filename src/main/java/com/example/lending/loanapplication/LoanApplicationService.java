package com.example.lending.loanapplication;

import com.example.lending.borrower.Borrower;
import com.example.lending.borrower.BorrowerService;
import com.example.lending.common.ResourceNotFoundException;
import com.example.lending.loanapplication.dto.LoanApplicationRequest;
import com.example.lending.loanapplication.dto.LoanApplicationResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LoanApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final BorrowerService borrowerService;

    public LoanApplicationService(
            LoanApplicationRepository loanApplicationRepository,
            BorrowerService borrowerService) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.borrowerService = borrowerService;
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

    private LoanApplication findOrThrow(UUID id) {
        return loanApplicationRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found: " + id));
    }
}
