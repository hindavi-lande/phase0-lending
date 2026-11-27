package com.example.lending.loanapplication;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lending.borrower.Borrower;
import com.example.lending.borrower.BorrowerRepository;
import com.example.lending.borrower.BorrowerStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
class LoanApplicationControllerIntegrationTest {

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
    void fullCrudLifecycle() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com");

        MvcResult created = mockMvc.perform(post("/api/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(borrowerId, "25000.00", 24, "Home renovation", "SUBMITTED")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/loan-applications/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.borrowerId").value(borrowerId.toString()))
                .andExpect(jsonPath("$.requestedAmount").value(25000.00))
                .andExpect(jsonPath("$.termMonths").value(24))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").exists())
                .andReturn();

        String id = readId(created);

        mockMvc.perform(get("/api/loan-applications/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("Home renovation"));

        mockMvc.perform(get("/api/loan-applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(put("/api/loan-applications/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(borrowerId, "30000.00", 36, "Debt consolidation", "UNDER_REVIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedAmount").value(30000.00))
                .andExpect(jsonPath("$.termMonths").value(36))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        mockMvc.perform(delete("/api/loan-applications/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/loan-applications/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void createForUnknownBorrowerReturns404() throws Exception {
        mockMvc.perform(post("/api/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(
                                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                                "25000.00", 24, "Home renovation", "SUBMITTED")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("Borrower not found")));
    }

    @Test
    void zeroRequestedAmountReturns400WithFieldError() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com");

        mockMvc.perform(post("/api/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(borrowerId, "0.00", 24, "Home renovation", "SUBMITTED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.requestedAmount")
                        .value("requestedAmount must be greater than zero"));
    }

    @Test
    void termBelowOneReturns400WithFieldError() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com");

        mockMvc.perform(post("/api/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(borrowerId, "25000.00", 0, "Home renovation", "SUBMITTED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.termMonths").value("termMonths must be at least 1"));
    }

    @Test
    void termAbove360Returns400WithFieldError() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com");

        mockMvc.perform(post("/api/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(borrowerId, "25000.00", 361, "Home renovation", "SUBMITTED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.termMonths").value("termMonths must be at most 360"));
    }

    @Test
    void blankPurposeReturns400WithFieldError() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com");

        mockMvc.perform(post("/api/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(borrowerId, "25000.00", 24, " ", "SUBMITTED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.purpose").value("purpose is required"));
    }

    @Test
    void invalidStatusReturns400MalformedBody() throws Exception {
        UUID borrowerId = persistBorrower("ada@example.com");

        mockMvc.perform(post("/api/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(borrowerId, "25000.00", 24, "Home renovation", "UNKNOWN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    void getUnknownApplicationReturns404() throws Exception {
        mockMvc.perform(get("/api/loan-applications/{id}", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("Loan application not found")));
    }

    @Test
    void listFiltersByBorrower() throws Exception {
        UUID adaId = persistBorrower("ada@example.com");
        UUID graceId = persistBorrower("grace@example.com");

        createApplication(adaId, "25000.00", 24, "Home renovation");
        createApplication(graceId, "10000.00", 12, "Car repair");

        mockMvc.perform(get("/api/loan-applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get("/api/loan-applications").param("borrowerId", adaId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].borrowerId").value(adaId.toString()));
    }

    @Test
    void updateCanMoveApplicationToAnotherBorrower() throws Exception {
        UUID adaId = persistBorrower("ada@example.com");
        UUID graceId = persistBorrower("grace@example.com");
        String id = createApplication(adaId, "25000.00", 24, "Home renovation");

        mockMvc.perform(put("/api/loan-applications/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(graceId, "18000.00", 18, "Wedding", "UNDER_REVIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.borrowerId").value(graceId.toString()))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
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

    private String createApplication(UUID borrowerId, String amount, int term, String purpose) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationJson(borrowerId, amount, term, purpose, "SUBMITTED")))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private String readId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    private static String applicationJson(
            UUID borrowerId, String amount, int term, String purpose, String status) {
        return """
                {
                  "borrowerId": "%s",
                  "requestedAmount": %s,
                  "termMonths": %d,
                  "purpose": "%s",
                  "status": "%s"
                }
                """.formatted(borrowerId, amount, term, purpose, status);
    }
}
