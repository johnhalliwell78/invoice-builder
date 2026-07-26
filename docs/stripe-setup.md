# Stripe Checkout — operator setup

Online card payment is **off by default**. With no secret key configured the
checkout endpoint refuses requests and the public invoice page hides the
"Pay" button, so development, CI, and tests need no Stripe account at all.

## 1. Test-mode keys

From the [Stripe dashboard](https://dashboard.stripe.com/test/apikeys) in
**test mode**, copy the secret key (`sk_test_…`).

```bash
export STRIPE_SECRET_KEY=sk_test_...
export STRIPE_WEBHOOK_SECRET=whsec_...        # from step 2
export PUBLIC_BASE_URL=http://localhost:5173  # where recipients land back
```

Never commit real keys. Production uses the same variable names with live
values injected by the deployment environment.

## 2. Webhook secret

The browser redirect after checkout proves nothing — it is user-controlled
and forgeable. **The webhook is the only thing that books a payment**, and it
is verified against this secret.

Local development, using the [Stripe CLI](https://stripe.com/docs/stripe-cli):

```bash
stripe login
stripe listen --forward-to localhost:8080/api/v1/public/stripe/webhook
```

The CLI prints a `whsec_…` secret — that is `STRIPE_WEBHOOK_SECRET`. It is
different from the dashboard's endpoint secret, and it changes per session.

For a deployed environment, add an endpoint in **Developers → Webhooks**
pointing at `https://<your-api-host>/api/v1/public/stripe/webhook` and copy
its signing secret.

## 3. Events to subscribe to

| Event | Why |
|---|---|
| `checkout.session.completed` | Instant methods (cards) — the normal path |
| `checkout.session.async_payment_succeeded` | Delayed methods (e.g. SEPA debit) that finish after the session closes |

Anything else is acknowledged and ignored.

## 4. Try it end to end

1. Send an invoice so it has a public token, and open `/i/<token>`.
2. Click **Pay** → Stripe Checkout opens.
3. Pay with test card `4242 4242 4242 4242`, any future expiry, any CVC.
4. The `stripe listen` terminal shows the delivered event; the invoice's
   payment history gains a card payment and flips to PAID once covered.

## How it behaves

- **Partial payments:** checkout always charges the *remaining balance*, so a
  recipient can pay after a partial bank transfer was recorded.
- **Retries and duplicates:** Stripe redelivers events. The event id is a
  primary key and the PaymentIntent id is uniquely indexed, so a payment
  cannot be booked twice.
- **Payment landing after a manual "mark paid":** still recorded. The money
  was really collected, so the books must show it — the invoice becomes
  visibly over-paid and the excess is audited for an out-of-band refund.
- **Zero-decimal currencies** (JPY, KRW, …) are converted correctly; there is
  no blanket ×100.
- **Refunds** are not implemented — issue them from the Stripe dashboard.
  Reflecting them in the ledger arrives with credit notes.
