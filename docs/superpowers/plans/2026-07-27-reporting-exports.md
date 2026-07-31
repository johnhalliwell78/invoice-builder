# Reporting & Exports — Phase 3

**Why now:** the product can invoice, collect, and refund. What it cannot do
is hand any of that to an accountant. Every SMB needs a period export and a
tax breakdown at quarter end; without it the data is trapped in the UI.

## Design

### CSV exports

Streamed, not buffered: a tenant with 50k invoices must not materialise the
whole file in memory.

**Formula injection is the real hazard here.** A customer named
`=cmd|'/c calc'!A1` becomes an executable formula the moment the CSV is
opened in Excel or Sheets (CWE-1236). Any field starting with `= + - @`, tab,
or CR is prefixed with an apostrophe before quoting. This is a security
control, not cosmetics, and is tested as such.

Exports offered: invoices (with filters mirroring the list view) and payments
(including reversals as negative rows, so the file reconciles to `amountPaid`).

### Tax summary

Grouped by currency and tax rate over an issue-date range. Tax **must** be
summed per line and rounded the way `InvoiceCalculator` rounds it
(`HALF_UP`, scale 2, per line) — computing tax on the summed net instead
would drift by cents from what the invoices actually say, and a report that
disagrees with the documents is worse than no report.

Excludes DRAFT and CANCELLED, and estimates: neither is revenue.

## Milestones

### RE.1 Backend (branch `feat/reporting-exports`)

- `CsvWriter` (RFC 4180 quoting + injection guard).
- `ExportService` streaming invoices/payments; `ReportService` tax summary.
- `GET /api/v1/exports/invoices.csv`, `/exports/payments.csv`,
  `GET /api/v1/reports/tax-summary`.
- Tests: injection guard, quoting edge cases, per-line rounding, tenant
  scoping, exclusion of drafts/estimates.

- [x] Implement → verify → merge.

### RE.2 Frontend

- Reports page: period picker, tax summary table, download buttons. i18n ×3.

- [x] Implement → verify → merge.

## Deferred

Scheduled/emailed reports, PDF report rendering, per-customer statements.
