package com.invoicebuilder.reporting;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    private String write(String... cells) {
        StringWriter out = new StringWriter();
        try (CsvWriter csv = new CsvWriter(out)) {
            csv.writeRow(cells);
        }
        return out.toString();
    }

    @Test
    void plainValuesAreNotQuotedUnnecessarily() {
        assertThat(write("INV-1", "42.00")).isEqualTo("INV-1,42.00\r\n");
    }

    @Test
    void separatorsQuotesAndNewlinesAreEscapedPerRfc4180() {
        assertThat(write("Acme, Inc.")).isEqualTo("\"Acme, Inc.\"\r\n");
        assertThat(write("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"\r\n");
        assertThat(write("line1\nline2")).isEqualTo("\"line1\nline2\"\r\n");
    }

    @Test
    void formulaInjectionIsNeutralised() {
        // A customer name like this executes when the file is opened in
        // Excel or Sheets unless the leading character is defused (CWE-1236).
        assertThat(write("=cmd|'/c calc'!A1")).startsWith("\"'=cmd");
        assertThat(write("+1+1")).startsWith("\"'+1+1");
        assertThat(write("-2+3")).startsWith("\"'-2+3");
        assertThat(write("@SUM(A1)")).startsWith("\"'@SUM");
        assertThat(write("\tTAB")).startsWith("\"'\tTAB");
    }

    @Test
    void negativeNumbersStayUsableAsNumbers() {
        // Defusing every leading '-' would corrupt real numeric columns, so
        // plain numbers are left alone.
        assertThat(write("-42.00")).isEqualTo("-42.00\r\n");
        assertThat(write("-0.5")).isEqualTo("-0.5\r\n");
    }

    @Test
    void nullsBecomeEmptyCells() {
        assertThat(write("a", null, "b")).isEqualTo("a,,b\r\n");
    }
}
