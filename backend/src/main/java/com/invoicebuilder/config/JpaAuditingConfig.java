package com.invoicebuilder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

@Configuration
public class JpaAuditingConfig {

    /**
     * JPA auditing's default provider returns {@code LocalDateTime}; entities
     * use {@code OffsetDateTime}. Bridge them with the application clock so
     * tests can override time.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(OffsetDateTime.now(clock));
    }
}
