# Payment Reversals (refunds, disputes) — Phase 3

**Why now:** the Stripe milestone made us collect real money, but money can
come *back*. A refund issued from the Stripe dashboard, or a customer
chargeback, produces events we currently ignore — the invoice stays PAID and
`amountPaid` stays high forever. Both reviews flagged this; it is the largest
remaining divergence between our ledger and reality.

## Design

Payments are append-only by deliberate choice, and that stays true. A reversal
is therefore **its own append-only record**, not a mutation or deletion of the
payment it reverses. `amountPaid` becomes:

```
amountPaid = Σ payments − Σ reversals
```

That keeps full history: you can always see that €119 was collected and €119
was refunded, rather than a row silently vanishing.

### Status handling

Today `PAID` is terminal in the transition table. Reversals must be able to
re-open an invoice, but we deliberately do **not** add `PAID → SENT` to that
table: `send()` calls `requireTransition(SENT)`, so opening it there would
also let someone re-send a paid invoice and quietly reset its state. Instead
the reversal path sets the status directly and derives it, the way
`markOverdueForTenant` already does:

- still covered (`amountPaid ≥ total`) → stays `PAID`
- otherwise → `OVERDUE` if past due, else `VIEWED` if it was ever viewed,
  else `SENT`; `paidAt` is cleared.

### Stripe events

| Event | Meaning | Reversal reason |
|---|---|---|
| `charge.refunded` | operator refunded (partly or fully) | `REFUND` |
| `charge.dispute.created` | customer charged back — funds withdrawn | `DISPUTE` |
| `charge.dispute.closed` | dispute resolved; if won, the funds return | reverses the `DISPUTE` |

Idempotency mirrors payments: unique `external_id` per reversal, so a
redelivered `charge.refunded` cannot double-reverse. `charge.refunded` reports
a **cumulative** `amount_refunded`, so the amount booked is the delta against
what we have already reversed for that payment — otherwise two partial refunds
would over-reverse.

### Manual reversals

Not every refund goes through Stripe — a bank transfer refunded by hand needs
recording too. Same endpoint, `reason = REFUND`, no external id.

## Milestones

### RV.1 Backend (branch `feat/payment-reversals`)

- Changelog `0020-payment-reversal.yaml`: `payment_reversal(id, tenant_id,
  payment_id FK, invoice_id, amount, reason, external_id unique nullable,
  note, created_by, created_at)`.
- `PaymentService.recordReversal` (manual) and `recordExternalReversal`
  (idempotent, from webhooks), both recomputing `amountPaid` and status
  under the existing `PESSIMISTIC_WRITE` invoice lock.
- Guard: cannot reverse more than was collected on that payment.
- `StripeWebhookService` handles the three charge events; resolving a charge
  back to our payment needs the PaymentIntent, so `charge.refunded` is matched
  via `payment_intent` against `payment.external_id`.
- Tests: partial then full refund (no over-reversal from cumulative amounts),
  dispute re-opens the invoice, redelivery is idempotent, over-reversal
  rejected, status derivation across due/viewed/sent.

- [x] Implement → verify → merge.

### RV.2 Frontend (same branch)

- Payments card shows reversals inline with a running balance.
- "Record refund" action on a paid/partly-paid invoice.
- List page balance accounts for reversals. i18n ×3.

- [x] Implement → verify → merge.

## Explicitly deferred

**Credit notes** (a document reducing what is owed, for goodwill or billing
errors) are a different concept from returning collected money, and need their
own numbering, PDF, and tax treatment. Separate milestone.
