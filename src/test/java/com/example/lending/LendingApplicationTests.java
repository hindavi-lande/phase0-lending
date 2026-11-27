package com.example.lending;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lending.borrower.BorrowerController;
import com.example.lending.loan.LoanController;
import com.example.lending.loanapplication.LoanApplicationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LendingApplicationTests {

    @Autowired
    private BorrowerController borrowerController;

    @Autowired
    private LoanApplicationController loanApplicationController;

    @Autowired
    private LoanController loanController;

    @Test
    void contextLoadsWithBothSlices() {
        assertThat(borrowerController).isNotNull();
        assertThat(loanApplicationController).isNotNull();
        assertThat(loanController).isNotNull();
    }
}
