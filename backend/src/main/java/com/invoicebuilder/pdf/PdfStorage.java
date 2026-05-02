package com.invoicebuilder.pdf;

import com.invoicebuilder.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Persists generated invoice PDFs to the local filesystem under
 * {@code ${app.storage.pdf-path}/{tenantId}/{invoiceId}.pdf}. Phase 7 may
 * swap this for an S3-backed implementation.
 */
@Service
public class PdfStorage {

    private static final Logger log = LoggerFactory.getLogger(PdfStorage.class);

    private final Path baseDir;

    public PdfStorage(AppProperties properties) {
        this.baseDir = properties.storage().pdfPath().toAbsolutePath().normalize();
    }

    public Path save(UUID tenantId, UUID invoiceId, byte[] pdf) {
        Path tenantDir = baseDir.resolve(tenantId.toString());
        Path target = tenantDir.resolve(invoiceId + ".pdf");
        try {
            Files.createDirectories(tenantDir);
            Files.write(target, pdf, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("Wrote {} bytes to {}", pdf.length, target);
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write invoice PDF " + target, e);
        }
    }

    public byte[] load(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read invoice PDF " + path, e);
        }
    }
}
