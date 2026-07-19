# Original Spec Alignment — Audit (2026-07-14)

Comparison of the current codebase against the original build prompt
("PROMPT: Build SaaS Invoice Builder — Full Project"). Verified by inspecting
packages, changelogs, and dependency usage — not from memory.

## Verdict

**Partially aligned.** The foundation through PDF/email/i18n/dashboard is built
and in several places *exceeds* the spec. But **five spec pillars remain
unbuilt** — their DB tables and dependencies were scaffolded, then never filled
(the `audit`, `currency`, and `notification` Java packages exist as **empty
directories**; bucket4j and the WebSocket starter are on the classpath with
zero usages).

This happened because after the original prompt, the roadmap was re-sequenced
into "Phase 1 = complete existing features" and "Phase 2 = product features,"
which deferred these pillars. They are additive work — nothing needs deleting.

## Built and matches (or exceeds) the spec

| Spec area | Status |
|---|---|
| Monorepo structure, Docker Compose, CI (GH Actions) | ✅ |
| DB schema — all 9 tables via Liquibase | ✅ (changelogs 0000–0013) |
| Auth: JWT access + rotating refresh (hashed), OAuth2 Google/GitHub | ✅ |
| Multi-tenant isolation (TenantContext + per-query filter) | ✅ |
| Password policy (BCrypt-12, ≥8/upper/digit) | ✅ |
| API conventions (envelope, ProblemDetail, pagination) | ✅ |
| Customer CRUD (search, soft delete) | ✅ |
| Invoice + line items + status state machine + atomic numbering | ✅ |
| PDF: Classic + Modern templates, QR, i18n labels | ✅ |
| Email (SendGrid + SMTP), public invoice view (token, VIEWED) | ✅ |
| Overdue scheduled job | ✅ |
| i18n en/de/fr on both tiers | ✅ |
| Dashboard summary + revenue chart | ✅ (single `/dashboard` endpoint) |
| **Beyond spec:** email CC/BCC + preview + resend, reminder history, tenant settings UI + branding + logo→PDF, user roles enforcement + invite flow + Team UI, PDF template picker | ✅ extra |

## In the spec but NOT built — the gaps

| # | Gap | Spec ref | Status | Effort |
|---|---|---|---|---|
| G4 | **Rate limiting** — login 5/15min, API 100/min | Sprint 8 | ✅ DONE 2026-07-14 — Redis fixed-window (`RateLimitService` + `RateLimitFilter`); Redis now in use | S |
| G3 | **Audit trail** — service recording, `/audit-logs` API, activity UI | Sprint 7 / Phase 6 | ✅ DONE 2026-07-14 — `AuditService` records invoice/customer events; activity timeline card | M |
| G2 | **Notification module** — entity/service + `GET /notifications`, mark-read, unread-count + header dropdown | Sprint 5 | ✅ DONE 2026-07-14 — event-driven (`NotificationEvent` + AFTER_COMMIT listener), REST API, header bell with polled unread badge | M |
| G1 | **Real-time notifications (WebSocket/STOMP)** — push the notifications G2 already persists | Sprint 5 / Phase 4 | ⬜ TODO — upgrades G2's polling to STOMP push; frontend `@stomp/stompjs`+`sockjs-client` still unused. Needs the running stack to verify end-to-end | L |
| G5 | **Currency rates** — scheduled fetch, `/currencies/rates`, cache, conversion | Sprint 6 | ⬜ TODO — `currency` pkg empty; `currency_rate` table (0008) exists | M |

Minor: test coverage is a solid suite (60+ tests) but not measured to the
spec's 80%/70% targets; Redis is provisioned but only needed once G4/G5 land.

## Recommended remediation order

Highest spec-centrality and dependency-readiness first:

1. **G4 Rate limiting** (S) — closes a security gap; smallest; unblocks the "Redis is unused" smell.
2. **G3 Audit trail** (M) — `@EntityListeners` hook; pure additive; feeds an activity UI.
3. **G2 Notifications module** (M) — REST + dropdown; prerequisite for G1.
4. **G1 WebSocket real-time** (L) — layers STOMP broadcast on top of G2; lights up the unused frontend deps.
5. **G5 Currency rates** (M) — most independent; can slot anywhere.

These interleave with the remaining Phase-2 product features (P2.2 duplicate,
P2.3 catalog, P2.4 combobox, P2.5 estimates, P2.6 recurring, P2.7 payments),
which are tracked in `docs/superpowers/plans/2026-07-11-phase2-product-features.md`.
