# phase0-lending

Phase 0 scaffold: borrowers, loan applications, and the loans produced by approving an
application. No auth, no scoring, no disbursement, no repayment collection — deliberately.

## Stack

| | |
|---|---|
| Java | 21 (toolchain target) |
| Spring Boot | 3.5.6 |
| Persistence | Spring Data JPA + Hibernate |
| Database | H2 in-memory (`MODE=PostgreSQL`) |
| Build | Maven |

## Run

```bash
mvn spring-boot:run          # http://localhost:8084
mvn test                     # unit + integration tests
mvn clean package            # executable jar in target/
```

H2 console: <http://localhost:8084/h2-console> (JDBC URL `jdbc:h2:mem:lending`, user `sa`, empty password).

## Domain

```
Borrower 1 ──── * LoanApplication 1 ──── 0..1 Loan 1 ──── * Installment
```

**Borrower** — `id`, `firstName`, `lastName`, `email` (unique), `phone`,
`dateOfBirth` (past), `annualIncome` (non-negative, 2 dp), `status` (`ACTIVE` | `INACTIVE`)

**LoanApplication** — `id`, `borrowerId` (FK → Borrower, required),
`requestedAmount` (> 0, 2 dp), `termMonths` (1–360), `purpose` (1–200 chars),
`status` (`SUBMITTED` | `UNDER_REVIEW` | `APPROVED` | `REJECTED` | `DISBURSED`),
`submittedAt`, plus the decision fields `reviewerId` (1–100 chars), `reviewedAt`,
`decidedAt`, `decisionReason` (rejection, 1–200 chars), `decisionNote` (approval,
0–200 chars)

**Loan** — `id`, `applicationId` (unique FK → LoanApplication), `principal`,
`annualInterestRate` (0–36, 2 dp), `termMonths`, `monthlyInstalment`,
`totalPayable`, `totalInterest`, `firstDueDate`, `maturityDate`,
`status` (`PENDING_DISBURSEMENT`), `createdAt`

**Installment** — `id`, `loanId` (FK → Loan), `sequence`, `dueDate`,
`openingBalance`, `principal`, `interest`, `total`, `closingBalance`, unique on
`(loanId, sequence)`

The borrower FK is mapped as a lazy `@ManyToOne` on `LoanApplication`; the wire format
exposes it as a flat `borrowerId` so responses never leak the entity graph. A loan and its
installments are written once during approval and are immutable from then on.

## API

Borrowers and loan applications keep their CRUD surface.

| Method | Path | Success |
|---|---|---|
| `POST` | `/api/{borrowers,loan-applications}` | `201` + `Location` |
| `GET` | `/api/{borrowers,loan-applications}/{id}` | `200` |
| `GET` | `/api/{borrowers,loan-applications}` | `200` (unpaged list) |
| `PUT` | `/api/{borrowers,loan-applications}/{id}` | `200` (full replace) |
| `DELETE` | `/api/{borrowers,loan-applications}/{id}` | `204` |
| `POST` | `/api/loan-applications/{id}/review` | `200` |
| `POST` | `/api/loan-applications/{id}/approve` | `201` + `Location: /api/loans/{loanId}` |
| `POST` | `/api/loan-applications/{id}/reject` | `200` |
| `GET` | `/api/loans/{id}` | `200` |

`GET /api/loan-applications?borrowerId={id}` filters by borrower. Loans are read-only:
there is no update or delete mapping, so `PUT` and `DELETE` on `/api/loans/{id}` return `405`.

### Decision workflow

An application follows exactly `SUBMITTED -> UNDER_REVIEW -> APPROVED` or
`SUBMITTED -> UNDER_REVIEW -> REJECTED`. Any other transition returns `409` with
`Application status cannot change from {current} to {target}`.

Start review (`reviewerId` is 1–100 characters):

```bash
curl -s -X POST localhost:8084/api/loan-applications/$APPLICATION_ID/review \
  -H 'Content-Type: application/json' \
  -d '{"reviewerId":"officer-1"}'
```

Approve with an annual rate between `0.00` and `36.00` and a first due date between 1 and
90 days in the future. `note` is optional (0–200 characters):

```bash
curl -s -X POST localhost:8084/api/loan-applications/$APPLICATION_ID/approve \
  -H 'Content-Type: application/json' \
  -d '{"reviewerId":"officer-2","annualInterestRate":12.50,"firstDueDate":"2027-01-05","note":"Approved"}'
```

Reject with a mandatory reason of 1–200 characters:

```bash
curl -s -X POST localhost:8084/api/loan-applications/$APPLICATION_ID/reject \
  -H 'Content-Type: application/json' \
  -d '{"reviewerId":"officer-2","reason":"Insufficient income"}'
```

Rules enforced during a decision:

- `reviewerId` is required and blank values return `400` with `fieldErrors.reviewerId`.
- Approval validates `annualInterestRate`, `firstDueDate` and `note`; rejection validates
  `reason`. Invalid input returns `400` with the offending field in `fieldErrors`.
- Approval creates exactly one loan and its full schedule in the same transaction. A
  failure while writing any installment rolls back the loan, the schedule and the status
  change, leaving the application `UNDER_REVIEW`.
- An application can produce at most one loan. A second approval or a concurrent losing
  approval returns `409` `Application has already been decided`.
- An `APPROVED` or `REJECTED` application cannot be edited through `PUT`; the response is
  `409` `Decided applications cannot be modified`.
- The loan starts in `PENDING_DISBURSEMENT`.

### Repayment schedule

The monthly instalment uses the reducing-balance EMI formula
`P*r*(1+r)^n / ((1+r)^n - 1)` where `r = annualInterestRate / 1200`; a `0%` rate divides
the principal by the term. Money is always scale 2 with `HALF_UP` rounding.

Every schedule has exactly `termMonths` rows numbered `1..N`, ordered by sequence:

- `interest` is the opening balance times the monthly rate, rounded to 2 decimals.
- `principal` is the instalment minus interest, except the final row, which takes the
  remaining balance so the principal amounts sum to the approved amount exactly.
- `closingBalance` is `openingBalance - principal` and never goes negative.
- `total` is `principal + interest`; `totalPayable` sums all row totals and
  `totalInterest` is `totalPayable - principal`.
- Due dates advance monthly from `firstDueDate`, clamping to the last valid day (for
  example 31 January to 28/29 February). `maturityDate` is the last due date.

`GET /api/loans/{id}` returns the totals and the full schedule:

```json
{
  "loanId": "...",
  "applicationId": "...",
  "principal": 25000.00,
  "annualInterestRate": 12.50,
  "termMonths": 24,
  "monthlyInstalment": 1182.68,
  "totalPayable": 28384.37,
  "totalInterest": 3384.37,
  "firstDueDate": "2027-01-05",
  "maturityDate": "2028-12-05",
  "status": "PENDING_DISBURSEMENT",
  "schedule": [
    {
      "sequence": 1,
      "dueDate": "2027-01-05",
      "openingBalance": 25000.00,
      "principal": 922.26,
      "interest": 260.42,
      "total": 1182.68,
      "closingBalance": 24077.74
    },
    {
      "sequence": 24,
      "dueDate": "2028-12-05",
      "openingBalance": 1170.54,
      "principal": 1170.54,
      "interest": 12.19,
      "total": 1182.73,
      "closingBalance": 0.00
    }
  ]
}
```

### Example

```bash
BORROWER=$(curl -s -X POST localhost:8084/api/borrowers \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","phone":"+44 20 7946 0958","dateOfBirth":"1990-01-01","annualIncome":120000.00,"status":"ACTIVE"}')

BORROWER_ID=$(echo "$BORROWER" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')

APPLICATION=$(curl -s -X POST localhost:8084/api/loan-applications \
  -H 'Content-Type: application/json' \
  -d "{\"borrowerId\":\"$BORROWER_ID\",\"requestedAmount\":25000.00,\"termMonths\":24,\"purpose\":\"Home renovation\",\"status\":\"SUBMITTED\"}")

APPLICATION_ID=$(echo "$APPLICATION" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')

curl -s -X POST localhost:8084/api/loan-applications/$APPLICATION_ID/review \
  -H 'Content-Type: application/json' \
  -d '{"reviewerId":"officer-1"}'

LOAN=$(curl -s -X POST localhost:8084/api/loan-applications/$APPLICATION_ID/approve \
  -H 'Content-Type: application/json' \
  -d '{"reviewerId":"officer-2","annualInterestRate":12.50,"firstDueDate":"2027-01-05"}')

LOAN_ID=$(echo "$LOAN" | sed -n 's/.*"loanId":"\([^"]*\)".*/\1/p')

curl -s localhost:8084/api/loans/$LOAN_ID
```

## Errors

Every handled failure returns the same body, so clients parse one shape:

```json
{
  "timestamp": "2026-11-25T09:15:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fieldErrors": { "email": "email must be a valid address" }
}
```

| Status | Cause |
|---|---|
| `400` | bean-validation failure (`fieldErrors` populated), malformed JSON / unknown enum value, or business validation such as a first due date outside the 1–90 day window |
| `404` | unknown id — including a `LoanApplication` pointing at a non-existent `Borrower`, an unknown application on a decision, or an unknown loan |
| `409` | duplicate `email`, deleting a `Borrower` that still has loan applications, an invalid status transition, `Application has already been decided`, `Decided applications cannot be modified`, or a lock/duplicate-loan race |
| `405` | an update or delete attempted on the read-only `/api/loans/{id}` resource |

## Layout

```
com.example.lending
├── LendingApplication.java
├── common/              ApiError, GlobalExceptionHandler, 5 exception types
├── borrower/            entity, enum, repository, service, controller, dto/
├── loanapplication/     entity, enum, repository, service, controller, dto/
└── loan/                Loan, Installment, calculator, repositories, service, controller, dto/
```

Each slice is self-contained; `LoanApplicationService` resolves the FK through
`BorrowerService.findOrThrow`, which is the single place the `Borrower` 404 is raised.
Approval delegates loan creation to `LoanService`, while
`RepaymentScheduleCalculator` stays framework-free and unit tested.

## Tests

Integration tests run against real H2 with `MockMvc`:

- `LendingApplicationTests` — context loads with every controller wired
- `BorrowerControllerIntegrationTest` — full CRUD, duplicate email, validation, delete guard
- `LoanApplicationControllerIntegrationTest` — full CRUD, borrower FK, filtering, validation
- `LoanDecisionControllerIntegrationTest` — review/approve/reject transitions, validation,
  concurrency, atomicity, decided-application guards and unknown ids
- `LoanControllerIntegrationTest` — loan totals, ordered schedule, unknown loan, `405` on
  update/delete
- `RepaymentScheduleCalculatorTest` — EMI, 0% rate, rounding, final-instalment absorption,
  month-end clamping and totals

## Out of scope

Disbursement, repayment collection and late fees are not implemented. The loan status stops
at `PENDING_DISBURSEMENT`, and the schedule is fixed at approval time.
