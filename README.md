# phase0-lending

Phase 0 scaffold: two entities, plain CRUD, one foreign-key relation. No auth, no
capabilities, no scoring, no schedules — deliberately.

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
mvn test                     # integration tests
mvn clean package            # executable jar in target/
```

H2 console: <http://localhost:8084/h2-console> (JDBC URL `jdbc:h2:mem:lending`, user `sa`, empty password).

## Domain

```
Borrower 1 ──── * LoanApplication
```

**Borrower** — `id`, `firstName`, `lastName`, `email` (unique), `phone`,
`dateOfBirth` (past), `annualIncome` (non-negative, 2 dp), `status` (`ACTIVE` | `INACTIVE`)

**LoanApplication** — `id`, `borrowerId` (FK → Borrower, required),
`requestedAmount` (> 0, 2 dp), `termMonths` (1–360), `purpose` (1–200 chars),
`status` (`SUBMITTED` | `UNDER_REVIEW` | `APPROVED` | `REJECTED` | `DISBURSED`),
`submittedAt`

The FK is mapped as a lazy `@ManyToOne` on `LoanApplication`; the wire format exposes it
as a flat `borrowerId` so responses never leak the entity graph.

## API

Both resources expose the same five operations.

| Method | Path | Success |
|---|---|---|
| `POST` | `/api/{borrowers,loan-applications}` | `201` + `Location` |
| `GET` | `/api/{borrowers,loan-applications}/{id}` | `200` |
| `GET` | `/api/{borrowers,loan-applications}` | `200` (unpaged list) |
| `PUT` | `/api/{borrowers,loan-applications}/{id}` | `200` (full replace) |
| `DELETE` | `/api/{borrowers,loan-applications}/{id}` | `204` |

`GET /api/loan-applications?borrowerId={id}` filters by borrower.

### Example

```bash
BORROWER=$(curl -s -X POST localhost:8084/api/borrowers \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Ada","lastName":"Lovelace","email":"ada@example.com","phone":"+44 20 7946 0958","dateOfBirth":"1990-01-01","annualIncome":120000.00,"status":"ACTIVE"}')

ID=$(echo "$BORROWER" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')

curl -s -X POST localhost:8084/api/loan-applications \
  -H 'Content-Type: application/json' \
  -d "{\"borrowerId\":\"$ID\",\"requestedAmount\":25000.00,\"termMonths\":24,\"purpose\":\"Home renovation\",\"status\":\"SUBMITTED\"}"
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
| `400` | bean-validation failure (`fieldErrors` populated), or malformed JSON / unknown enum value |
| `404` | unknown id — including a `LoanApplication` pointing at a non-existent `Borrower` |
| `409` | duplicate `email`, or deleting a `Borrower` that still has loan applications |

## Layout

```
com.example.lending
├── LendingApplication.java
├── common/              ApiError, GlobalExceptionHandler, 3 exception types
├── borrower/            entity, enum, repository, service, controller, dto/
└── loanapplication/     entity, enum, repository, service, controller, dto/
```

Each slice is self-contained; `LoanApplicationService` resolves the FK through
`BorrowerService.findOrThrow`, which is the single place the `Borrower` 404 is raised.

## Tests

Integration tests run against real H2 with `MockMvc`:

- `LendingApplicationTests` — context loads with both slices wired
- `BorrowerControllerIntegrationTest` — full CRUD, duplicate email, validation, delete guard
- `LoanApplicationControllerIntegrationTest` — full CRUD, borrower FK, filtering, validation
