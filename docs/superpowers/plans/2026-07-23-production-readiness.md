# Production Readiness (post-Phase 2)

**Why now:** the Phase 2 adversarial review caught a critical transaction-poisoning
bug in the recurring sweeper that all 105 unit tests could not see — it only
exists with real Spring transaction proxies and a real Postgres. Before Stripe
touches real money, that class of bug needs a permanent safety net.

## PR.1 Integration test foundation (branch `feat/integration-tests`)

- Testcontainers Postgres via the deps already in `build.gradle`
  (`testcontainers.postgresql`, `testcontainers.junit.jupiter`) —
  `@DynamicPropertySource`, no new dependencies.
- `IntegrationTestBase`: `@SpringBootTest` + shared static container +
  `@Testcontainers(disabledWithoutDocker = true)` so laptop runs without
  Docker skip cleanly; CI (ubuntu-latest) always runs them.
- Booting the context is itself the migration test: Liquibase applies
  changelogs 0000–0017 against real Postgres.
- Tests (each one targets a bug class unit tests provably missed):
  1. `RecurringSweepIT` — tenant with a healthy schedule + a schedule whose
     customer is soft-deleted → sweep commits the healthy draft, advances
     both schedules, no rollback-only poisoning. (Fails on pre-fix code.)
  2. `PaymentConcurrencyIT` — two concurrent payments racing the balance
     check → row lock serializes; exactly one succeeds; no over-payment.
  3. `EstimateConvertIT` — concurrent convert of one APPROVED estimate →
     exactly one linked invoice (one-shot guard under lock).
  4. `MigrationSmokeIT` — context boots, key tables exist.
- Keep unit tests fast: ITs live in the same `test` task but skip without
  Docker; CI gets them for free via `./gradlew check`.

- [x] Implement → verify (with Docker) → merge. Mutation-verified: pre-fix transaction shape fails RecurringSweepIT.

## PR.2 Estimate email + PDF wording (branch `fix/estimate-wording`)

- `composeEmail` / PDF title / attachment name say "Invoice" for estimates.
  Add doc-type-aware message keys (en/de/fr ×2 files) and pick by
  `invoice.getDocType()`. Public page already says "Estimate".

- [x] Implement → verify → merge.

## PR.3 Input validation odds and ends (branch `fix/validation-polish`)

- `MakeRecurringRequest.firstRun`: reject past dates (`@FutureOrPresent`).
- `PaymentRequest.paidOn`: reject future dates (`@PastOrPresent`) — backdating
  is legitimate, postdating is not.
- Frontend: date input min/max hints to match.

- [x] Implement → verify → merge.

## PR.4 E2E smoke (Playwright) — NEXT milestone, not this one

- Full-stack boot via compose; register → customer → invoice → send → public
  page flow. Deliberately separate: needs its own infra decisions (CI
  services, mailpit assertions).

## Deferred (unchanged)

- Credit notes / payment reversals / cancel-with-payments guard → Phase 3.
- Stripe revenue loop → immediately after this milestone.
