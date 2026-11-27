package com.example.lending.loan;

import com.example.lending.loanapplication.LoanApplication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A loan produced by approving an application. Every column is written once at approval: there is
 * no update path, and the application link is unique so an application can produce at most one
 * loan.
 */
@Entity
@Table(
        name = "loans",
        uniqueConstraints = @UniqueConstraint(name = "uq_loan_application", columnNames = "application_id"))
public class Loan {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true, updatable = false)
    private LoanApplication application;

    @Column(name = "principal", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal principal;

    @Column(name = "annual_interest_rate", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal annualInterestRate;

    @Column(name = "term_months", nullable = false, updatable = false)
    private int termMonths;

    @Column(name = "monthly_instalment", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal monthlyInstalment;

    @Column(name = "total_payable", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal totalPayable;

    @Column(name = "total_interest", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal totalInterest;

    @Column(name = "first_due_date", nullable = false, updatable = false)
    private LocalDate firstDueDate;

    @Column(name = "maturity_date", nullable = false, updatable = false)
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 30)
    private LoanStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Loan() {
        // required by JPA
    }

    public Loan(
            LoanApplication application,
            BigDecimal principal,
            BigDecimal annualInterestRate,
            int termMonths,
            BigDecimal monthlyInstalment,
            BigDecimal totalPayable,
            BigDecimal totalInterest,
            LocalDate firstDueDate,
            LocalDate maturityDate,
            LoanStatus status) {
        this.application = application;
        this.principal = principal;
        this.annualInterestRate = annualInterestRate;
        this.termMonths = termMonths;
        this.monthlyInstalment = monthlyInstalment;
        this.totalPayable = totalPayable;
        this.totalInterest = totalInterest;
        this.firstDueDate = firstDueDate;
        this.maturityDate = maturityDate;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public LoanApplication getApplication() {
        return application;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public int getTermMonths() {
        return termMonths;
    }

    public BigDecimal getMonthlyInstalment() {
        return monthlyInstalment;
    }

    public BigDecimal getTotalPayable() {
        return totalPayable;
    }

    public BigDecimal getTotalInterest() {
        return totalInterest;
    }

    public LocalDate getFirstDueDate() {
        return firstDueDate;
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public LoanStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
