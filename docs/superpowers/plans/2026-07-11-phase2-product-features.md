# Phase 2 — Product Features

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Checkbox (`- [ ]`) steps. Companion to the Phase 1 plans (2026-07-08, 2026-07-09 — all shipped).

**Goal:** Ship the seven Phase 2 product features in order: Dashboard, Duplicate Invoice, Product Catalog, Customer combobox improvements, Estimates, Recurring Invoices, Payment Tracking.

**Architecture:** Everything extends the existing module layout and patterns (tenant-scoped repositories, `record` DTOs, `ApiResponse` envelope, TanStack Query + RHF/zod, i18n ×3, Liquibase changelogs 0014+). No new dependencies on either tier — the revenue chart is hand-rolled SVG, not a chart library. One branch per feature, merged to `main` after green tests (established pattern).

**Global constraints:** identical to the Phase 1 plans (no new deps, i18n parity en/de/fr, conventional commits with the Claude trailer, backend tests via the off-VPN init-script bypass, lockfile stays untracked).

**Multi-currency rule (applies to all aggregates):** never sum across currencies. All monetary aggregates are grouped by currency; the UI headlines the tenant's default currency and lists others separately.

---

## P2.1 Dashboard (branch `feat/dashboard`)

**Backend** — new `dashboard` package.
- `DashboardService` + `GET /api/v1/dashboard` → `DashboardResponse`:
  - `tiles: [{currency, outstanding, overdue, paidThisMonth}]` (grouped by currency; outstanding = SENT+VIEWED+OVERDUE totals, overdue subset, paidThisMonth by `paidAt >= startOfMonth`),
  - `statusCounts: {DRAFT: n, SENT: n, …}`,
  - `revenueByMonth: [{month: "2026-07", currency, total}]` — last 12 months of PAID revenue (native `to_char(paid_at,'YYYY-MM')` query), missing months zero-filled per currency in the service,
  - `customersByMonth: [{month, count}]` — last 12 months of new customers,
  - `recentInvoices: [{id, invoiceNumber, status, total, currency, customerName, updatedAt}]` — 8 latest, customer name joined server-side.
- Repository additions: aggregate queries on `InvoiceRepository` (native where month-bucketing) + `CustomerRepository.countByMonth`. Recent-invoices JPQL join on `Customer`.
- Test: `DashboardServiceTest` — month-series zero-fill logic (the only real logic) + grouping mapping from stubbed rows.

**Frontend** — rebuild `DashboardPage`:
- Stat tiles (outstanding / overdue / paid this month, headline = tenant default currency via `useTenant`), status badges row, 12-month revenue bar chart + customer growth mini-chart (shared hand-rolled SVG `BarChart` component — consult the dataviz skill before writing it), recent-activity list linking to invoices.
- `api/dashboard.ts` + `hooks/useDashboard.ts`, i18n `dashboard.*` ×3, component test (tiles render from mocked stats).

- [ ] Backend failing tests → implement → green → commit.
- [ ] Frontend + i18n + test → full verify → commit → merge.

## P2.2 Duplicate Invoice (branch `feat/duplicate-invoice`)

- `POST /api/v1/invoices/{id}/duplicate` → new DRAFT: copies customer, currency, template, notes, terms, line items; fresh number via `reserveNext`; `issueDate = today`, `dueDate = today + (src.due − src.issue)`; source may be ANY status. Returns `InvoiceResponse`.
- Service test: field copy, date shift, fresh number, DRAFT status, no token/sentAt copied.
- Frontend: "Duplicate" button on `InvoiceDetailPage` (all statuses) → `useDuplicateInvoice` → navigate to `/invoices/{newId}/edit`. i18n ×3.

- [ ] Failing test → implement → verify → merge.

## P2.3 Product Catalog (branch `feat/product-catalog`)

- Changelog `0014-product.yaml`: `product(id, tenant_id, name≤255, description TEXT, unit_price NUMERIC(15,2), tax_rate NUMERIC(5,2), category≤100, active bool default true, timestamps)` + index on tenant_id.
- Entity/repo/service/controller mirroring the Customer module: CRUD + `q` search (name/category, active only) + pagination under `/api/v1/products`.
- Frontend: Products page (list+form, mirroring Customers), nav item; **line-item autocomplete** in `InvoiceFormPage`: typing in a description field queries products (300 ms debounce) and shows a dropdown; picking one fills description/unitPrice/taxRate.
- Tests: ProductService unit test; autocomplete component test.

- [ ] Backend → frontend CRUD → autocomplete → verify → merge.

## P2.4 Customer Improvements (branch `feat/customer-combobox`)

- New `CustomerCombobox` component (input + debounced server search + dropdown + keyboard nav + "load more" via `useInfiniteQuery`); replaces the 200-cap `<select>` in `InvoiceFormPage` and the filter dropdown in `InvoiceListPage`.
- Backend already supports `q` + pagination — no changes expected.
- Also: add `customerName` to `InvoiceListItem` (backend join) and drop the client-side join in `InvoiceListPage` (deletes the other 200-cap).
- Tests: combobox component test; `InvoiceListItem` mapping test.

- [ ] Implement → verify → merge.

## P2.5 Estimate Workflow (branch `feat/estimates`)

- Changelog `0015-invoice-doc-type.yaml`: `doc_type VARCHAR(20) NOT NULL DEFAULT 'INVOICE'` on `invoice`; number prefix for estimates (`EST-…`) via separate tenant counter column `next_estimate_number`.
- `DocType {INVOICE, ESTIMATE}`; `InvoiceStatus` gains `APPROVED`, `DECLINED` (estimate-only transitions: DRAFT→SENT→APPROVED|DECLINED; APPROVED→converted). Transition table becomes doc-type aware.
- Endpoints: existing invoice CRUD gains `docType` filter; `POST /{id}/approve`, `POST /{id}/decline` (estimate only), `POST /{id}/convert` → creates a linked INVOICE draft (copy like duplicate), stamps `convertedInvoiceId`.
- Frontend: Estimates nav page (reuses list/form/detail components parameterized by doc type), approve/decline/convert actions, public page shows "Estimate" title.
- Tests: status machine per doc type; convert copies + links.

- [ ] Backend state machine → endpoints → frontend → verify → merge.

## P2.6 Recurring Invoices (branch `feat/recurring`)

- Changelog `0016-recurring-invoice.yaml`: `recurring_invoice(id, tenant_id, customer_id, frequency VARCHAR(10) DAILY|WEEKLY|MONTHLY|YEARLY, next_run DATE, active bool, auto_send bool, currency, template, notes, terms, line_items JSONB, created_by, timestamps)`.
- `RecurringInvoiceSweeper` (daily 04:00 UTC, mirrors `OverdueSweeper`): for each due schedule → create DRAFT via `InvoiceService.create` payload from the JSONB snapshot → optionally `send` when `auto_send` → advance `next_run` by frequency.
- Endpoints: CRUD `/api/v1/recurring` + "create from invoice" (`POST /api/v1/invoices/{id}/make-recurring {frequency, autoSend}`).
- Frontend: Recurring page (list + enable/disable + next run), "Make recurring" action on invoice detail. i18n ×3.
- Tests: sweeper advances dates correctly across frequencies (month-end clamping!), generates drafts, honors active flag.

- [ ] Backend → frontend → verify → merge.

## P2.7 Payment Tracking (branch `feat/payments`)

- Changelog `0017-payment.yaml`: `payment(id, tenant_id, invoice_id FK, amount NUMERIC(15,2), method VARCHAR(30) BANK_TRANSFER|CARD|CASH|PAYPAL|OTHER, paid_on DATE, note ≤500, created_by, created_at)`.
- `POST /api/v1/invoices/{id}/payments` (allowed for SENT/VIEWED/OVERDUE/PAID? — no: open statuses only), `GET …/payments`, `DELETE …/payments/{paymentId}` (recalculates). `amountPaid = Σ payments`; when `amountPaid ≥ total` → transition to PAID (`paidAt = latest payment`); deleting below total reverts PAID→SENT-family… **keep v1 simple: payments are append-only, no delete; document it.**
- `markPaid` becomes "record remaining balance as payment" (keeps API compat).
- Frontend: Payments card on detail (history + balance), "Record payment" dialog (amount defaulting to balance, method, date, note); list page shows balance for partially paid.
- Tests: partial → balance math; full → PAID transition; over-payment rejected.

- [ ] Backend → frontend → verify → merge.

---

## Self-review notes

- Order matches the user's brief exactly. P2.4's `customerName` join also benefits P2.1's recent-activity list (dashboard does its own join server-side, so no dependency).
- Estimates reuse the invoice machinery rather than a parallel table — one calculator, one PDF pipeline, one send flow. The cost is doc-type-aware transitions, contained in `InvoiceStatus`/service guards.
- Payments deliberately append-only in v1; reversing payments touches accounting semantics that belong with credit notes (Phase 3+).
- Deferred explicitly: Stripe/online payment collection (roadmap Phase 2 "revenue loop" in the analysis doc, but not in the user's Phase 2 list).
