package com.invoicebuilder.email;

import com.invoicebuilder.config.AppProperties;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Single email entrypoint that decides at runtime whether to use SendGrid
 * (prod, when {@code app.sendgrid.api-key} is configured) or the local
 * Mailpit SMTP server (dev). The contract is identical for both backends.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public EmailService(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

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

    // ------------------------------------------------------------------ SendGrid

    private void sendViaSendGrid(EmailMessage message) {
        Email from = new Email(properties.sendgrid().fromEmail(), properties.sendgrid().fromName());
        Email to = new Email(message.toEmail(), message.toName());
        Mail mail = new Mail(from, message.subject(), to, new Content("text/plain", message.plainTextBody()));

        Personalization personalization = mail.getPersonalization().get(0);
        message.cc().forEach(addr -> personalization.addCc(new Email(addr)));
        message.bcc().forEach(addr -> personalization.addBcc(new Email(addr)));

        if (message.attachmentBytes() != null && message.attachmentBytes().length > 0) {
            Attachments attachment = new Attachments();
            attachment.setContent(Base64.getEncoder().encodeToString(message.attachmentBytes()));
            attachment.setType("application/pdf");
            attachment.setFilename(message.attachmentName());
            attachment.setDisposition("attachment");
            mail.addAttachments(attachment);
        }

        try {
            SendGrid sg = new SendGrid(properties.sendgrid().apiKey());
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);
            if (response.getStatusCode() >= 300) {
                throw new IllegalStateException("SendGrid responded " + response.getStatusCode()
                        + ": " + response.getBody());
            }
            log.info("SendGrid accepted email to {} (status {})", message.toEmail(), response.getStatusCode());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deliver email via SendGrid", e);
        }
    }

    // ------------------------------------------------------------------ SMTP / Mailpit

    private void sendViaSmtp(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.sendgrid().fromEmail(), properties.sendgrid().fromName());
            helper.setTo(message.toEmail());
            if (!message.cc().isEmpty()) {
                helper.setCc(message.cc().toArray(String[]::new));
            }
            if (!message.bcc().isEmpty()) {
                helper.setBcc(message.bcc().toArray(String[]::new));
            }
            helper.setSubject(message.subject());
            helper.setText(message.plainTextBody(), false);
            if (message.attachmentBytes() != null && message.attachmentBytes().length > 0) {
                helper.addAttachment(message.attachmentName(),
                        new ByteArrayResource(message.attachmentBytes()), "application/pdf");
            }
            mailSender.send(mime);
            log.info("SMTP delivery to {} via {}", message.toEmail(), properties.sendgrid().fromEmail());
        } catch (MessagingException | IOException e) {
            throw new IllegalStateException("Failed to deliver email via SMTP", e);
        }
    }
}
