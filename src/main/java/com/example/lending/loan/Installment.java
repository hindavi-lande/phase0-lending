package com.example.lending.loan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the repayment schedule captured at approval time. Rows are never updated or deleted
 * through the API; they are read through the owning loan.
 */
@Entity
@Table(
        name = "installments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_installment_loan_sequence",
                columnNames = {"loan_id", "sequence"}))
public class Installment {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false, updatable = false)
    private Loan loan;

    @Column(name = "sequence", nullable = false, updatable = false)
    private int sequence;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "opening_balance", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "principal", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal principal;

    @Column(name = "interest", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal interest;

    @Column(name = "total", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "closing_balance", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal closingBalance;

    protected Installment() {
        // required by JPA
    }

    public Installment(
            Loan loan,
            int sequence,
            LocalDate dueDate,
            BigDecimal openingBalance,
            BigDecimal principal,
            BigDecimal interest,
            BigDecimal total,
            BigDecimal closingBalance) {
        this.loan = loan;
        this.sequence = sequence;
        this.dueDate = dueDate;
        this.openingBalance = openingBalance;
        this.principal = principal;
        this.interest = interest;
        this.total = total;
        this.closingBalance = closingBalance;
    }

    public UUID getId() {
        return id;
    }

    public Loan getLoan() {
        return loan;
    }

    public int getSequence() {
        return sequence;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public BigDecimal getInterest() {
        return interest;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }
}
