package com.example.lending.loanapplication;

import com.example.lending.loan.dto.LoanResponse;
import com.example.lending.loanapplication.dto.ApprovalRequest;
import com.example.lending.loanapplication.dto.LoanApplicationRequest;
import com.example.lending.loanapplication.dto.LoanApplicationResponse;
import com.example.lending.loanapplication.dto.RejectionRequest;
import com.example.lending.loanapplication.dto.ReviewRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loan-applications")
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;

    public LoanApplicationController(LoanApplicationService loanApplicationService) {
        this.loanApplicationService = loanApplicationService;
    }

    @PostMapping
    public ResponseEntity<LoanApplicationResponse> create(
            @Valid @RequestBody LoanApplicationRequest request) {
        LoanApplicationResponse created = loanApplicationService.create(request);
        return ResponseEntity.created(URI.create("/api/loan-applications/" + created.id())).body(created);
    }

    @GetMapping
    public List<LoanApplicationResponse> list(@RequestParam(required = false) UUID borrowerId) {
        return loanApplicationService.list(borrowerId);
    }

    @GetMapping("/{id}")
    public LoanApplicationResponse get(@PathVariable UUID id) {
        return loanApplicationService.getById(id);
    }

    @PutMapping("/{id}")
    public LoanApplicationResponse update(
            @PathVariable UUID id, @Valid @RequestBody LoanApplicationRequest request) {
        return loanApplicationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        loanApplicationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/review")
    public LoanApplicationResponse review(
            @PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return loanApplicationService.startReview(id, request);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<LoanResponse> approve(
            @PathVariable UUID id, @Valid @RequestBody ApprovalRequest request) {
        LoanResponse loan = loanApplicationService.approve(id, request);
        return ResponseEntity.created(URI.create("/api/loans/" + loan.loanId())).body(loan);
    }

    @PostMapping("/{id}/reject")
    public LoanApplicationResponse reject(
            @PathVariable UUID id, @Valid @RequestBody RejectionRequest request) {
        return loanApplicationService.reject(id, request);
    }
}
