package com.invoicebuilder.reporting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.regex.Pattern;

/**
 * Minimal RFC 4180 CSV writer with a spreadsheet-injection guard.
 *
 * <p>Escaping commas and quotes is the easy half. The half that matters for
 * security is that spreadsheet applications treat a cell beginning with
 * {@code = + - @}, tab, or carriage return as a <em>formula</em>. A customer
 * called {@code =cmd|'/c calc'!A1} therefore becomes code execution the
 * moment an accountant opens the export (CWE-1236). Such cells get a leading
 * apostrophe so the spreadsheet reads them as text.</p>
 *
 * <p>Plain numbers are exempt: defusing every leading {@code -} would turn
 * real negative amounts into text and break the numeric columns the export
 * exists to provide.</p>
 */
public final class CsvWriter implements AutoCloseable {

    private static final Pattern NUMERIC = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final String INJECTION_PREFIXES = "=+-@\t\r";

    private final Writer out;

    public CsvWriter(Writer out) {
        this.out = out;
    }

    public void writeRow(String... cells) {
        try {
            for (int i = 0; i < cells.length; i++) {
                if (i > 0) {
                    out.write(',');
                }
                out.write(escape(cells[i]));
            }
            out.write("\r\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String value = raw;
        boolean defused = false;
        if (INJECTION_PREFIXES.indexOf(value.charAt(0)) >= 0 && !NUMERIC.matcher(value).matches()) {
            value = "'" + value;
            defused = true;
        }
        boolean mustQuote = defused
                || value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!mustQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        flush();
    }
}
