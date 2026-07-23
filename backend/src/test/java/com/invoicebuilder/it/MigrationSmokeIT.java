package com.invoicebuilder.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Booting the shared context already ran every Liquibase changelog against
 * real Postgres; this pins the outcome so a broken migration fails loudly.
 */
class MigrationSmokeIT extends IntegrationTestBase {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void allPhase2TablesExist() {
        for (String table : new String[] {
                "tenant", "app_user", "customer", "invoice", "invoice_line_item",
                "product", "recurring_invoice", "payment", "audit_log", "notification"}) {
            Integer count = jdbc.queryForObject(
                    "select count(*) from information_schema.tables where table_name = ?",
                    Integer.class, table);
            assertThat(count).as("table %s", table).isEqualTo(1);
        }
    }

    @Test
    void invoiceTableCarriesEstimateColumns() {
        Integer count = jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'invoice' and column_name in ('doc_type', 'converted_invoice_id')
                """, Integer.class);
        assertThat(count).isEqualTo(2);
    }
}
