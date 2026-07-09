package com.invoicebuilder.tenant;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.common.exception.ErrorCode;
import com.invoicebuilder.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * Persists tenant logos under {@code ${app.storage.logo-path}/{tenantId}.{ext}}.
 * Mirrors {@link com.invoicebuilder.pdf.PdfStorage}'s local-filesystem approach.
 */
@Service
public class LogoStorage {

    private static final Logger log = LoggerFactory.getLogger(LogoStorage.class);
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg");

    private final Path baseDir;

    public LogoStorage(AppProperties properties) {
        this.baseDir = properties.storage().logoPath().toAbsolutePath().normalize();
    }

    /** Stores the logo and returns the relative path to persist on the tenant. */
    public String save(Tenant tenant, byte[] bytes, String contentType) {
        String extension = EXTENSION_BY_TYPE.get(contentType == null ? "" : contentType.toLowerCase());
        if (extension == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Logo must be a PNG or JPEG image");
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Logo must be between 1 byte and 2 MB");
        }
        String filename = tenant.getId() + "." + extension;
        try {
            Files.createDirectories(baseDir);
            // Drop a stale logo with the other extension so only one file remains.
            for (String ext : EXTENSION_BY_TYPE.values()) {
                if (!ext.equals(extension)) {
                    Files.deleteIfExists(baseDir.resolve(tenant.getId() + "." + ext));
                }
            }
            Files.write(baseDir.resolve(filename), bytes,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store logo for tenant " + tenant.getId(), e);
        }
        log.debug("Stored {} byte logo for tenant {}", bytes.length, tenant.getId());
        return filename;
    }

    /** Returns the logo bytes, or null when the tenant has none (or the file vanished). */
    public byte[] loadOrNull(Tenant tenant) {
        if (tenant.getLogoPath() == null || tenant.getLogoPath().isBlank()) {
            return null;
        }
        Path file = baseDir.resolve(tenant.getLogoPath()).normalize();
        if (!file.startsWith(baseDir) || !Files.isReadable(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            log.warn("Failed to read logo {} for tenant {}", tenant.getLogoPath(), tenant.getId(), e);
            return null;
        }
    }

    public String contentType(Tenant tenant) {
        String path = tenant.getLogoPath();
        return path != null && path.endsWith(".jpg") ? "image/jpeg" : "image/png";
    }

    public void delete(Tenant tenant) {
        if (tenant.getLogoPath() == null) {
            return;
        }
        try {
            Files.deleteIfExists(baseDir.resolve(tenant.getLogoPath()).normalize());
        } catch (IOException e) {
            log.warn("Failed to delete logo for tenant {}", tenant.getId(), e);
        }
    }
}
