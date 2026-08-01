# API keys & outbound webhooks

Two ways to integrate: pull with an API key, or be pushed to with a webhook.

## API keys

Create one under **Settings → API keys**. The secret is shown **once** — only
its SHA-256 hash is stored, so it cannot be recovered afterwards. Give each
integration its own key so one can be revoked without disturbing the others.

```bash
curl -H "X-API-Key: ib_..." https://your-host/api/v1/invoices
```

- Works anywhere a JWT does; the two schemes coexist.
- Each key carries a role (`ADMIN` or `MEMBER`) and is scoped to one tenant.
- Actions are attributed in the audit log to the user who created the key.
- Revocation takes effect on the next request.
- `last used` is recorded at 5-minute resolution — enough to spot dead keys
  without turning every API call into a database write.

## Outbound webhooks

Register an endpoint under **Settings → Webhooks** and pick the events you
care about: `INVOICE_SENT`, `INVOICE_VIEWED`, `INVOICE_PAID`,
`INVOICE_OVERDUE`, `CUSTOMER_CREATED`.

We POST JSON:

```json
{
  "type": "INVOICE_PAID",
  "occurredAt": "2026-08-02T10:00:00Z",
  "data": { "referenceType": "Invoice", "referenceId": "…", "subject": "INV-2026-0001" }
}
```

### Verifying the signature

Every request carries `X-InvoiceBuilder-Signature`, deliberately in the same
shape Stripe uses so you can reuse a familiar recipe:

```
X-InvoiceBuilder-Signature: t=<unix-seconds>,v1=<hex hmac-sha256>
```

The signed string is `"<timestamp>.<raw body>"`, keyed with your endpoint
secret. Compare with a constant-time function, and reject stale timestamps to
defeat replays.

### Delivery behaviour

- Queued **after** the originating transaction commits, so a rolled-back
  invoice never announces itself.
- Sent by a background job — a slow endpoint of yours never slows us down.
- Retried with exponential backoff (1, 2, 4, 8, 16, 32 minutes), then marked
  failed after 6 attempts. Return any 2xx to acknowledge.

### URL restrictions (and their limits)

Only public `http(s)` addresses are accepted. Loopback, link-local (including
the `169.254.169.254` cloud metadata address), private ranges, and non-HTTP
schemes are rejected — otherwise the sender would be a server-side request
forgery primitive (CWE-918).

This is checked twice: on registration, and again immediately before each
connection, because a hostname can resolve somewhere internal even if it
looked fine when registered. **It still does not close DNS rebinding**, where
the answer changes between check and connect. Deployments handling untrusted
tenants should egress-filter the sender process as well.
