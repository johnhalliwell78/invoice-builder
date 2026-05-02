package com.invoicebuilder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@ConfigurationPropertiesScan
public class InvoiceBuilderApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvoiceBuilderApplication.class, args);
    }
}
