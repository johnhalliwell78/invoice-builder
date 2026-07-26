# Stripe Revenue Loop (Phase 3 flagship)

**Goal:** a recipient opening the public invoice link can pay by card, and the
payment lands in our books automatically — closing the loop on P2.7.

**Non-goals (v1):** refunds, saved cards, subscriptions/Stripe Billing,
multi-account Connect payouts, Apple/Google Pay tuning. Stripe Checkout only.

## Design

Stripe **Checkout** (hosted page) — no card data ever touches our server, so
PCI scope stays at SAQ-A. Flow:

1. `POST /api/v1/public/invoices/{token}/checkout` (anonymous, token-scoped)
   → validates the invoice is payable → creates a Checkout Session for the
   **remaining balance** → returns `{ url }`; frontend redirects.
2. Stripe hosts payment, then redirects back to `/i/{token}?payment=success`.
3. Stripe calls `POST /api/v1/public/stripe/webhook` → **this** is what books
   the payment (the redirect is cosmetic and must never be trusted).

### Correctness requirements (money — no hand-waving)

- **Signature verification** on every webhook via `Webhook.constructEvent`
  with the endpoint secret. Unsigned/mis-signed → 400, nothing recorded.
- **Raw body** must reach the verifier byte-for-byte (`@RequestBody String`,
  no Jackson round-trip).
- **Idempotency, two layers:** Stripe retries deliveries and can send several
  events for one payment.
  - `stripe_event` table keyed by Stripe's event id (PK) — replay = no-op.
  - `payment.external_id` unique — the same PaymentIntent can never be
    booked twice even across different event types.
- **Zero-decimal currencies:** JPY/KRW have no minor unit. Converting with a
  blanket ×100 would charge a customer 100× the invoice. Explicit set.
- **Amount drift:** a manual payment may land between session creation and
  completion. The webhook books *the amount Stripe actually collected*, not
  the balance we computed earlier. That amount can exceed the remaining
  balance — the money is already taken, so rejecting it would make our books
  lie. New `PaymentService.recordExternal` skips the typo guard that
  `record` applies to hand-entered payments, and audits the overpayment.
- **No TenantContext** on a webhook request (it is anonymous, JWT-less):
  resolve the tenant from the invoice and set it explicitly, like the
  sweepers do.
- **Feature gating:** no secret key configured → endpoints 404 and the UI
  hides "Pay now". Dev, CI, and tests run untouched without Stripe keys.

## Milestones

### ST.1 Backend (branch `feat/stripe-checkout`)

- `stripe-java 33.1.1`; `app.stripe.{secret-key,webhook-secret,enabled}`,
  `app.public-base-url`.
- Changelog `0018-stripe.yaml`: `stripe_event(id PK, type, invoice_id,
  received_at)` + `payment.external_id` (unique, nullable).
- `StripeCheckoutService` (session creation), `StripeWebhookService`
  (verify → dedup → book), `StripePublicController`.
- `PaymentService.recordExternal(...)`.
- Tests: minor-unit conversion incl. JPY; webhook rejects bad signature,
  ignores unknown types, dedups replays, books a payment; IT proving the
  DB-level unique constraint stops a double booking.

- [ ] Implement → verify → merge.

### ST.2 Frontend (same branch)

- "Pay now" on the public page when payable + enabled; redirect to Stripe.
- Return handling: `?payment=success|cancelled` → toast + refetch.
- `PublicInvoiceResponse.paymentEnabled` drives visibility. i18n ×3.

- [ ] Implement → verify → merge.

### ST.3 Operator docs

- README/`docs/`: test-mode keys, `stripe listen` for local webhooks, which
  events to subscribe to in the dashboard.

- [ ] Write → merge.

## Deferred to later Phase 3

Refunds + credit notes (they share ledger semantics), reporting/exports,
API keys + outbound webhooks.
