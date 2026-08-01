# Credit Notes & Cancellation Guard — Phase 3 leftovers

Two related gaps in the accounting model.

## Why credit notes are not refunds

A **refund** returns money that was collected (built in RV.1). A **credit
note** reduces what is *owed* — a billing error, goodwill, returned goods —
with no money moving at all. Conflating them misstates both revenue and tax,
which is why reversals deliberately did not try to cover this.

## Design

A credit note is a document with line items, tax, and its own number. That is
exactly what the invoice pipeline already provides, so — as with estimates —
it becomes a third `DocType` rather than a parallel table, inheriting the
calculator, PDF, numbering, and audit trail.

- `DocType.CREDIT_NOTE`, numbered `CN-YYYY-nnnn` from its own tenant counter.
- `credited_invoice_id` links it to the invoice it credits (mirrors
  `converted_invoice_id`).
- `invoice.credited_amount` accumulates issued credit notes, so
  **balance = total − amountPaid − creditedAmount**.
- Lifecycle: `DRAFT → SENT` (issued), terminal. Issuing is what applies the
  credit; a draft credit note affects nothing.

### Settlement semantics (the subtle part)

When `amountPaid + creditedAmount >= total` the invoice owes nothing and is
settled to `PAID` — otherwise it would sit in `OVERDUE` forever and keep
generating reminders for money nobody expects.

But `paidAt` is left **null** when no money was actually received. That is
load-bearing: the dashboard's revenue figures key on `paidAt`, so an invoice
written off by credit never inflates revenue. Tested explicitly.

### Tax summary

Credit notes are negative revenue, so the tax report subtracts them. Without
this a written-off invoice would still be declared as taxable turnover.

## Milestones

### CN.1 Backend

- Changelog `0021-credit-note.yaml`: `doc_type` gains CREDIT_NOTE (no schema
  change needed), plus `invoice.credited_amount` and
  `invoice.credited_invoice_id`, and `tenant.next_credit_note_number`.
- `InvoiceService.createCreditNote(invoiceId)` and issue-on-send handling.
- Status machine: credit-note transitions.
- Tax summary subtracts CREDIT_NOTE totals.
- **Cancellation guard:** an invoice with recorded payments can currently be
  cancelled, stranding the money. Cancelling now requires a zero balance
  received — otherwise refund or credit it first.

- [x] Implement → verify → merge.

### CN.2 Frontend

- "Credit note" action on an invoice; credit notes listed under their own
  nav entry reusing the list/detail pages by doc type. i18n ×3.

- [x] Implement → verify → merge.

## Deferred

Standalone credit notes not tied to an invoice; applying one credit note
across several invoices; credit-note PDF wording beyond the doc-type title.
