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
