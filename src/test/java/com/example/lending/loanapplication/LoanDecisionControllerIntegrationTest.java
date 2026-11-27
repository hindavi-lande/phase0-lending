package com.example.lending.loanapplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lending.borrower.Borrower;
import com.example.lending.borrower.BorrowerRepository;
import com.example.lending.borrower.BorrowerStatus;
import com.example.lending.loan.InstallmentRepository;
import com.example.lending.loan.LoanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanDecisionControllerIntegrationTest {

    private static final String ALREADY_DECIDED = "Application has already been decided";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    @Autowired
    private LoanRepository loanRepository;

    @MockitoSpyBean
    private InstallmentRepository installmentRepository;

    @BeforeEach
    void clean() {
        deleteAllData();
    }

    @AfterEach
    void tearDown() {
        deleteAllData();
    }

    private void deleteAllData() {
        installmentRepository.deleteAll();
        loanRepository.deleteAll();
        loanApplicationRepository.deleteAll();
        borrowerRepository.deleteAll();
    }

    @Test
    void reviewRecordsReviewerAndMovesApplicationToUnderReview() throws Exception {
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);

        mockMvc.perform(post("/api/loan-applications/{id}/review", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("officer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.reviewerId").value("officer-1"))
                .andExpect(jsonPath("$.reviewedAt").exists());

        mockMvc.perform(get("/api/loan-applications/{id}", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.reviewerId").value("officer-1"));
    }

    @Test
    void reviewOnApplicationAlreadyUnderReviewReturns409() throws Exception {
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());

        mockMvc.perform(post("/api/loan-applications/{id}/review", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("officer-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("Application status cannot change from UNDER_REVIEW to UNDER_REVIEW"));
    }

    @Test
    void approveCreatesLoanAndPersistsDecision() throws Exception {
        LocalDate firstDueDate = LocalDate.now().plusDays(30);
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());

        MvcResult approval = mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson("officer-2", "12.50", firstDueDate, "Approved by committee")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/loans/")))
                .andExpect(jsonPath("$.loanId").exists())
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.principal").value(25000.00))
                .andExpect(jsonPath("$.annualInterestRate").value(12.50))
                .andExpect(jsonPath("$.termMonths").value(24))
                .andExpect(jsonPath("$.monthlyInstalment").value(1182.68))
                .andExpect(jsonPath("$.totalPayable").value(28384.37))
                .andExpect(jsonPath("$.totalInterest").value(3384.37))
                .andExpect(jsonPath("$.firstDueDate").value(firstDueDate.toString()))
                .andExpect(jsonPath("$.maturityDate").value(firstDueDate.plusMonths(23).toString()))
                .andExpect(jsonPath("$.status").value("PENDING_DISBURSEMENT"))
                .andExpect(jsonPath("$.schedule", hasSize(24)))
                .andExpect(jsonPath("$.schedule[0].sequence").value(1))
                .andExpect(jsonPath("$.schedule[23].sequence").value(24))
                .andExpect(jsonPath("$.schedule[23].total").value(1182.73))
                .andReturn();

        String loanId = objectMapper.readTree(approval.getResponse().getContentAsString())
                .get("loanId")
                .asText();

        mockMvc.perform(get("/api/loans/{id}", loanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.schedule", hasSize(24)))
                .andExpect(jsonPath("$.schedule[23].closingBalance").value(0.00));

        mockMvc.perform(get("/api/loan-applications/{id}", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewerId").value("officer-2"))
                .andExpect(jsonPath("$.decidedAt").exists())
                .andExpect(jsonPath("$.decisionNote").value("Approved by committee"));

        assertThat(loanRepository.count()).isEqualTo(1);
        assertThat(installmentRepository.count()).isEqualTo(24);
    }

    @Test
    void approvingTwiceReturns409AndKeepsOneLoan() throws Exception {
        LocalDate firstDueDate = LocalDate.now().plusDays(30);
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());

        mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson("officer-2", "12.50", firstDueDate, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson("officer-2", "12.50", firstDueDate, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ALREADY_DECIDED));

        assertThat(loanRepository.count()).isEqualTo(1);
        assertThat(installmentRepository.count()).isEqualTo(24);
    }

    @Test
    void approveBeforeReviewReturns409InvalidTransition() throws Exception {
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);

        mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson(
                                "officer-2", "12.50", LocalDate.now().plusDays(30), null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Application status cannot change from SUBMITTED to APPROVED"));
    }

    @Test
    void rejectRecordsDecisionAndMovesApplicationToRejected() throws Exception {
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());

        mockMvc.perform(post("/api/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectionJson("officer-3", "Insufficient income")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewerId").value("officer-3"))
                .andExpect(jsonPath("$.decidedAt").exists())
                .andExpect(jsonPath("$.decisionReason").value("Insufficient income"));

        assertThat(loanRepository.count()).isZero();
        assertThat(installmentRepository.count()).isZero();
    }

    @Test
    void rejectBeforeReviewReturns409InvalidTransition() throws Exception {
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);

        mockMvc.perform(post("/api/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectionJson("officer-3", "Insufficient income")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Application status cannot change from SUBMITTED to REJECTED"));
    }

    @Test
    void decisionsOnRejectedApplicationReturn409AlreadyDecided() throws Exception {
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());

        mockMvc.perform(post("/api/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectionJson("officer-3", "Insufficient income")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson(
                                "officer-2", "12.50", LocalDate.now().plusDays(30), null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ALREADY_DECIDED));

        mockMvc.perform(post("/api/loan-applications/{id}/review", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("officer-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ALREADY_DECIDED));

        mockMvc.perform(post("/api/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectionJson("officer-3", "Insufficient income")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ALREADY_DECIDED));
    }

    @Test
    void decisionsOnApprovedApplicationReturn409AlreadyDecided() throws Exception {
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());

        mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson(
                                "officer-2", "12.50", LocalDate.now().plusDays(30), null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectionJson("officer-3", "Insufficient income")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ALREADY_DECIDED));

        mockMvc.perform(post("/api/loan-applications/{id}/review", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("officer-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ALREADY_DECIDED));
    }

    @Test
    void approvalValidationReturns400WithNamedFields() throws Exception {
        LocalDate validDueDate = LocalDate.now().plusDays(30);
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());

        postApprove(applicationId, approvalJson("officer-2", "36.01", validDueDate, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.annualInterestRate")
                        .value("annualInterestRate must be at most 36.00"));

        postApprove(applicationId, approvalJson("officer-2", "-0.01", validDueDate, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.annualInterestRate")
                        .value("annualInterestRate must be at least 0.00"));

        postApprove(applicationId, approvalJson("officer-2", "12.50", LocalDate.now(), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.firstDueDate")
                        .value("firstDueDate must be between 1 and 90 days in the future"));

        postApprove(applicationId, approvalJson("officer-2", "12.50", LocalDate.now().plusDays(91), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstDueDate").exists());

        postApprove(applicationId, approvalJson("officer-2", "12.50", validDueDate, "x".repeat(201)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.note")
                        .value("note must be at most 200 characters"));

        postApprove(applicationId, approvalJson(" ", "12.50", validDueDate, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reviewerId").exists());
    }

    @Test
    void decisionReviewerValidationReturns400WithNamedField() throws Exception {
        LocalDate validDueDate = LocalDate.now().plusDays(30);
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);

        mockMvc.perform(post("/api/loan-applications/{id}/review", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(" ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reviewerId").exists());

        mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson(null, "12.50", validDueDate, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reviewerId").exists());
    }

    @Test
    void rejectionValidationReturns400WithNamedFields() throws Exception {
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());

        mockMvc.perform(post("/api/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectionJson("officer-3", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());

        mockMvc.perform(post("/api/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectionJson(" ", "Insufficient income")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reviewerId").exists());

        mockMvc.perform(post("/api/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectionJson("officer-3", "x".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason")
                        .value("reason must be between 1 and 200 characters"));
    }

    @Test
    void zeroRateApprovalHasNoInterestAndExactTotals() throws Exception {
        String applicationId = createApplication(persistBorrower("ada@example.com"), "1200.00", 12);
        review(applicationId, "officer-1").andExpect(status().isOk());

        mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson(
                                "officer-2", "0.00", LocalDate.now().plusDays(20), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.monthlyInstalment").value(100.00))
                .andExpect(jsonPath("$.totalPayable").value(1200.00))
                .andExpect(jsonPath("$.totalInterest").value(0.00))
                .andExpect(jsonPath("$.schedule", hasSize(12)))
                .andExpect(jsonPath("$.schedule[0].interest").value(0.00))
                .andExpect(jsonPath("$.schedule[0].principal").value(100.00))
                .andExpect(jsonPath("$.schedule[11].closingBalance").value(0.00));
    }

    @Test
    void finalInstalmentAbsorbsRoundingDifferences() throws Exception {
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());

        MvcResult approval = mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson(
                                "officer-2", "12.50", LocalDate.now().plusDays(30), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schedule[23].principal").value(1170.54))
                .andExpect(jsonPath("$.schedule[23].total").value(1182.73))
                .andReturn();

        JsonNode body = objectMapper.readTree(approval.getResponse().getContentAsString());
        BigDecimal principalSum = BigDecimal.ZERO;
        for (JsonNode row : body.get("schedule")) {
            principalSum = principalSum.add(row.get("principal").decimalValue());
        }
        assertThat(principalSum).isEqualByComparingTo("25000.00");
    }

    @Test
    void monthEndFirstDueDateClampsSubsequentDueDates() throws Exception {
        LocalDate firstDueDate = monthEndWithThirtyOneDaysWithinApprovalWindow();
        String applicationId = createApplication(persistBorrower("ada@example.com"), "300.00", 3);
        review(applicationId, "officer-1").andExpect(status().isOk());

        mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson("officer-2", "0.00", firstDueDate, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstDueDate").value(firstDueDate.toString()))
                .andExpect(jsonPath("$.maturityDate").value(firstDueDate.plusMonths(2).toString()))
                .andExpect(jsonPath("$.schedule[0].dueDate").value(firstDueDate.toString()))
                .andExpect(jsonPath("$.schedule[1].dueDate").value(firstDueDate.plusMonths(1).toString()))
                .andExpect(jsonPath("$.schedule[2].dueDate").value(firstDueDate.plusMonths(2).toString()));
    }

    @Test
    void updatingApprovedApplicationReturns409() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com");
        String applicationId = createApplication(borrowerId, "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());
        mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson(
                                "officer-2", "12.50", LocalDate.now().plusDays(30), null)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/loan-applications/{id}", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(borrowerId, "25000.00", 24, "APPROVED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Decided applications cannot be modified"));
    }

    @Test
    void updatingRejectedApplicationReturns409() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com");
        String applicationId = createApplication(borrowerId, "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());
        mockMvc.perform(post("/api/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectionJson("officer-3", "Insufficient income")))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/loan-applications/{id}", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(borrowerId, "25000.00", 24, "REJECTED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Decided applications cannot be modified"));
    }

    @Test
    void unknownApplicationIdsOnDecisionsReturn404() throws Exception {
        String unknown = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/loan-applications/{id}/review", unknown)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson("officer-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Loan application not found")));

        mockMvc.perform(post("/api/loan-applications/{id}/approve", unknown)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson(
                                "officer-2", "12.50", LocalDate.now().plusDays(30), null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Loan application not found")));

        mockMvc.perform(post("/api/loan-applications/{id}/reject", unknown)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectionJson("officer-3", "Insufficient income")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Loan application not found")));
    }

    @Test
    void concurrentApprovalsProduceOneLoanAndOneConflict() throws Exception {
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        String body = approvalJson("officer-2", "12.50", LocalDate.now().plusDays(30), null);

        Callable<MvcResult> approval = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
        };

        try {
            Future<MvcResult> first = executor.submit(approval);
            Future<MvcResult> second = executor.submit(approval);
            List<MvcResult> results = List.of(
                    first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            long created = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 201)
                    .count();
            List<MvcResult> conflicts = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 409)
                    .toList();

            assertThat(created).isEqualTo(1);
            assertThat(conflicts).hasSize(1);
            JsonNode error = objectMapper.readTree(
                    conflicts.get(0).getResponse().getContentAsString());
            assertThat(error.get("message").asText()).isEqualTo(ALREADY_DECIDED);

            assertThat(loanRepository.count()).isEqualTo(1);
            assertThat(installmentRepository.count()).isEqualTo(24);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void forcedInstallmentFailureLeavesApplicationUnderReviewAndWritesNothing() throws Exception {
        String applicationId = createApplication(persistBorrower("ada@example.com"), "25000.00", 24);
        review(applicationId, "officer-1").andExpect(status().isOk());

        Mockito.doThrow(new DataIntegrityViolationException("forced installment failure"))
                .when(installmentRepository)
                .saveAllAndFlush(any());

        mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approvalJson(
                                "officer-2", "12.50", LocalDate.now().plusDays(30), null)))
                .andExpect(status().isConflict());

        LoanApplication application = loanApplicationRepository.findById(UUID.fromString(applicationId))
                .orElseThrow();
        assertThat(application.getStatus()).isEqualTo(LoanApplicationStatus.UNDER_REVIEW);
        assertThat(loanRepository.count()).isZero();
        assertThat(installmentRepository.count()).isZero();
    }

    private ResultActions review(String id, String reviewerId) throws Exception {
        return mockMvc.perform(post("/api/loan-applications/{id}/review", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewJson(reviewerId)));
    }

    private ResultActions postApprove(String id, String body) throws Exception {
        return mockMvc.perform(post("/api/loan-applications/{id}/approve", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String createApplication(UUID borrowerId, String amount, int term) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(borrowerId, amount, term, "SUBMITTED")))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private UUID persistBorrower(String email) {
        Borrower borrower = new Borrower(
                "Ada",
                "Lovelace",
                email,
                "+44 20 7946 0958",
                LocalDate.of(1990, 1, 1),
                new BigDecimal("120000.00"),
                BorrowerStatus.ACTIVE);
        return borrowerRepository.save(borrower).getId();
    }

    private String readId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    private LocalDate monthEndWithThirtyOneDaysWithinApprovalWindow() {
        LocalDate latest = LocalDate.now().plusDays(90);
        LocalDate candidate = LocalDate.now().plusDays(1);
        while (!candidate.isAfter(latest)) {
            if (candidate.getDayOfMonth() == 31) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        return LocalDate.now().plusDays(30).with(TemporalAdjusters.lastDayOfMonth());
    }

    private String reviewJson(String reviewerId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reviewerId", reviewerId);
        return objectMapper.writeValueAsString(body);
    }

    private String approvalJson(
            String reviewerId, String rate, LocalDate firstDueDate, String note) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reviewerId", reviewerId);
        body.put("annualInterestRate", new BigDecimal(rate));
        body.put("firstDueDate", firstDueDate.toString());
        if (note != null) {
            body.put("note", note);
        }
        return objectMapper.writeValueAsString(body);
    }

    private String rejectionJson(String reviewerId, String reason) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reviewerId", reviewerId);
        body.put("reason", reason);
        return objectMapper.writeValueAsString(body);
    }

    private static String applicationJson(
            UUID borrowerId, String amount, int term, String status) {
        return """
                {
                  "borrowerId": "%s",
                  "requestedAmount": %s,
                  "termMonths": %d,
                  "purpose": "Home renovation",
                  "status": "%s"
                }
                """.formatted(borrowerId, amount, term, status);
    }
}
