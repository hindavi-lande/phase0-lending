package com.example.lending.borrower;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lending.loanapplication.LoanApplicationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class BorrowerControllerIntegrationTest {

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
        MvcResult created = mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerJson("ada@example.com")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/borrowers/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.annualIncome").value(120000.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        String id = readId(created);

        mockMvc.perform(get("/api/borrowers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.lastName").value("Lovelace"));

        mockMvc.perform(get("/api/borrowers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(put("/api/borrowers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerJson("ada.new@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada.new@example.com"));

        mockMvc.perform(delete("/api/borrowers/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/borrowers/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingDuplicateEmailInDifferentCaseReturns409() throws Exception {
        createBorrower("ada@example.com");

        mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerJson("ADA@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(containsString("already in use")));
    }

    @Test
    void blankFirstNameReturns400WithFieldError() throws Exception {
        String body = """
                {
                  "firstName": " ",
                  "lastName": "Lovelace",
                  "email": "ada@example.com",
                  "phone": "+44 20 7946 0958",
                  "dateOfBirth": "1990-01-01",
                  "annualIncome": 120000.00,
                  "status": "ACTIVE"
                }
                """;

        mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.firstName").value("firstName is required"));
    }

    @Test
    void futureDateOfBirthReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerJsonWithDate("ada@example.com", "2999-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").value("dateOfBirth must be in the past"));
    }

    @Test
    void invalidEmailReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerJson("not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("email must be a valid address"));
    }

    @Test
    void negativeAnnualIncomeReturns400WithFieldError() throws Exception {
        String body = """
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "email": "ada@example.com",
                  "phone": "+44 20 7946 0958",
                  "dateOfBirth": "1990-01-01",
                  "annualIncome": -1.00,
                  "status": "ACTIVE"
                }
                """;

        mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.annualIncome")
                        .value("annualIncome must not be negative"));
    }

    @Test
    void getUnknownBorrowerReturns404() throws Exception {
        mockMvc.perform(get("/api/borrowers/{id}", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("Borrower not found")));
    }

    @Test
    void updateKeepingOwnEmailSucceeds() throws Exception {
        String id = createBorrower("ada@example.com");

        mockMvc.perform(put("/api/borrowers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerJson("ada@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    void updateToAnotherBorrowersEmailReturns409() throws Exception {
        String id = createBorrower("ada@example.com");
        createBorrower("grace@example.com");

        mockMvc.perform(put("/api/borrowers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerJson("grace@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already in use")));
    }

    @Test
    void deletingBorrowerWithApplicationsReturns409() throws Exception {
        String borrowerId = createBorrower("ada@example.com");

        mockMvc.perform(post("/api/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "borrowerId": "%s",
                                  "requestedAmount": 25000.00,
                                  "termMonths": 24,
                                  "purpose": "Home renovation",
                                  "status": "SUBMITTED"
                                }
                                """.formatted(borrowerId)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/borrowers/{id}", borrowerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("loan applications")));

        mockMvc.perform(get("/api/borrowers/{id}", borrowerId))
                .andExpect(status().isOk());
    }

    private String createBorrower(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerJson(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result);
    }

    private String readId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    private static String borrowerJson(String email) {
        return borrowerJsonWithDate(email, "1990-01-01");
    }

    private static String borrowerJsonWithDate(String email, String dateOfBirth) {
        return """
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "email": "%s",
                  "phone": "+44 20 7946 0958",
                  "dateOfBirth": "%s",
                  "annualIncome": 120000.00,
                  "status": "ACTIVE"
                }
                """.formatted(email, dateOfBirth);
    }
}
