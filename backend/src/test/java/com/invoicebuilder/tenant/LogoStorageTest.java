package com.invoicebuilder.tenant;

import com.invoicebuilder.common.exception.AppException;
import com.invoicebuilder.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogoStorageTest {

    @TempDir
    Path tempDir;

    private LogoStorage storage;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt("0123456789abcdef0123456789abcdef", Duration.ofMinutes(15), Duration.ofDays(7), "test"),
                null,
                new AppProperties.Sendgrid("", "noreply@test.local", "Test"),
                new AppProperties.Storage(tempDir.resolve("pdfs"), tempDir.resolve("logos")),
                new AppProperties.Cors(List.of()),
                new AppProperties.RateLimit(5, Duration.ofMinutes(15), 100));
        storage = new LogoStorage(properties);
        tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
    }

    @Test
    void savesAndLoadsPngLogo() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

        String path = storage.save(tenant, png, "image/png");

        assertThat(path).endsWith(".png");
        tenant.setLogoPath(path);
        assertThat(storage.loadOrNull(tenant)).isEqualTo(png);
    }

    @Test
    void rejectsUnsupportedContentType() {
        assertThatThrownBy(() -> storage.save(tenant, new byte[]{1}, "image/gif"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void rejectsOversizedLogo() {
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> storage.save(tenant, big, "image/png"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void deleteRemovesFileAndLoadReturnsNull() {
        String path = storage.save(tenant, new byte[]{1, 2}, "image/jpeg");
        tenant.setLogoPath(path);

        storage.delete(tenant);

        assertThat(storage.loadOrNull(tenant)).isNull();
    }

    @Test
    void loadReturnsNullWhenNoLogoPath() {
        assertThat(storage.loadOrNull(tenant)).isNull();
    }
}
