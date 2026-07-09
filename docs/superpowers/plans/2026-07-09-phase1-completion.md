# Phase 1 Completion — PDF Templates, Tenant Settings, Auto-Overdue, User Roles

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Companion to `2026-07-08-email-sending.md` (Feature 1, shipped).

**Goal:** Finish the four remaining half-built Phase 1 features: expose PDF template selection end-to-end, build the tenant Settings page (incl. logo + branding used by the PDF), schedule the overdue sweep with reminder emails + history, and enforce user roles with invite/member management.

**Architecture:** Each feature is an independent branch merged to `main` after green tests (the pattern the user chose for Feature 1). Backend work extends the existing module layout (`tenant`, `invoice`, `user`, `email`, `pdf`) with Liquibase changelogs 0010+; no new architecture. Frontend work follows the established feature-folder + api/hooks + RHF/zod patterns.

**Tech Stack:** unchanged — Spring Boot 3.4 / Liquibase / JUnit+Mockito, React 19 / TanStack Query / RHF+zod / Vitest.

## Global Constraints

- No new dependencies on either tier.
- i18n parity: every new user-facing string in `en.json` AND `de.json` AND `fr.json`; every new backend email/PDF string in `messages.properties` + `_de` + `_fr`.
- Backend tests run via `./gradlew test` with the public-repos init-script bypass while off-VPN (see memory `user_env_artifactory`).
- Conventional commits ending with `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Branch per feature: `feat/pdf-templates`, `feat/tenant-settings`, `feat/auto-overdue`, `feat/user-roles`. Merge locally to `main` after each feature is green.
- Template whitelist lives in ONE place: `InvoicePdfGenerator.TEMPLATES = Set.of("classic", "modern")`.

---

## Feature 2 — PDF Template Selection (branch `feat/pdf-templates`)

### Task F2.1: Backend — tenant default template + template validation

**Files:** Create `backend/src/main/resources/db/changelog/0010-tenant-default-template.yaml` (add column `default_template VARCHAR(50) NOT NULL DEFAULT 'classic'`), register in `db.changelog-master.yaml`. Modify `Tenant.java` (field + accessors, default `"classic"`), `TenantResponse.java`, `TenantUpdateRequest.java` (`@Pattern(regexp = "^(classic|modern)$")`), `TenantService.update`, `InvoicePdfGenerator` (public `static final Set<String> TEMPLATES`), `InvoiceService` (`resolveTemplate(requested, tenant)` — valid requested wins, else tenant default; reject unknown with `VALIDATION_FAILED`; `create` loads tenant once for currency+template). Test: `InvoiceServiceTemplateTest` (create uses tenant default when absent; explicit template wins; unknown template → AppException).

- [ ] Failing tests → implement → green → commit.

### Task F2.2: Backend — per-request template override on PDF endpoints

**Files:** `InvoicePdfGenerator.render(invoice, tenant, customer, String templateOverride)` overload (3-arg delegates with null), `InvoiceService.renderPdf(id, templateOverride)` validating override against `TEMPLATES`, `InvoiceController` preview/pdf endpoints gain `@RequestParam(required=false) String template`. Tests added to `InvoiceServiceTemplateTest` (override respected, invalid override rejected, stored template untouched).

- [ ] Failing tests → implement → green → commit.

### Task F2.3: Frontend — template picker + preview toggle

**Files:** `api/invoices.ts` (`fetchInvoicePdf(id, mode, template?)`, `InvoicePayload.template` already exists), `types/api.ts` (no change — `template` already on Invoice), `InvoiceFormPage.tsx` (template `<select>` with classic/modern, defaulted from existing invoice on edit), `InvoicePreviewDialog.tsx` (classic/modern toggle re-fetching preview with override; download honors selection), i18n keys ×3 (`invoices.fields.template`, `invoices.template.classic`, `invoices.template.modern`). Test: extend/add Vitest for preview dialog template toggle (stub `URL.createObjectURL`).

- [ ] Failing test → implement → green → full verify (test/type-check/lint/build + backend) → commit → merge to main.

---

## Feature 3 — Tenant Settings Page (branch `feat/tenant-settings`)

### Task F3.1: Backend — branding columns

Changelog `0011-tenant-branding.yaml`: `footer_text TEXT NULL`, `payment_info TEXT NULL`, `branding_color VARCHAR(7) NULL`. Extend `Tenant`, `TenantResponse`, `TenantUpdateRequest` (`@Pattern(regexp = "^#[0-9A-Fa-f]{6}$")` for color, `@Size(max=2000)` texts), `TenantService.update`. Test: `TenantServiceTest` (update applies branding fields; TenantContext-scoped).

- [ ] Failing tests → implement → green → commit.

### Task F3.2: Backend — logo upload/serve/delete

`LogoStorage` service (tenant package; stores `${app.storage.logo-path}/{tenantId}.{png|jpg}`, max 2 MB, content-type whitelist png/jpeg; `save`, `loadOrNull(tenant)`, `delete`). `TenantController`: `POST /api/v1/tenant/logo` (multipart), `GET /api/v1/tenant/logo` (bytes + content type), `DELETE /api/v1/tenant/logo`. Sets/clears `tenant.logoPath`. Test: `LogoStorageTest` with temp dir (save/load/delete, rejects oversize + wrong type).

- [ ] Failing tests → implement → green → commit.

### Task F3.3: Backend — PDF uses branding (logo, accent color, footer, payment info)

`InvoicePdfGenerator`: accent color parsed from `tenant.getBrandingColor()` (fallback current ACCENT), logo image (byte[] param) rendered in both headers when present, payment-info block + footer text rendered above the thank-you line. `InvoiceService.renderPdf`/`sendInvoiceEmail` pass `logoStorage.loadOrNull(tenant)`. New msg keys `pdf.invoice.paymentInfo` ×3 properties files. Test: generator smoke test — render with branding produces non-empty PDF bytes and doesn't throw (no pixel assertions).

- [ ] Failing tests → implement → green → commit.

### Task F3.4: Frontend — Settings page

`api/tenant.ts` (`getTenant`, `updateTenant`, `uploadLogo`, `deleteLogo`, logo URL helper), `hooks/useTenant.ts`, `features/settings/SettingsPage.tsx` replacing the router placeholder: company info (name, currency, locale, tax id, invoice prefix), address block, default template select, branding color `<input type="color">`, footer + payment-info textareas, logo upload w/ preview + remove. RHF+zod mirroring server rules. i18n `settings.*` ×3. Test: SettingsPage submit calls `updateTenant` with form values (mock api).

- [ ] Failing test → implement → green → full verify → commit → merge to main.

---

## Feature 4 — Automatic Overdue + Reminders (branch `feat/auto-overdue`)

### Task F4.1: Backend — scheduled sweep

`InvoiceRepository.findTenantsWithOverdueCandidates(statuses, today)` (distinct tenantIds). `OverdueSweeper` component: `@Scheduled(cron = "0 15 3 * * *", zone = "UTC")` → for each tenant `invoiceService.markOverdueForTenant(tenantId, LocalDate.now(clock))`. Test: sweeper iterates tenants (mocks).

- [ ] Failing tests → implement → green → commit.

### Task F4.2: Backend — reminder emails + history

Changelog `0012-invoice-reminder.yaml`: `invoice_reminder(id UUID PK, tenant_id UUID NOT NULL, invoice_id UUID NOT NULL FK→invoice, recipient VARCHAR(255), type VARCHAR(20), sent_at TIMESTAMPTZ NOT NULL)`. Entity + repository. `markOverdueForTenant` also emails the customer a localized reminder (`email.reminder.subject/body` ×3 properties, PDF attached, reuses `sendInvoiceEmail` machinery via a type param or dedicated `sendReminderEmail`) and records `AUTO_OVERDUE`; `resend` records `MANUAL_RESEND`. `GET /api/v1/invoices/{id}/reminders` → list. Tests: overdue sweep transitions + emails + records; resend records history.

- [ ] Failing tests → implement → green → commit.

### Task F4.3: Frontend — reminder history on detail page

`api/invoices.ts` `listReminders(id)`, hook, card on `InvoiceDetailPage` (type + date + recipient), i18n ×3. Full verify → commit → merge to main.

- [ ] Implement → green → merge.

---

## Feature 5 — User Roles & Team Management (branch `feat/user-roles`)

**Permission model:** OWNER — everything incl. ownership transfer. ADMIN — tenant settings, team management (except touching the OWNER or transferring), invoices/customers. MEMBER — invoices/customers only.

### Task F5.1: Backend — member management endpoints + enforcement

`user/UserController` + `UserService`: `GET /api/v1/users` (list tenant members; OWNER/ADMIN), `PUT /api/v1/users/{id}/role` (OWNER/ADMIN; only OWNER may change an OWNER/assign OWNER — actually OWNER role assignable only via transfer), `PUT /api/v1/users/{id}/active` (deactivate/reactivate; not self, not OWNER), `POST /api/v1/users/transfer-ownership` (OWNER only; swaps roles atomically, revokes nothing). `@PreAuthorize` on controller; data-dependent rules in service (self-deactivation, owner protection) with `ErrorCode.ACCESS_DENIED`. Add `@PreAuthorize("hasAnyRole('OWNER','ADMIN')")` to `TenantController` mutating endpoints (PUT, logo POST/DELETE). New ErrorCodes if needed. Tests: `UserServiceTest` rules (cannot deactivate self/owner, cannot demote owner, transfer swaps roles).

- [ ] Failing tests → implement → green → commit.

### Task F5.2: Backend — invite flow

Changelog `0013-app-user-invite.yaml`: `invite_token_hash VARCHAR(64) UNIQUE NULL`, `invited_at TIMESTAMPTZ NULL` on `app_user`. `POST /api/v1/users/invite {email, role(ADMIN|MEMBER)}` (OWNER/ADMIN): creates inactive LOCAL user (fullName = email local-part placeholder), 256-bit token hashed like refresh tokens, sends localized invite email (`email.invite.subject/body` ×3) with link `{frontend}/invite/{token}` (base URL derived like `publicViewUrl`). Public endpoints: `GET /api/v1/public/invites/{token}` → `{email, tenantName}`; `POST /api/v1/public/invites/{token}/accept {fullName, password}` → sets name + bcrypt hash, activates, clears token. Duplicate email → `EMAIL_ALREADY_EXISTS`. Tests: invite creates inactive user + token; accept activates + clears; expired/unknown token rejected; duplicate rejected.

- [ ] Failing tests → implement → green → commit.

### Task F5.3: Frontend — Team UI + invite accept page + role-aware UI

`api/users.ts` + `hooks/useUsers.ts`. Settings page gains a Team card (OWNER/ADMIN only): member table (name, email, role badge, active), invite dialog (email + role), role select per row, deactivate toggle, transfer-ownership button (OWNER only, `window.confirm`-style typed confirm via Modal). New public route `/invite/:token` → `InviteAcceptPage` (shows tenant/email, name + password form → accept → navigate `/login` with success toast). MEMBER: settings form fields disabled, Team hidden. i18n `team.*` + `invite.*` ×3. Test: invite accept page submits, or team list renders + invite dialog posts (mock api) — at least one component test.

- [ ] Failing test → implement → green → full verify → commit → merge to main.

---

## Self-review notes

- F2 depends on nothing; F3 uses F2's `default_template` in the settings form; F4 reuses F1's email machinery; F5 touches `TenantController` last so F3's endpoints exist before guards are added.
- Spec coverage vs. user brief: F2 ✔ (expose selection, live preview, tenant default, switch before export); F3 ✔ (logo, company info, prefix, tax id, address, footer, payment info, branding color); F4 ✔ (scheduler, transition, reminder emails, reminder history); F5 ✔ (invite, role management, permission checks, member management, owner transfer).
- Deliberately out of scope: per-invoice immutable PDF archive, dunning cadence config (Phase 2), notification center (Phase 3), email verification for invites beyond the token itself.
