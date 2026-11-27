package com.example.lending.loan;

import com.example.lending.loan.dto.LoanResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of a loan. There is deliberately no update or delete mapping: a loan and its
 * schedule are fixed at approval time.
 */
@RestController
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping("/{id}")
    public LoanResponse get(@PathVariable UUID id) {
        return loanService.getById(id);
    }
}
