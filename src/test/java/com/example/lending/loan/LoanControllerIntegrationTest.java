package com.example.lending.loan;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lending.borrower.Borrower;
import com.example.lending.borrower.BorrowerRepository;
import com.example.lending.borrower.BorrowerStatus;
import com.example.lending.loanapplication.LoanApplicationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
class LoanControllerIntegrationTest {

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

    @Autowired
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
    void readingLoanReturnsTotalsAndScheduleInSequenceOrder() throws Exception {
        LocalDate firstDueDate = LocalDate.now().plusDays(30);
        String applicationId = createReviewedApplication("ada@example.com", "25000.00", 24);
        MvcResult approval = approve(applicationId, "12.50", firstDueDate, "Approved");
        String loanId = readId(approval, "loanId");

        mockMvc.perform(get("/api/loans/{id}", loanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanId").value(loanId))
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
                .andExpect(jsonPath("$.schedule[0].dueDate").value(firstDueDate.toString()))
                .andExpect(jsonPath("$.schedule[0].openingBalance").value(25000.00))
                .andExpect(jsonPath("$.schedule[0].principal").value(922.26))
                .andExpect(jsonPath("$.schedule[0].interest").value(260.42))
                .andExpect(jsonPath("$.schedule[0].total").value(1182.68))
                .andExpect(jsonPath("$.schedule[0].closingBalance").value(24077.74))
                .andExpect(jsonPath("$.schedule[1].sequence").value(2))
                .andExpect(jsonPath("$.schedule[1].openingBalance").value(24077.74))
                .andExpect(jsonPath("$.schedule[23].sequence").value(24))
                .andExpect(jsonPath("$.schedule[23].principal").value(1170.54))
                .andExpect(jsonPath("$.schedule[23].interest").value(12.19))
                .andExpect(jsonPath("$.schedule[23].total").value(1182.73))
                .andExpect(jsonPath("$.schedule[23].closingBalance").value(0.00));
    }

    @Test
    void unknownLoanReturns404() throws Exception {
        mockMvc.perform(get("/api/loans/{id}", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("Loan not found")));
    }

    @Test
    void loansExposeNoUpdateOrDeleteOperation() throws Exception {
        String applicationId = createReviewedApplication("ada@example.com", "25000.00", 24);
        String loanId = readId(approve(applicationId, "12.50", LocalDate.now().plusDays(30), null), "loanId");

        mockMvc.perform(put("/api/loans/{id}", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(delete("/api/loans/{id}", loanId))
                .andExpect(status().isMethodNotAllowed());
    }

    private String createReviewedApplication(String email, String amount, int term) throws Exception {
        UUID borrowerId = persistBorrower(email);

        MvcResult created = mockMvc.perform(post("/api/loan-applications")
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
        String applicationId = readId(created, "id");

        mockMvc.perform(post("/api/loan-applications/{id}/review", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewerId\":\"officer-1\"}"))
                .andExpect(status().isOk());

        return applicationId;
    }

    private MvcResult approve(String applicationId, String rate, LocalDate firstDueDate, String note)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reviewerId", "officer-2");
        request.put("annualInterestRate", new BigDecimal(rate));
        request.put("firstDueDate", firstDueDate.toString());
        if (note != null) {
            request.put("note", note);
        }

        return mockMvc.perform(post("/api/loan-applications/{id}/approve", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
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

    private String readId(MvcResult result, String field) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get(field).asText();
    }
}
