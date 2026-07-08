# Invoice Email Sending — Implementation Plan (Phase 1, Feature 1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the invoice send dialog honest — the backend honors recipient/subject/message overrides, gains CC/BCC, an email-preview endpoint that prefills the dialog with the exact email to be sent, and a resend action for already-sent invoices.

**Architecture:** Extend the existing `SendInvoiceRequest` DTO and wire it into `InvoiceController.send` (today the controller drops the body — the frontend already posts it). Extract the subject/body/recipient resolution inside `InvoiceService.sendInvoiceEmail` into a reusable `composeEmail` method that powers both delivery and a new `GET /email-preview` endpoint. CC/BCC flow through `EmailService.EmailMessage` into both transports (SendGrid + SMTP) with duplicate-address sanitization. The frontend `SendInvoiceDialog` is upgraded to the codebase's standard react-hook-form + zod pattern, prefilled from the preview endpoint, and reused in a new `resend` mode from the detail page.

**Tech Stack:** Java 21 / Spring Boot 3.4 (Bean Validation, MessageSource i18n), JUnit 5 + Mockito + AssertJ (all already on the test classpath via `spring-boot-starter-test`), React 19 + react-hook-form + zod + TanStack Query, Vitest + Testing Library (already configured in `vite.config.ts`).

## Global Constraints

- **No new dependencies** on either tier — everything needed is already declared.
- **This repo currently has ZERO tests.** `backend/src/test/java` does not exist yet; `frontend/src` has no `*.test.*` files. Tasks 1 and 5 bootstrap the respective test trees. `useJUnitPlatform()` and Vitest are already configured — just add files.
- Backend conventions: constructor injection, `record` DTOs in `dto/` subpackages, `ApiResponse.of(...)` envelope, `AppException(ErrorCode, msg)` for errors, thin controllers.
- Frontend conventions: API functions in `src/api/*.ts` returning `res.data.data`, TanStack Query hooks in `src/hooks/*.ts` with `qc.invalidateQueries({ queryKey: KEY })`, react-hook-form + zod for forms, i18n via `useTranslation()`.
- **i18n parity:** every new user-facing string must be added to `en.json`, `de.json`, AND `fr.json`. (de/fr are currently missing 16 existing keys — Task 6 backfills them.)
- Commit style: conventional commits (`feat(scope): ...`, `test(scope): ...`). Every commit message ends with:
  `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`
- Server is authoritative: email validation happens on BOTH tiers (zod client-side, Bean Validation server-side).
- Run backend tests with `cd backend && ./gradlew test`; frontend with `cd frontend && pnpm test:run`.

---

### Task 1: EmailService — CC/BCC support with duplicate sanitization

**Files:**
- Modify: `backend/src/main/java/com/invoicebuilder/email/EmailService.java`
- Modify: `backend/src/main/java/com/invoicebuilder/invoice/InvoiceService.java:221-223` (single call site of `EmailMessage` — constructor gains two params)
- Test: `backend/src/test/java/com/invoicebuilder/email/EmailServiceTest.java` (new — first backend test file; create the directory tree)

**Interfaces:**
- Consumes: existing `EmailService`, `AppProperties` records.
- Produces: `EmailService.EmailMessage(String toEmail, String toName, List<String> cc, List<String> bcc, String subject, String plainTextBody, String attachmentName, byte[] attachmentBytes)` — **field order matters**; `cc`/`bcc` may be passed `null` (normalized to empty). Task 2's service code and test rely on this exact signature.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/invoicebuilder/email/EmailServiceTest.java`:

```java
package com.invoicebuilder.email;

import com.invoicebuilder.config.AppProperties;
import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService service;
    private MimeMessage mime;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        // Blank SendGrid API key forces the SMTP path.
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("0123456789abcdef0123456789abcdef", Duration.ofMinutes(15), Duration.ofDays(7), "test"),
                null,
                new AppProperties.Sendgrid("", "noreply@test.local", "Invoice Builder Test"),
                new AppProperties.Storage(Path.of("./build/test-storage"), Path.of("./build/test-storage")),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.RateLimit(5, Duration.ofMinutes(15), 100));
        service = new EmailService(mailSender, properties);
        mime = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mime);
    }

    @Test
    void smtpDeliverySetsCcAndBcc() throws Exception {
        service.send(new EmailService.EmailMessage(
                "to@example.com", "To Person",
                List.of("cc@example.com"), List.of("bcc@example.com"),
                "Subject", "Body", null, null));

        verify(mailSender).send(mime);
        assertThat(mime.getRecipients(Message.RecipientType.CC))
                .extracting(Address::toString).containsExactly("cc@example.com");
        assertThat(mime.getRecipients(Message.RecipientType.BCC))
                .extracting(Address::toString).containsExactly("bcc@example.com");
    }

    @Test
    void ccAndBccAreDedupedAgainstToAndEachOther() throws Exception {
        service.send(new EmailService.EmailMessage(
                "to@example.com", "To Person",
                List.of("cc@example.com", "TO@example.com", "cc@example.com", "  "),
                List.of("bcc@example.com", "CC@example.com"),
                "Subject", "Body", null, null));

        assertThat(mime.getRecipients(Message.RecipientType.CC))
                .extracting(Address::toString).containsExactly("cc@example.com");
        assertThat(mime.getRecipients(Message.RecipientType.BCC))
                .extracting(Address::toString).containsExactly("bcc@example.com");
    }

    @Test
    void nullCcAndBccAreNormalizedToEmpty() throws Exception {
        service.send(new EmailService.EmailMessage(
                "to@example.com", "To Person", null, null,
                "Subject", "Body", null, null));

        verify(mailSender).send(mime);
        assertThat(mime.getRecipients(Message.RecipientType.CC)).isNull();
        assertThat(mime.getRecipients(Message.RecipientType.BCC)).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests 'com.invoicebuilder.email.EmailServiceTest'`
Expected: COMPILATION FAILURE — `EmailMessage` has no 8-arg constructor with `List` params.

- [ ] **Step 3: Implement — extend `EmailMessage`, sanitize, wire both transports**

In `EmailService.java`, replace the `EmailMessage` record and `send` method, and extend both transport methods.

Add imports:

```java
import com.sendgrid.helpers.mail.objects.Personalization;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
```

Replace the record:

```java
    public record EmailMessage(
            String toEmail,
            String toName,
            List<String> cc,
            List<String> bcc,
            String subject,
            String plainTextBody,
            String attachmentName,
            byte[] attachmentBytes
    ) {
        public EmailMessage {
            cc = cc == null ? List.of() : List.copyOf(cc);
            bcc = bcc == null ? List.of() : List.copyOf(bcc);
        }
    }
```

Replace `send` and add the sanitization helpers:

```java
    public void send(EmailMessage message) {
        EmailMessage sanitized = sanitize(message);
        if (StringUtils.hasText(properties.sendgrid().apiKey())) {
            sendViaSendGrid(sanitized);
        } else {
            sendViaSmtp(sanitized);
        }
    }

    /**
     * Drops cc/bcc entries that are blank or duplicate the to-address or an
     * earlier entry (SendGrid rejects the same address across to/cc/bcc).
     */
    private static EmailMessage sanitize(EmailMessage m) {
        Set<String> seen = new HashSet<>();
        seen.add(m.toEmail().trim().toLowerCase(Locale.ROOT));
        List<String> cc = dedupe(m.cc(), seen);
        List<String> bcc = dedupe(m.bcc(), seen);
        return new EmailMessage(m.toEmail(), m.toName(), cc, bcc,
                m.subject(), m.plainTextBody(), m.attachmentName(), m.attachmentBytes());
    }

    private static List<String> dedupe(List<String> emails, Set<String> seen) {
        List<String> out = new ArrayList<>();
        for (String email : emails) {
            if (email == null || email.isBlank()) {
                continue;
            }
            String trimmed = email.trim();
            if (seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                out.add(trimmed);
            }
        }
        return out;
    }
```

In `sendViaSendGrid`, after the `Mail mail = new Mail(...)` line, add:

```java
        Personalization personalization = mail.getPersonalization().get(0);
        message.cc().forEach(addr -> personalization.addCc(new Email(addr)));
        message.bcc().forEach(addr -> personalization.addBcc(new Email(addr)));
```

In `sendViaSmtp`, after `helper.setTo(message.toEmail());`, add:

```java
            if (!message.cc().isEmpty()) {
                helper.setCc(message.cc().toArray(String[]::new));
            }
            if (!message.bcc().isEmpty()) {
                helper.setBcc(message.bcc().toArray(String[]::new));
            }
```

Update the single call site in `InvoiceService.sendInvoiceEmail` (currently lines 221-223) to the new arity — pass empty lists for now (Task 2 threads real values):

```java
        emailService.send(new EmailService.EmailMessage(
                recipient, customer.getName(), List.of(), List.of(), subject, body,
                "invoice-" + invoice.getInvoiceNumber() + ".pdf", pdf));
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests 'com.invoicebuilder.email.EmailServiceTest'`
Expected: 3 tests PASS. Also run `./gradlew compileJava` — BUILD SUCCESSFUL (call-site updated).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/invoicebuilder/email/EmailService.java \
        backend/src/main/java/com/invoicebuilder/invoice/InvoiceService.java \
        backend/src/test/java/com/invoicebuilder/email/EmailServiceTest.java
git commit -m "feat(email): CC/BCC support with duplicate sanitization in both transports

First backend test file — bootstraps backend/src/test.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Honor SendInvoiceRequest on /send (the dropped-payload bug)

**Files:**
- Modify: `backend/src/main/java/com/invoicebuilder/invoice/dto/SendInvoiceRequest.java`
- Modify: `backend/src/main/java/com/invoicebuilder/invoice/InvoiceController.java:84-88`
- Modify: `backend/src/main/java/com/invoicebuilder/invoice/InvoiceService.java` (delete the `send(UUID)` overload at lines 185-189; thread cc/bcc; extract tenant/customer loaders)
- Test: `backend/src/test/java/com/invoicebuilder/invoice/InvoiceServiceSendTest.java` (new)

**Interfaces:**
- Consumes: `EmailService.EmailMessage` 8-arg constructor from Task 1.
- Produces:
  - `SendInvoiceRequest(String recipientEmail, List<String> cc, List<String> bcc, String subject, String message, Boolean skipEmail)` with helpers `ccOrEmpty()`, `bccOrEmpty()`, `isEmailSkipped()`.
  - `InvoiceService.send(UUID id, SendInvoiceRequest request)` — the ONLY send method (overload removed); `request` may be null.
  - Private helpers `loadTenant(UUID)` and `loadCustomer(UUID)` on `InvoiceService` (Task 3 reuses them).
  - Endpoint contract: `POST /api/v1/invoices/{id}/send` accepts an optional JSON body.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/invoicebuilder/invoice/InvoiceServiceSendTest.java`:

```java
package com.invoicebuilder.invoice;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.customer.CustomerRepository;
import com.invoicebuilder.email.EmailService;
import com.invoicebuilder.invoice.dto.SendInvoiceRequest;
import com.invoicebuilder.pdf.InvoicePdfGenerator;
import com.invoicebuilder.pdf.PdfStorage;
import com.invoicebuilder.tenant.Tenant;
import com.invoicebuilder.tenant.TenantContext;
import com.invoicebuilder.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceSendTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final byte[] PDF = {1, 2, 3};
    private static final Instant NOW = Instant.parse("2026-07-08T12:00:00Z");

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private InvoiceNumberGenerator numberGenerator;
    @Mock private InvoiceCalculator calculator;
    @Mock private InvoicePdfGenerator pdfGenerator;
    @Mock private PdfStorage pdfStorage;
    @Mock private EmailService emailService;
    @Mock private MessageSource messages;

    private InvoiceService service;
    private Invoice invoice;
    private Tenant tenant;
    private Customer customer;

    @BeforeEach
    void setUp() {
        service = new InvoiceService(invoiceRepository, customerRepository, tenantRepository,
                numberGenerator, calculator, pdfGenerator, pdfStorage, emailService, messages,
                Clock.fixed(NOW, ZoneOffset.UTC));
        TenantContext.set(TENANT_ID);

        invoice = new Invoice();
        invoice.setTenantId(TENANT_ID);
        invoice.setCustomerId(CUSTOMER_ID);
        invoice.setInvoiceNumber("INV-2026-0001");
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setIssueDate(LocalDate.of(2026, 7, 1));
        invoice.setDueDate(LocalDate.of(2026, 8, 1));

        tenant = new Tenant();
        tenant.setName("Acme GmbH");
        tenant.setDefaultLocale("en");

        customer = new Customer();
        customer.setName("Widget Co");
        customer.setEmail("billing@widget.example");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubInvoiceLookup() {
        when(invoiceRepository.findByIdAndTenantId(INVOICE_ID, TENANT_ID))
                .thenReturn(Optional.of(invoice));
    }

    private void stubParties() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
    }

    private void stubPdf() {
        when(pdfGenerator.render(invoice, tenant, customer)).thenReturn(PDF);
    }

    private void stubDefaultMessages() {
        when(messages.getMessage(eq("email.invoice.subject"), any(), any(Locale.class)))
                .thenReturn("Invoice INV-2026-0001 from Acme GmbH");
        when(messages.getMessage(eq("email.invoice.bodyDefault"), any(), any(Locale.class)))
                .thenReturn("Hi, please find your invoice attached.");
    }

    @Test
    void sendAppliesCustomRecipientSubjectMessageAndCcBcc() {
        stubInvoiceLookup();
        stubParties();
        stubPdf();
        SendInvoiceRequest request = new SendInvoiceRequest(
                "custom@example.com", List.of("cc@example.com"), List.of("bcc@example.com"),
                "Custom subject", "Custom message", null);

        service.send(INVOICE_ID, request);

        ArgumentCaptor<EmailService.EmailMessage> captor =
                ArgumentCaptor.forClass(EmailService.EmailMessage.class);
        verify(emailService).send(captor.capture());
        EmailService.EmailMessage sent = captor.getValue();
        assertThat(sent.toEmail()).isEqualTo("custom@example.com");
        assertThat(sent.cc()).containsExactly("cc@example.com");
        assertThat(sent.bcc()).containsExactly("bcc@example.com");
        assertThat(sent.subject()).isEqualTo("Custom subject");
        assertThat(sent.plainTextBody()).isEqualTo("Custom message");
        assertThat(sent.attachmentName()).isEqualTo("invoice-INV-2026-0001.pdf");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
        assertThat(invoice.getSentAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(invoice.getPublicToken()).isNotBlank();
    }

    @Test
    void sendFallsBackToCustomerEmailAndLocalizedDefaults() {
        stubInvoiceLookup();
        stubParties();
        stubPdf();
        stubDefaultMessages();

        service.send(INVOICE_ID, null);

        ArgumentCaptor<EmailService.EmailMessage> captor =
                ArgumentCaptor.forClass(EmailService.EmailMessage.class);
        verify(emailService).send(captor.capture());
        assertThat(captor.getValue().toEmail()).isEqualTo("billing@widget.example");
        assertThat(captor.getValue().subject()).isEqualTo("Invoice INV-2026-0001 from Acme GmbH");
        assertThat(captor.getValue().cc()).isEmpty();
    }

    @Test
    void sendWithSkipEmailTransitionsWithoutSending() {
        stubInvoiceLookup();
        SendInvoiceRequest request = new SendInvoiceRequest(null, null, null, null, null, true);

        service.send(INVOICE_ID, request);

        verifyNoInteractions(emailService);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    void sendSilentlySkipsEmailWhenNoRecipientAvailable() {
        stubInvoiceLookup();
        stubParties();
        stubDefaultMessages();
        customer.setEmail(null);

        service.send(INVOICE_ID, null);

        verifyNoInteractions(emailService);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    void sendRejectsNonDraftInvoice() {
        stubInvoiceLookup();
        invoice.setStatus(InvoiceStatus.PAID);

        assertThatThrownBy(() -> service.send(INVOICE_ID, null))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(emailService);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests 'com.invoicebuilder.invoice.InvoiceServiceSendTest'`
Expected: COMPILATION FAILURE — `SendInvoiceRequest` has no 6-arg constructor (no cc/bcc yet).

- [ ] **Step 3: Implement**

**(a)** Replace `backend/src/main/java/com/invoicebuilder/invoice/dto/SendInvoiceRequest.java`:

```java
package com.invoicebuilder.invoice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Optional overrides for the generated email when sending an invoice. Any
 * absent field falls back to a localized default rendered from the i18n
 * bundle ({@code email.invoice.subject} / {@code email.invoice.bodyDefault}).
 */
public record SendInvoiceRequest(
        @Email @Size(max = 255) String recipientEmail,
        @Size(max = 10) List<@Email @Size(max = 255) String> cc,
        @Size(max = 10) List<@Email @Size(max = 255) String> bcc,
        @Size(max = 255) String subject,
        @Size(max = 4000) String message,
        Boolean skipEmail
) {
    public boolean isEmailSkipped() {
        return Boolean.TRUE.equals(skipEmail);
    }

    public List<String> ccOrEmpty() {
        return cc == null ? List.of() : cc;
    }

    public List<String> bccOrEmpty() {
        return bcc == null ? List.of() : bcc;
    }
}
```

**(b)** In `InvoiceController.java`, replace the send endpoint (lines 84-88):

```java
    @PostMapping("/{id}/send")
    @Operation(summary = "Send invoice (DRAFT → SENT) with optional email overrides")
    public ApiResponse<InvoiceResponse> send(@PathVariable UUID id,
                                             @Valid @RequestBody(required = false) SendInvoiceRequest request) {
        return ApiResponse.of(InvoiceResponse.from(invoiceService.send(id, request)));
    }
```

Add import: `import com.invoicebuilder.invoice.dto.SendInvoiceRequest;`

**(c)** In `InvoiceService.java`:

Delete the convenience overload (lines 185-189):

```java
    /** Convenience overload for callers that don't pass an email payload. */
    @Transactional
    public Invoice send(UUID id) {
        return send(id, null);
    }
```

Replace `sendInvoiceEmail` (lines 191-224) with a version using extracted loaders and threading cc/bcc:

```java
    private void sendInvoiceEmail(Invoice invoice, SendInvoiceRequest request) {
        Tenant tenant = loadTenant(invoice.getTenantId());
        Customer customer = loadCustomer(invoice.getCustomerId());

        String recipient = request != null && request.recipientEmail() != null && !request.recipientEmail().isBlank()
                ? request.recipientEmail()
                : customer.getEmail();
        if (recipient == null || recipient.isBlank()) {
            // Customer has no email and caller didn't override — silently skip.
            return;
        }

        Locale locale = Locale.forLanguageTag(
                tenant.getDefaultLocale() == null ? "en" : tenant.getDefaultLocale());
        String subject = request != null && request.subject() != null && !request.subject().isBlank()
                ? request.subject()
                : messages.getMessage("email.invoice.subject",
                        new Object[]{invoice.getInvoiceNumber(), tenant.getName()},
                        locale);
        String body = request != null && request.message() != null && !request.message().isBlank()
                ? request.message()
                : messages.getMessage("email.invoice.bodyDefault",
                        new Object[]{invoice.getInvoiceNumber(), invoice.getDueDate(), tenant.getName()},
                        locale);

        byte[] pdf = pdfGenerator.render(invoice, tenant, customer);
        pdfStorage.save(invoice.getTenantId(), invoice.getId(), pdf);

        emailService.send(new EmailService.EmailMessage(
                recipient, customer.getName(),
                request == null ? List.of() : request.ccOrEmpty(),
                request == null ? List.of() : request.bccOrEmpty(),
                subject, body,
                "invoice-" + invoice.getInvoiceNumber() + ".pdf", pdf));
    }
```

Add the two loaders next to the existing `load(UUID)` helper (they replace the inline tenant/customer lookups):

```java
    private Tenant loadTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND, "Tenant not found"));
    }

    private Customer loadCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer not found"));
    }
```

Also update `renderPdf` (lines 78-87) to use the new loaders (deletes its duplicated lookups):

```java
    @Transactional(readOnly = true)
    public byte[] renderPdf(UUID id) {
        Invoice invoice = load(id);
        Tenant tenant = loadTenant(invoice.getTenantId());
        Customer customer = loadCustomer(invoice.getCustomerId());
        byte[] pdf = pdfGenerator.render(invoice, tenant, customer);
        pdfStorage.save(invoice.getTenantId(), invoice.getId(), pdf);
        return pdf;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test`
Expected: `EmailServiceTest` (3) + `InvoiceServiceSendTest` (5) all PASS. BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/invoicebuilder/invoice/ \
        backend/src/test/java/com/invoicebuilder/invoice/InvoiceServiceSendTest.java
git commit -m "fix(invoice): honor send-dialog payload — recipient, subject, message, CC/BCC

The controller previously dropped the request body; every custom field
the user typed in the send dialog was silently discarded.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Email preview endpoint (GET /invoices/{id}/email-preview)

**Files:**
- Modify: `backend/src/main/java/com/invoicebuilder/invoice/InvoiceService.java` (extract `composeEmail`, add `previewEmail`)
- Create: `backend/src/main/java/com/invoicebuilder/invoice/dto/EmailPreviewResponse.java`
- Modify: `backend/src/main/java/com/invoicebuilder/invoice/InvoiceController.java` (new GET endpoint)
- Test: `backend/src/test/java/com/invoicebuilder/invoice/InvoiceServiceSendTest.java` (add 2 tests)

**Interfaces:**
- Consumes: `loadTenant`/`loadCustomer` from Task 2.
- Produces:
  - `InvoiceService.EmailPreview(String recipientEmail, String subject, String body)` (nested public record).
  - `InvoiceService.previewEmail(UUID id)` → `EmailPreview` (tenant-scoped, read-only).
  - Endpoint: `GET /api/v1/invoices/{id}/email-preview` → `ApiResponse<EmailPreviewResponse>` with fields `recipientEmail` (nullable), `subject`, `body`. Task 5's frontend types mirror this shape exactly.

- [ ] **Step 1: Add failing tests**

Append to `InvoiceServiceSendTest.java`:

```java
    @Test
    void previewEmailReturnsCustomerRecipientAndLocalizedDefaults() {
        stubInvoiceLookup();
        stubParties();
        stubDefaultMessages();

        InvoiceService.EmailPreview preview = service.previewEmail(INVOICE_ID);

        assertThat(preview.recipientEmail()).isEqualTo("billing@widget.example");
        assertThat(preview.subject()).isEqualTo("Invoice INV-2026-0001 from Acme GmbH");
        assertThat(preview.body()).isEqualTo("Hi, please find your invoice attached.");
        verifyNoInteractions(emailService, pdfGenerator);
    }

    @Test
    void previewEmailReturnsNullRecipientWhenCustomerHasNoEmail() {
        stubInvoiceLookup();
        stubParties();
        stubDefaultMessages();
        customer.setEmail(null);

        InvoiceService.EmailPreview preview = service.previewEmail(INVOICE_ID);

        assertThat(preview.recipientEmail()).isNull();
        assertThat(preview.subject()).isNotBlank();
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && ./gradlew test --tests 'com.invoicebuilder.invoice.InvoiceServiceSendTest'`
Expected: COMPILATION FAILURE — `previewEmail` / `EmailPreview` not defined.

- [ ] **Step 3: Implement**

**(a)** In `InvoiceService.java`, add the record + public method, and refactor `sendInvoiceEmail` to delegate to a shared `composeEmail` (this removes the duplication just introduced — the resolution block moves wholesale):

```java
    /** Resolved email content — what will actually be sent for this invoice. */
    public record EmailPreview(String recipientEmail, String subject, String body) {
    }

    @Transactional(readOnly = true)
    public EmailPreview previewEmail(UUID id) {
        Invoice invoice = load(id);
        Tenant tenant = loadTenant(invoice.getTenantId());
        Customer customer = loadCustomer(invoice.getCustomerId());
        return composeEmail(invoice, tenant, customer, null);
    }

    /**
     * Resolves recipient, subject, and body: explicit request values win,
     * otherwise localized defaults from the tenant's locale bundle.
     */
    private EmailPreview composeEmail(Invoice invoice, Tenant tenant, Customer customer,
                                      SendInvoiceRequest request) {
        String recipient = request != null && request.recipientEmail() != null && !request.recipientEmail().isBlank()
                ? request.recipientEmail()
                : customer.getEmail();

        Locale locale = Locale.forLanguageTag(
                tenant.getDefaultLocale() == null ? "en" : tenant.getDefaultLocale());
        String subject = request != null && request.subject() != null && !request.subject().isBlank()
                ? request.subject()
                : messages.getMessage("email.invoice.subject",
                        new Object[]{invoice.getInvoiceNumber(), tenant.getName()},
                        locale);
        String body = request != null && request.message() != null && !request.message().isBlank()
                ? request.message()
                : messages.getMessage("email.invoice.bodyDefault",
                        new Object[]{invoice.getInvoiceNumber(), invoice.getDueDate(), tenant.getName()},
                        locale);
        return new EmailPreview(recipient, subject, body);
    }

    private void sendInvoiceEmail(Invoice invoice, SendInvoiceRequest request) {
        Tenant tenant = loadTenant(invoice.getTenantId());
        Customer customer = loadCustomer(invoice.getCustomerId());
        EmailPreview content = composeEmail(invoice, tenant, customer, request);
        if (content.recipientEmail() == null || content.recipientEmail().isBlank()) {
            // Customer has no email and caller didn't override — silently skip.
            return;
        }

        byte[] pdf = pdfGenerator.render(invoice, tenant, customer);
        pdfStorage.save(invoice.getTenantId(), invoice.getId(), pdf);

        emailService.send(new EmailService.EmailMessage(
                content.recipientEmail(), customer.getName(),
                request == null ? List.of() : request.ccOrEmpty(),
                request == null ? List.of() : request.bccOrEmpty(),
                content.subject(), content.body(),
                "invoice-" + invoice.getInvoiceNumber() + ".pdf", pdf));
    }
```

**(b)** Create `backend/src/main/java/com/invoicebuilder/invoice/dto/EmailPreviewResponse.java`:

```java
package com.invoicebuilder.invoice.dto;

import com.invoicebuilder.invoice.InvoiceService;

/** Default email content for an invoice, used to prefill the send dialog. */
public record EmailPreviewResponse(String recipientEmail, String subject, String body) {

    public static EmailPreviewResponse from(InvoiceService.EmailPreview preview) {
        return new EmailPreviewResponse(preview.recipientEmail(), preview.subject(), preview.body());
    }
}
```

**(c)** In `InvoiceController.java`, add after the `previewPdf` endpoint:

```java
    @GetMapping("/{id}/email-preview")
    @Operation(summary = "Preview the invoice email content (recipient, subject, body)")
    public ApiResponse<EmailPreviewResponse> emailPreview(@PathVariable UUID id) {
        return ApiResponse.of(EmailPreviewResponse.from(invoiceService.previewEmail(id)));
    }
```

Add import: `import com.invoicebuilder.invoice.dto.EmailPreviewResponse;`

- [ ] **Step 4: Run all backend tests**

Run: `cd backend && ./gradlew test`
Expected: 10 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/invoicebuilder/invoice/ \
        backend/src/test/java/com/invoicebuilder/invoice/InvoiceServiceSendTest.java
git commit -m "feat(invoice): email-preview endpoint returning resolved recipient/subject/body

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Resend endpoint (POST /invoices/{id}/resend)

**Files:**
- Modify: `backend/src/main/java/com/invoicebuilder/invoice/InvoiceService.java` (add `resend`)
- Modify: `backend/src/main/java/com/invoicebuilder/invoice/InvoiceController.java` (add endpoint)
- Test: `backend/src/test/java/com/invoicebuilder/invoice/InvoiceServiceSendTest.java` (add 3 tests)

**Interfaces:**
- Consumes: `sendInvoiceEmail` from Task 3.
- Produces:
  - `InvoiceService.resend(UUID id, SendInvoiceRequest request)` → `Invoice`. Allowed only from SENT/VIEWED/OVERDUE; throws `AppException(ErrorCode.INVALID_STATE_TRANSITION)` otherwise. Never mutates status or `sentAt`.
  - Endpoint: `POST /api/v1/invoices/{id}/resend` with optional `SendInvoiceRequest` body → `ApiResponse<InvoiceResponse>`. Task 5's `resendInvoice` API fn targets this.

- [ ] **Step 1: Add failing tests**

Append to `InvoiceServiceSendTest.java`:

```java
    @Test
    void resendDeliversEmailWithoutChangingStatusOrSentAt() {
        stubInvoiceLookup();
        stubParties();
        stubPdf();
        stubDefaultMessages();
        invoice.setStatus(InvoiceStatus.VIEWED);
        invoice.setPublicToken("existing-token");
        OffsetDateTime originalSentAt = OffsetDateTime.parse("2026-07-01T09:00:00Z");
        invoice.setSentAt(originalSentAt);

        service.resend(INVOICE_ID, null);

        verify(emailService).send(any(EmailService.EmailMessage.class));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VIEWED);
        assertThat(invoice.getSentAt()).isEqualTo(originalSentAt);
        assertThat(invoice.getPublicToken()).isEqualTo("existing-token");
    }

    @Test
    void resendAppliesEmailOverrides() {
        stubInvoiceLookup();
        stubParties();
        stubPdf();
        invoice.setStatus(InvoiceStatus.OVERDUE);
        SendInvoiceRequest request = new SendInvoiceRequest(
                "reminder@example.com", null, null, "Reminder", "Please pay.", null);

        service.resend(INVOICE_ID, request);

        ArgumentCaptor<EmailService.EmailMessage> captor =
                ArgumentCaptor.forClass(EmailService.EmailMessage.class);
        verify(emailService).send(captor.capture());
        assertThat(captor.getValue().toEmail()).isEqualTo("reminder@example.com");
        assertThat(captor.getValue().subject()).isEqualTo("Reminder");
    }

    @Test
    void resendRejectsDraftAndTerminalStatuses() {
        stubInvoiceLookup();
        for (InvoiceStatus status : List.of(InvoiceStatus.DRAFT, InvoiceStatus.PAID, InvoiceStatus.CANCELLED)) {
            invoice.setStatus(status);
            assertThatThrownBy(() -> service.resend(INVOICE_ID, null))
                    .isInstanceOf(AppException.class);
        }
        verifyNoInteractions(emailService);
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && ./gradlew test --tests 'com.invoicebuilder.invoice.InvoiceServiceSendTest'`
Expected: COMPILATION FAILURE — `resend` not defined.

- [ ] **Step 3: Implement**

**(a)** In `InvoiceService.java`, add after `send(...)` (imports needed: `java.util.EnumSet`, `java.util.Set`):

```java
    private static final Set<InvoiceStatus> RESENDABLE =
            EnumSet.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.OVERDUE);

    /** Re-delivers the invoice email without touching status, sentAt, or the public token. */
    @Transactional
    public Invoice resend(UUID id, SendInvoiceRequest request) {
        Invoice invoice = load(id);
        if (!RESENDABLE.contains(invoice.getStatus())) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only sent, viewed, or overdue invoices can be resent");
        }
        sendInvoiceEmail(invoice, request);
        return invoice;
    }
```

**(b)** In `InvoiceController.java`, add after the send endpoint:

```java
    @PostMapping("/{id}/resend")
    @Operation(summary = "Resend the invoice email (status unchanged)")
    public ApiResponse<InvoiceResponse> resend(@PathVariable UUID id,
                                               @Valid @RequestBody(required = false) SendInvoiceRequest request) {
        return ApiResponse.of(InvoiceResponse.from(invoiceService.resend(id, request)));
    }
```

- [ ] **Step 4: Run all backend tests**

Run: `cd backend && ./gradlew test`
Expected: 13 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/invoicebuilder/invoice/ \
        backend/src/test/java/com/invoicebuilder/invoice/InvoiceServiceSendTest.java
git commit -m "feat(invoice): resend endpoint for SENT/VIEWED/OVERDUE invoices

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: Frontend plumbing — email-list helper, API functions, hooks

**Files:**
- Create: `frontend/src/lib/email.ts`
- Test: `frontend/src/lib/email.test.ts` (new — first frontend test file)
- Modify: `frontend/src/api/invoices.ts` (extend `SendInvoicePayload`, add `getEmailPreview`, `resendInvoice`)
- Modify: `frontend/src/hooks/useInvoices.ts` (add `useEmailPreview`, `useResendInvoice`)

**Interfaces:**
- Consumes: endpoints from Tasks 3 & 4.
- Produces (Task 6 relies on these exact names):
  - `parseEmailList(input: string): string[]` — splits on `,`/`;`, trims, drops blanks, dedupes case-insensitively (keeps first casing).
  - `findInvalidEmail(emails: string[]): string | null` — first invalid address or null.
  - `interface EmailPreview { recipientEmail: string | null; subject: string; body: string }` exported from `@/api/invoices`.
  - `SendInvoicePayload` gains `cc?: string[]; bcc?: string[]`.
  - `getEmailPreview(id: string): Promise<EmailPreview>`, `resendInvoice(id: string, payload?: SendInvoicePayload): Promise<Invoice>`.
  - `useEmailPreview(id: string, enabled: boolean)`, `useResendInvoice()` (mutation arg shape `{ id, payload? }`, same as `useSendInvoice`).

- [ ] **Step 1: Write the failing test**

Create `frontend/src/lib/email.test.ts`:

```ts
import { describe, expect, it } from 'vitest';
import { findInvalidEmail, parseEmailList } from './email';

describe('parseEmailList', () => {
  it('splits on commas and semicolons and trims whitespace', () => {
    expect(parseEmailList(' a@x.io , b@x.io ;c@x.io ')).toEqual(['a@x.io', 'b@x.io', 'c@x.io']);
  });

  it('drops blanks and dedupes case-insensitively keeping first casing', () => {
    expect(parseEmailList('A@x.io,, a@x.io ,')).toEqual(['A@x.io']);
  });

  it('returns empty array for empty input', () => {
    expect(parseEmailList('')).toEqual([]);
  });
});

describe('findInvalidEmail', () => {
  it('returns null when all addresses are valid', () => {
    expect(findInvalidEmail(['a@x.io', 'b@y.co'])).toBeNull();
  });

  it('returns the first invalid address', () => {
    expect(findInvalidEmail(['a@x.io', 'not-an-email', 'also bad'])).toBe('not-an-email');
  });

  it('returns null for empty list', () => {
    expect(findInvalidEmail([])).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm test:run src/lib/email.test.ts`
Expected: FAIL — `Cannot find module './email'`.

- [ ] **Step 3: Implement**

**(a)** Create `frontend/src/lib/email.ts`:

```ts
import { z } from 'zod';

const emailSchema = z.string().email();

/**
 * Splits a comma/semicolon-separated input into trimmed, case-insensitively
 * deduplicated email strings (first casing wins). Does NOT validate — pair
 * with findInvalidEmail.
 */
export function parseEmailList(input: string): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of input.split(/[,;]/)) {
    const email = raw.trim();
    if (!email) continue;
    const key = email.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(email);
  }
  return out;
}

/** Returns the first syntactically invalid address, or null if all pass. */
export function findInvalidEmail(emails: string[]): string | null {
  for (const email of emails) {
    if (!emailSchema.safeParse(email).success) return email;
  }
  return null;
}
```

**(b)** In `frontend/src/api/invoices.ts`, replace `SendInvoicePayload` and add below `sendInvoice`:

```ts
export interface SendInvoicePayload {
  recipientEmail?: string;
  cc?: string[];
  bcc?: string[];
  subject?: string;
  message?: string;
  skipEmail?: boolean;
}

export interface EmailPreview {
  recipientEmail: string | null;
  subject: string;
  body: string;
}

export async function getEmailPreview(id: string): Promise<EmailPreview> {
  const res = await api.get<ApiEnvelope<EmailPreview>>(`/api/v1/invoices/${id}/email-preview`);
  return res.data.data;
}

export async function resendInvoice(id: string, payload?: SendInvoicePayload): Promise<Invoice> {
  const res = await api.post<ApiEnvelope<Invoice>>(`/api/v1/invoices/${id}/resend`, payload ?? {});
  return res.data.data;
}
```

**(c)** In `frontend/src/hooks/useInvoices.ts`, extend the import from `@/api/invoices` with `getEmailPreview, resendInvoice` and add:

```ts
export function useEmailPreview(id: string, enabled: boolean) {
  return useQuery({
    queryKey: [...KEY, 'email-preview', id],
    queryFn: () => getEmailPreview(id),
    enabled,
  });
}

export function useResendInvoice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; payload?: SendInvoicePayload }) =>
      resendInvoice(args.id, args.payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  });
}
```

- [ ] **Step 4: Verify**

Run: `cd frontend && pnpm test:run src/lib/email.test.ts && pnpm type-check`
Expected: 6 tests PASS; type-check clean.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/email.ts frontend/src/lib/email.test.ts \
        frontend/src/api/invoices.ts frontend/src/hooks/useInvoices.ts
git commit -m "feat(frontend): email-list parsing helper, preview/resend API + hooks

First frontend test file — bootstraps Vitest usage.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: SendInvoiceDialog — RHF+zod, CC/BCC, preview prefill, resend mode + i18n

**Files:**
- Modify: `frontend/src/features/invoices/SendInvoiceDialog.tsx` (full rewrite below)
- Modify: `frontend/src/i18n/locales/en.json`, `de.json`, `fr.json`
- Test: `frontend/src/features/invoices/SendInvoiceDialog.test.tsx` (new)

**Interfaces:**
- Consumes: Task 5's helpers/hooks.
- Produces: `SendInvoiceDialog` props — `{ open, onClose, invoiceId, invoiceNumber, defaultRecipient, mode?: 'send' | 'resend', onSent?: () => void }`. `mode` defaults to `'send'`; `onSent` is now optional. Task 7's detail page relies on this.

- [ ] **Step 1: i18n keys (all three locales)**

In `en.json`, inside `invoices.fields` add after `"messagePlaceholder"`:

```json
   "cc": "CC",
   "bcc": "BCC",
   "ccBccHint": "Separate multiple addresses with commas.",
   "invalidEmailList": "One or more email addresses are invalid.",
   "invalidRecipient": "Enter a valid email address."
```

In `en.json`, inside `invoices.actions` add: `"resend": "Resend"`.
In `en.json`, at the `invoices` level add after `"sending"`:

```json
  "resent": "Invoice email resent.",
  "resendTitle": "Resend {{number}}",
  "resendSubtitle": "Send the invoice email again — the status won't change."
```

In `de.json`: **backfill the 16 missing keys** and add the new ones. Inside `invoices.fields` add:

```json
   "recipient": "Empfänger-E-Mail",
   "recipientHint": "Standardmäßig die E-Mail-Adresse des Kunden, wenn leer.",
   "subject": "Betreff",
   "subjectPlaceholder": "Leer lassen für den Standardbetreff",
   "message": "Nachricht",
   "messagePlaceholder": "Persönliche Notiz zur E-Mail hinzufügen…",
   "cc": "CC",
   "bcc": "BCC",
   "ccBccHint": "Mehrere Adressen durch Kommas trennen.",
   "invalidEmailList": "Mindestens eine E-Mail-Adresse ist ungültig.",
   "invalidRecipient": "Gültige E-Mail-Adresse eingeben."
```

Inside `de.json` `invoices.actions` add: `"resend": "Erneut senden"`, `"preview": "Vorschau"`.
At the `de.json` `invoices` level add:

```json
  "previewTitle": "Vorschau {{number}}",
  "previewFailed": "Vorschau konnte nicht geladen werden.",
  "download": "PDF herunterladen",
  "sendTitle": "{{number}} senden",
  "sendSubtitle": "Wir hängen das PDF an und senden die E-Mail an den Empfänger.",
  "sending": "Wird gesendet…",
  "resent": "Rechnungs-E-Mail erneut gesendet.",
  "resendTitle": "{{number}} erneut senden",
  "resendSubtitle": "Die Rechnungs-E-Mail erneut senden — der Status bleibt unverändert.",
  "publicTitle": "Rechnung",
  "publicNotFound": "Dieser Rechnungslink ist ungültig oder abgelaufen."
```

In `de.json` `common` add: `"close": "Schließen"`.

In `fr.json`: same backfill. Inside `invoices.fields` add:

```json
   "recipient": "E-mail du destinataire",
   "recipientHint": "Par défaut, l'e-mail du client si vide.",
   "subject": "Objet",
   "subjectPlaceholder": "Laisser vide pour l'objet par défaut",
   "message": "Message",
   "messagePlaceholder": "Ajouter une note personnelle à l'e-mail…",
   "cc": "CC",
   "bcc": "Cci",
   "ccBccHint": "Séparez plusieurs adresses par des virgules.",
   "invalidEmailList": "Au moins une adresse e-mail n'est pas valide.",
   "invalidRecipient": "Saisissez une adresse e-mail valide."
```

Inside `fr.json` `invoices.actions` add: `"resend": "Renvoyer"`, `"preview": "Aperçu"`.
At the `fr.json` `invoices` level add:

```json
  "previewTitle": "Aperçu {{number}}",
  "previewFailed": "Impossible de charger l'aperçu.",
  "download": "Télécharger le PDF",
  "sendTitle": "Envoyer {{number}}",
  "sendSubtitle": "Nous joignons le PDF et envoyons l'e-mail au destinataire.",
  "sending": "Envoi…",
  "resent": "E-mail de facture renvoyé.",
  "resendTitle": "Renvoyer {{number}}",
  "resendSubtitle": "Renvoyer l'e-mail de la facture — le statut reste inchangé.",
  "publicTitle": "Facture",
  "publicNotFound": "Ce lien de facture est invalide ou a expiré."
```

In `fr.json` `common` add: `"close": "Fermer"`.

Sanity-check parity afterwards:

```bash
cd frontend && python3 -c "
import json
def flat(d, p=''):
    out = set()
    for k, v in d.items():
        key = f'{p}.{k}' if p else k
        out |= flat(v, key) if isinstance(v, dict) else {key}
    return out
en = flat(json.load(open('src/i18n/locales/en.json')))
for loc in ('de','fr'):
    other = flat(json.load(open(f'src/i18n/locales/{loc}.json')))
    print(loc, 'missing:', sorted(en - other))"
```

Expected: `de missing: []` and `fr missing: []`.

- [ ] **Step 2: Write the failing component test**

Create `frontend/src/features/invoices/SendInvoiceDialog.test.tsx`:

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';

import '@/i18n';

vi.mock('@/api/invoices', () => ({
  listInvoices: vi.fn(),
  getInvoice: vi.fn(),
  createInvoice: vi.fn(),
  updateInvoice: vi.fn(),
  deleteInvoice: vi.fn(),
  sendInvoice: vi.fn(),
  resendInvoice: vi.fn(),
  markPaid: vi.fn(),
  cancelInvoice: vi.fn(),
  fetchInvoicePdf: vi.fn(),
  getPublicInvoice: vi.fn(),
  getEmailPreview: vi.fn(),
}));

import { getEmailPreview, resendInvoice, sendInvoice } from '@/api/invoices';
import { SendInvoiceDialog } from './SendInvoiceDialog';

const PREVIEW = {
  recipientEmail: 'billing@widget.example',
  subject: 'Invoice INV-1 from Acme',
  body: 'Hi, please find your invoice attached.',
};

function renderDialog(props: Partial<Parameters<typeof SendInvoiceDialog>[0]> = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  return render(
    <SendInvoiceDialog
      open
      onClose={() => undefined}
      invoiceId="inv-1"
      invoiceNumber="INV-1"
      defaultRecipient={null}
      {...props}
    />,
    { wrapper },
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getEmailPreview).mockResolvedValue(PREVIEW);
  vi.mocked(sendInvoice).mockResolvedValue({} as never);
  vi.mocked(resendInvoice).mockResolvedValue({} as never);
});

describe('SendInvoiceDialog', () => {
  it('prefills recipient, subject, and message from the email preview', async () => {
    renderDialog();
    expect(await screen.findByDisplayValue('Invoice INV-1 from Acme')).toBeInTheDocument();
    expect(screen.getByDisplayValue('billing@widget.example')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Hi, please find your invoice attached.')).toBeInTheDocument();
  });

  it('rejects an invalid CC address and does not submit', async () => {
    const user = userEvent.setup();
    renderDialog();
    await screen.findByDisplayValue('Invoice INV-1 from Acme');

    await user.type(screen.getByLabelText('CC'), 'not-an-email');
    await user.click(screen.getByRole('button', { name: 'Send' }));

    expect(await screen.findByText('One or more email addresses are invalid.')).toBeInTheDocument();
    expect(sendInvoice).not.toHaveBeenCalled();
  });

  it('submits parsed CC/BCC lists with the payload', async () => {
    const user = userEvent.setup();
    renderDialog();
    await screen.findByDisplayValue('Invoice INV-1 from Acme');

    await user.type(screen.getByLabelText('CC'), 'a@x.io, b@x.io');
    await user.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() =>
      expect(sendInvoice).toHaveBeenCalledWith(
        'inv-1',
        expect.objectContaining({
          recipientEmail: 'billing@widget.example',
          cc: ['a@x.io', 'b@x.io'],
        }),
      ),
    );
  });

  it('uses the resend mutation and title in resend mode', async () => {
    const user = userEvent.setup();
    renderDialog({ mode: 'resend' });
    await screen.findByDisplayValue('Invoice INV-1 from Acme');

    expect(screen.getByRole('dialog', { name: 'Resend INV-1' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Resend' }));

    await waitFor(() => expect(resendInvoice).toHaveBeenCalled());
    expect(sendInvoice).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd frontend && pnpm test:run src/features/invoices/SendInvoiceDialog.test.tsx`
Expected: FAIL — component doesn't fetch preview / has no `mode` prop / no CC field.

- [ ] **Step 4: Rewrite the component**

Replace `frontend/src/features/invoices/SendInvoiceDialog.tsx` entirely:

```tsx
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { isAxiosError } from 'axios';
import { toast } from 'sonner';
import { Send } from 'lucide-react';

import { useEmailPreview, useResendInvoice, useSendInvoice } from '@/hooks/useInvoices';
import type { SendInvoicePayload } from '@/api/invoices';
import { findInvalidEmail, parseEmailList } from '@/lib/email';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Modal } from '@/components/Modal';
import type { ProblemDetail } from '@/types/api';

const schema = z
  .object({
    recipient: z.string().min(1).email(),
    cc: z.string().optional(),
    bcc: z.string().optional(),
    subject: z.string().max(255).optional(),
    message: z.string().max(4000).optional(),
  })
  .superRefine((values, ctx) => {
    (['cc', 'bcc'] as const).forEach((field) => {
      if (findInvalidEmail(parseEmailList(values[field] ?? ''))) {
        ctx.addIssue({ code: z.ZodIssueCode.custom, path: [field], message: 'invalid' });
      }
    });
  });
type FormValues = z.infer<typeof schema>;

export function SendInvoiceDialog({
  open,
  onClose,
  invoiceId,
  invoiceNumber,
  defaultRecipient,
  mode = 'send',
  onSent,
}: {
  open: boolean;
  onClose: () => void;
  invoiceId: string;
  invoiceNumber: string;
  defaultRecipient: string | null;
  mode?: 'send' | 'resend';
  onSent?: () => void;
}) {
  const { t } = useTranslation();
  const send = useSendInvoice();
  const resend = useResendInvoice();
  const preview = useEmailPreview(invoiceId, open);
  const isResend = mode === 'resend';

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { recipient: defaultRecipient ?? '', cc: '', bcc: '', subject: '', message: '' },
  });

  // Prefill with the exact email the backend would send, once the preview loads.
  useEffect(() => {
    if (!open) return;
    reset({
      recipient: defaultRecipient ?? preview.data?.recipientEmail ?? '',
      cc: '',
      bcc: '',
      subject: preview.data?.subject ?? '',
      message: preview.data?.body ?? '',
    });
  }, [open, defaultRecipient, preview.data, reset]);

  async function onSubmit(values: FormValues) {
    const cc = parseEmailList(values.cc ?? '');
    const bcc = parseEmailList(values.bcc ?? '');
    const payload: SendInvoicePayload = {
      recipientEmail: values.recipient,
      cc: cc.length ? cc : undefined,
      bcc: bcc.length ? bcc : undefined,
      subject: values.subject || undefined,
      message: values.message || undefined,
    };
    try {
      if (isResend) {
        await resend.mutateAsync({ id: invoiceId, payload });
        toast.success(t('invoices.resent'));
      } else {
        await send.mutateAsync({ id: invoiceId, payload });
        toast.success(t('invoices.sent'));
      }
      onSent?.();
      onClose();
    } catch (err) {
      const detail = isAxiosError<ProblemDetail>(err) ? err.response?.data?.detail : undefined;
      toast.error(detail ?? t('auth.errors.default'));
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={t(isResend ? 'invoices.resendTitle' : 'invoices.sendTitle', { number: invoiceNumber })}
      description={t(isResend ? 'invoices.resendSubtitle' : 'invoices.sendSubtitle')}
      size="lg"
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="send-invoice-form" disabled={isSubmitting}>
            <Send className="mr-2 h-4 w-4" />
            {isSubmitting
              ? t('invoices.sending')
              : t(isResend ? 'invoices.actions.resend' : 'invoices.actions.send')}
          </Button>
        </>
      }
    >
      <form
        id="send-invoice-form"
        className="space-y-4"
        onSubmit={handleSubmit(onSubmit)}
        noValidate
      >
        <div className="space-y-1.5">
          <Label htmlFor="send-recipient">{t('invoices.fields.recipient')} *</Label>
          <Input
            id="send-recipient"
            type="email"
            aria-invalid={!!errors.recipient}
            placeholder="customer@example.com"
            {...register('recipient')}
          />
          {errors.recipient ? (
            <p className="text-xs text-destructive">{t('invoices.fields.invalidRecipient')}</p>
          ) : (
            <p className="text-xs text-muted-foreground">{t('invoices.fields.recipientHint')}</p>
          )}
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1.5">
            <Label htmlFor="send-cc">{t('invoices.fields.cc')}</Label>
            <Input id="send-cc" aria-invalid={!!errors.cc} {...register('cc')} />
            {errors.cc && (
              <p className="text-xs text-destructive">{t('invoices.fields.invalidEmailList')}</p>
            )}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="send-bcc">{t('invoices.fields.bcc')}</Label>
            <Input id="send-bcc" aria-invalid={!!errors.bcc} {...register('bcc')} />
            {errors.bcc && (
              <p className="text-xs text-destructive">{t('invoices.fields.invalidEmailList')}</p>
            )}
          </div>
          <p className="-mt-2 text-xs text-muted-foreground sm:col-span-2">
            {t('invoices.fields.ccBccHint')}
          </p>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="send-subject">{t('invoices.fields.subject')}</Label>
          <Input
            id="send-subject"
            placeholder={t('invoices.fields.subjectPlaceholder')}
            {...register('subject')}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="send-message">{t('invoices.fields.message')}</Label>
          <Textarea
            id="send-message"
            rows={5}
            placeholder={t('invoices.fields.messagePlaceholder')}
            {...register('message')}
          />
        </div>
      </form>
    </Modal>
  );
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd frontend && pnpm test:run && pnpm type-check && pnpm lint`
Expected: all dialog tests + email helper tests PASS; type-check and lint clean. (`InvoiceDetailPage` still passes `onSent` — prop is now optional, still compatible.)

- [ ] **Step 6: Commit**

```bash
git add frontend/src/features/invoices/SendInvoiceDialog.tsx \
        frontend/src/features/invoices/SendInvoiceDialog.test.tsx \
        frontend/src/i18n/locales/
git commit -m "feat(frontend): send dialog with CC/BCC, live email preview prefill, resend mode

Also backfills 16 send/preview/public i18n keys missing from de/fr.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: Resend action on detail page, CI enforcement, docs

**Files:**
- Modify: `frontend/src/features/invoices/InvoiceDetailPage.tsx`
- Modify: `.github/workflows/ci-frontend.yml:56-58` (drop `continue-on-error`)
- Modify: `docs/technical-analysis.md` (mark debt #1 resolved)

**Interfaces:**
- Consumes: `SendInvoiceDialog` `mode` prop from Task 6.
- Produces: user-visible Resend button for SENT/VIEWED/OVERDUE invoices.

- [ ] **Step 1: Wire resend into the detail page**

In `InvoiceDetailPage.tsx`:

Add mode state next to the existing dialog state (line 35):

```tsx
  const [sendOpen, setSendOpen] = useState(false);
  const [sendMode, setSendMode] = useState<'send' | 'resend'>('send');
```

Add a derived flag next to `canMarkPaid` (line 55):

```tsx
  const canResend = ['SENT', 'VIEWED', 'OVERDUE'].includes(invoice.status);
```

Change the draft Send button (line 90) to set the mode:

```tsx
                <Button onClick={() => { setSendMode('send'); setSendOpen(true); }}>
                  <Send className="mr-2 h-4 w-4" />
                  {t('invoices.actions.send')}
                </Button>
```

Add a Resend button after the `canMarkPaid` block (after line 101):

```tsx
            {canResend && (
              <Button
                variant="outline"
                onClick={() => { setSendMode('resend'); setSendOpen(true); }}
              >
                <Send className="mr-2 h-4 w-4" />
                {t('invoices.actions.resend')}
              </Button>
            )}
```

Update the dialog usage (lines 220-227) — pass `mode`, drop the dead `onSent` callback:

```tsx
      <SendInvoiceDialog
        open={sendOpen}
        onClose={() => setSendOpen(false)}
        invoiceId={invoice.id}
        invoiceNumber={invoice.invoiceNumber}
        defaultRecipient={customer.data?.email ?? null}
        mode={sendMode}
      />
```

- [ ] **Step 2: Enforce frontend tests in CI**

In `.github/workflows/ci-frontend.yml`, replace:

```yaml
      - name: Test
        run: pnpm test:run --coverage
        continue-on-error: true   # no tests yet — flip to false once Phase 7 lands
```

with:

```yaml
      - name: Test
        run: pnpm test:run
```

- [ ] **Step 3: Update the analysis doc**

In `docs/technical-analysis.md`, Technical Debt table row 1, replace the "Cost of ignoring" cell content with:

```
✅ Resolved 2026-07-08 — `/send` and `/resend` accept the full `SendInvoiceRequest` (see `docs/superpowers/plans/2026-07-08-email-sending.md`)
```

- [ ] **Step 4: Full verification**

```bash
cd frontend && pnpm test:run && pnpm type-check && pnpm lint && pnpm build
cd ../backend && ./gradlew test
```

Expected: everything green. Then manually exercise the flow (infra + backend + frontend running): create draft → Send with a CC → check Mailpit at http://localhost:8026 shows the CC header and custom subject → Resend from the detail page → second email arrives, status unchanged.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/invoices/InvoiceDetailPage.tsx \
        .github/workflows/ci-frontend.yml docs/technical-analysis.md
git commit -m "feat(frontend): resend action on invoice detail; enforce frontend tests in CI

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Out of scope (deliberately)

- **Reminder history / scheduled reminders** — Phase 1 Feature 4 (Automatic Overdue) owns those; `resend` here is manual-only and stateless.
- **HTML email templates** — emails stay plain-text; templating is a separate feature.
- **Async email dispatch** — email still sends synchronously inside the transaction; moving to after-commit events is queued in Engineering Improvements (it's a prerequisite for dunning retries, not for this feature).
- **Email address book / recent recipients** — YAGNI until multi-recipient workflows exist.

## Self-review notes

- Spec coverage: connect FE↔BE ✔ (Task 2), custom recipient/subject/message ✔ (Task 2), CC ✔ / BCC ✔ (Tasks 1+2+6), validate emails ✔ (Bean Validation Task 2, zod Task 6), Preview Email ✔ (Tasks 3+6 — dialog prefills with the exact resolved content), resend ✔ (Tasks 4+7), tests ✔ (every task), API update ✔ (Swagger annotations on new endpoints), UI improvements ✔ (Task 6).
- Type consistency: `EmailMessage` 8-arg order fixed in Task 1 and used identically in Tasks 2-4; `SendInvoicePayload.cc/bcc: string[]` matches backend `List<String>`; `EmailPreview` field names match `EmailPreviewResponse` (`recipientEmail`, `subject`, `body`).
- Backward compatibility: `/send` body remains optional (`required = false`), so any caller posting no body keeps working; `onSent` prop made optional rather than removed.
