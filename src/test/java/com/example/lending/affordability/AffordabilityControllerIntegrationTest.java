package com.example.lending.affordability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lending.borrower.Borrower;
import com.example.lending.borrower.BorrowerRepository;
import com.example.lending.borrower.BorrowerStatus;
import com.example.lending.loanapplication.LoanApplication;
import com.example.lending.loanapplication.LoanApplicationRepository;
import com.example.lending.loanapplication.LoanApplicationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AffordabilityControllerIntegrationTest {

    private static final String PRE_CHECK = "/api/loan-applications/{id}/affordability-pre-check";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    @BeforeEach
    void clean() {
        loanApplicationRepository.deleteAll();
        borrowerRepository.deleteAll();
    }

    @Test
    void defaultRateMatchesTicketExample() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com", "120000.00");
        String applicationId = createApplication(borrowerId, "25000.00", 24);

        mockMvc.perform(post(PRE_CHECK, applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "existingMonthlyObligations": 1500.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.borrowerId").value(borrowerId.toString()))
                .andExpect(jsonPath("$.requestedAmount").value(25000.00))
                .andExpect(jsonPath("$.termMonths").value(24))
                .andExpect(jsonPath("$.annualInterestRate").value(12.50))
                .andExpect(jsonPath("$.monthlyIncome").value(10000.00))
                .andExpect(jsonPath("$.estimatedMonthlyInstalment").value(1182.68))
                .andExpect(jsonPath("$.existingMonthlyObligations").value(1500.00))
                .andExpect(jsonPath("$.totalMonthlyOutgo").value(2682.68))
                .andExpect(jsonPath("$.totalPayable").value(28384.32))
                .andExpect(jsonPath("$.totalInterest").value(3384.32))
                .andExpect(jsonPath("$.debtToIncomeRatio").value(26.83))
                .andExpect(jsonPath("$.decision").value("ELIGIBLE"))
                .andExpect(jsonPath("$.reasons", hasSize(1)))
                .andExpect(jsonPath("$.reasons[0]").value("WITHIN_AFFORDABILITY_LIMIT"))
                .andExpect(jsonPath("$.thresholds.preferredMaxDti").value(40.00))
                .andExpect(jsonPath("$.thresholds.hardMaxDti").value(50.00));
    }

    @Test
    void rateCanBeOverriddenAndObligationsDefaultToZero() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com", "120000.00");
        String applicationId = createApplication(borrowerId, "25000.00", 24);

        mockMvc.perform(post(PRE_CHECK, applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "annualInterestRate": 0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualInterestRate").value(0.00))
                .andExpect(jsonPath("$.estimatedMonthlyInstalment").value(1041.67))
                .andExpect(jsonPath("$.existingMonthlyObligations").value(0.00))
                .andExpect(jsonPath("$.totalMonthlyOutgo").value(1041.67))
                .andExpect(jsonPath("$.totalPayable").value(25000.08))
                .andExpect(jsonPath("$.totalInterest").value(0.08))
                .andExpect(jsonPath("$.debtToIncomeRatio").value(10.42))
                .andExpect(jsonPath("$.decision").value("ELIGIBLE"));
    }

    @Test
    void allThreeDecisionBandsAreReachable() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com", "120000.00");
        String applicationId = createApplication(borrowerId, "25000.00", 24);

        // Outgo 2682.68 => DTI 26.83.
        mockMvc.perform(post(PRE_CHECK, applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"existingMonthlyObligations\": 1500.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("ELIGIBLE"))
                .andExpect(jsonPath("$.reasons[0]").value("WITHIN_AFFORDABILITY_LIMIT"));

        // Outgo 4500.00 => DTI 45.00.
        mockMvc.perform(post(PRE_CHECK, applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"existingMonthlyObligations\": 3317.32}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtToIncomeRatio").value(45.00))
                .andExpect(jsonPath("$.decision").value("REFERRED"))
                .andExpect(jsonPath("$.reasons[0]").value("ABOVE_PREFERRED_LIMIT"));

        // Outgo 5182.68 => DTI 51.83.
        mockMvc.perform(post(PRE_CHECK, applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"existingMonthlyObligations\": 4000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtToIncomeRatio").value(51.83))
                .andExpect(jsonPath("$.decision").value("INELIGIBLE"))
                .andExpect(jsonPath("$.reasons[0]").value("ABOVE_MAXIMUM_LIMIT"));
    }

    @Test
    void zeroIncomeIsIneligibleRatherThanAnError() throws Exception {
        UUID borrowerId = persistBorrower("zero@example.com", "0.00");
        String applicationId = createApplication(borrowerId, "25000.00", 24);

        mockMvc.perform(post(PRE_CHECK, applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyIncome").value(0.00))
                .andExpect(jsonPath("$.debtToIncomeRatio").value(nullValue()))
                .andExpect(jsonPath("$.decision").value("INELIGIBLE"))
                .andExpect(jsonPath("$.reasons[0]").value("INCOME_NOT_POSITIVE"));
    }

    @Test
    void unknownApplicationReturns404WithExactMessage() throws Exception {
        String missingId = "11111111-1111-1111-1111-111111111111";

        mockMvc.perform(post(PRE_CHECK, missingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Loan application not found: " + missingId));
    }

    @Test
    void rateAboveThirtySixReturns400WithFieldError() throws Exception {
        String applicationId = existingApplication();

        mockMvc.perform(post(PRE_CHECK, applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualInterestRate\": 36.01}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.annualInterestRate")
                        .value("annualInterestRate must be between 0 and 36"));
    }

    @Test
    void negativeRateReturns400WithFieldError() throws Exception {
        String applicationId = existingApplication();

        mockMvc.perform(post(PRE_CHECK, applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"annualInterestRate\": -0.01}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.annualInterestRate")
                        .value("annualInterestRate must be between 0 and 36"));
    }

    @Test
    void negativeObligationsReturns400WithFieldError() throws Exception {
        String applicationId = existingApplication();

        mockMvc.perform(post(PRE_CHECK, applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"existingMonthlyObligations\": -0.01}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.existingMonthlyObligations")
                        .value("existingMonthlyObligations must not be negative"));
    }

    @Test
    void obligationsWithMoreThanTwoDecimalsReturns400WithFieldError() throws Exception {
        String applicationId = existingApplication();

        mockMvc.perform(post(PRE_CHECK, applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"existingMonthlyObligations\": 10.005}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.existingMonthlyObligations")
                        .value("existingMonthlyObligations must have at most 2 decimals"));
    }

    @Test
    void repeatedCallsReturnIdenticalJsonAndWriteNothing() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com", "120000.00");
        String applicationId = createApplication(borrowerId, "25000.00", 24);
        UUID applicationUuid = UUID.fromString(applicationId);

        long borrowerCount = borrowerRepository.count();
        long applicationCount = loanApplicationRepository.count();

        Borrower borrowerBefore = borrowerRepository.findById(borrowerId).orElseThrow();
        String emailBefore = borrowerBefore.getEmail();
        BigDecimal incomeBefore = borrowerBefore.getAnnualIncome();

        LoanApplication applicationBefore = loanApplicationRepository.findById(applicationUuid).orElseThrow();
        BigDecimal amountBefore = applicationBefore.getRequestedAmount();
        int termBefore = applicationBefore.getTermMonths();
        String purposeBefore = applicationBefore.getPurpose();
        LoanApplicationStatus statusBefore = applicationBefore.getStatus();
        Instant submittedAtBefore = applicationBefore.getSubmittedAt();

        String requestBody = "{\"existingMonthlyObligations\": 1500.00}";
        String first = preCheckJson(applicationId, requestBody);
        String second = preCheckJson(applicationId, requestBody);

        assertThat(second).isEqualTo(first);

        assertThat(borrowerRepository.count()).isEqualTo(borrowerCount);
        assertThat(loanApplicationRepository.count()).isEqualTo(applicationCount);

        Borrower borrowerAfter = borrowerRepository.findById(borrowerId).orElseThrow();
        assertThat(borrowerAfter.getEmail()).isEqualTo(emailBefore);
        assertThat(borrowerAfter.getAnnualIncome()).isEqualByComparingTo(incomeBefore);

        LoanApplication applicationAfter = loanApplicationRepository.findById(applicationUuid).orElseThrow();
        assertThat(applicationAfter.getRequestedAmount()).isEqualByComparingTo(amountBefore);
        assertThat(applicationAfter.getTermMonths()).isEqualTo(termBefore);
        assertThat(applicationAfter.getPurpose()).isEqualTo(purposeBefore);
        assertThat(applicationAfter.getStatus()).isEqualTo(statusBefore);
        assertThat(applicationAfter.getSubmittedAt()).isEqualTo(submittedAtBefore);
    }

    private String existingApplication() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com", "120000.00");
        return createApplication(borrowerId, "25000.00", 24);
    }

    private String preCheckJson(String applicationId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(PRE_CHECK, applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private UUID persistBorrower(String email, String annualIncome) {
        Borrower borrower = new Borrower(
                "Ada",
                "Lovelace",
                email,
                "+44 20 7946 0958",
                LocalDate.of(1990, 1, 1),
                new BigDecimal(annualIncome),
                BorrowerStatus.ACTIVE);
        return borrowerRepository.save(borrower).getId();
    }

    private String createApplication(UUID borrowerId, String amount, int term) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowerId": "%s",
                                  "requestedAmount": %s,
                                  "termMonths": %d,
                                  "purpose": "Home renovation",
                                  "status": "SUBMITTED"
                                }
                                """.formatted(borrowerId, amount, term)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }
}
