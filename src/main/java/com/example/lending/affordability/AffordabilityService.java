package com.example.lending.affordability;

import com.example.lending.affordability.dto.AffordabilityPreCheckRequest;
import com.example.lending.affordability.dto.AffordabilityPreCheckResponse;
import com.example.lending.borrower.Borrower;
import com.example.lending.loanapplication.LoanApplication;
import com.example.lending.loanapplication.LoanApplicationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only affordability pre-check. It resolves an existing application and calculates
 * from the persisted borrower income and requested terms; nothing is saved or mutated.
 */
@Service
@Transactional(readOnly = true)
public class AffordabilityService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final LoanApplicationService loanApplicationService;
    private final AffordabilityCalculator calculator;
    private final AffordabilityProperties properties;

    public AffordabilityService(
            LoanApplicationService loanApplicationService,
            AffordabilityCalculator calculator,
            AffordabilityProperties properties) {
        this.loanApplicationService = loanApplicationService;
        this.calculator = calculator;
        this.properties = properties;
    }

    public AffordabilityPreCheckResponse preCheck(
            UUID applicationId, AffordabilityPreCheckRequest request) {

        LoanApplication application = loanApplicationService.findOrThrow(applicationId);
        Borrower borrower = application.getBorrower();

        BigDecimal annualInterestRate = request == null || request.annualInterestRate() == null
                ? properties.getDefaultAnnualInterestRate()
                : request.annualInterestRate();
        BigDecimal existingMonthlyObligations = request == null
                || request.existingMonthlyObligations() == null
                        ? ZERO
                        : request.existingMonthlyObligations();

        BigDecimal rate = annualInterestRate.setScale(MONEY_SCALE, ROUNDING);
        BigDecimal obligations = existingMonthlyObligations.setScale(MONEY_SCALE, ROUNDING);

        AffordabilityCalculator.Result result = calculator.calculate(
                application.getRequestedAmount(),
                application.getTermMonths(),
                rate,
                borrower.getAnnualIncome(),
                obligations);

        BigDecimal preferredMaxDti = properties.getPreferredMaxDti().setScale(MONEY_SCALE, ROUNDING);
        BigDecimal hardMaxDti = properties.getHardMaxDti().setScale(MONEY_SCALE, ROUNDING);

        return new AffordabilityPreCheckResponse(
                application.getId(),
                borrower.getId(),
                application.getRequestedAmount().setScale(MONEY_SCALE, ROUNDING),
                application.getTermMonths(),
                rate,
                result.monthlyIncome(),
                result.estimatedMonthlyInstalment(),
                obligations,
                result.totalMonthlyOutgo(),
                result.totalPayable(),
                result.totalInterest(),
                result.debtToIncomeRatio(),
                result.decision(),
                result.reasons(),
                new AffordabilityPreCheckResponse.DtiThresholds(preferredMaxDti, hardMaxDti));
    }
}
