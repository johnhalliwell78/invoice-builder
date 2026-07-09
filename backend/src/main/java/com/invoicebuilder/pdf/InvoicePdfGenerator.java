package com.invoicebuilder.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.invoicebuilder.config.AppProperties;
import com.invoicebuilder.customer.Customer;
import com.invoicebuilder.invoice.Invoice;
import com.invoicebuilder.invoice.InvoiceLineItem;
import com.invoicebuilder.tenant.Tenant;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.Property;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.Locale;

/**
 * Renders an {@link Invoice} into a PDF byte array using one of two visual
 * templates ({@code classic} or {@code modern}). All math has already been
 * computed by {@code InvoiceCalculator}; this class only formats and lays out.
 */
@Service
public class InvoicePdfGenerator {

    /** The visual templates this generator can render. Single source of truth. */
    public static final java.util.Set<String> TEMPLATES = java.util.Set.of("classic", "modern");

    private static final DeviceRgb ACCENT = new DeviceRgb(37, 99, 235);   // Tailwind blue-600
    private static final DeviceRgb MUTED = new DeviceRgb(115, 115, 115);
    private static final DeviceRgb LIGHT_BG = new DeviceRgb(245, 245, 247);
    private static final DeviceRgb BORDER_COLOR = new DeviceRgb(220, 220, 220);
    private static final DeviceRgb WHITE = new DeviceRgb(255, 255, 255);

    private final MessageSource messages;
    private final AppProperties properties;

    public InvoicePdfGenerator(MessageSource messages, AppProperties properties) {
        this.messages = messages;
        this.properties = properties;
    }

    public byte[] render(Invoice invoice, Tenant tenant, Customer customer) {
        return render(invoice, tenant, customer, null, null);
    }

    public byte[] render(Invoice invoice, Tenant tenant, Customer customer, String templateOverride) {
        return render(invoice, tenant, customer, templateOverride, null);
    }

    /**
     * Renders with an optional template override ({@code null} falls back to
     * the template stored on the invoice) and optional logo image bytes.
     */
    public byte[] render(Invoice invoice, Tenant tenant, Customer customer,
                         String templateOverride, byte[] logoBytes) {
        String template = templateOverride != null ? templateOverride : invoice.getTemplate();
        Locale locale = Locale.forLanguageTag(
                tenant.getDefaultLocale() == null ? "en" : tenant.getDefaultLocale());
        DeviceRgb accent = parseAccent(tenant.getBrandingColor());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf, PageSize.A4);
        try {
            doc.setMargins(48, 48, 48, 48);
            boolean modern = "modern".equalsIgnoreCase(template);
            if (modern) {
                renderModernHeader(doc, invoice, tenant, locale, accent, logoBytes);
            } else {
                renderClassicHeader(doc, invoice, tenant, locale, logoBytes);
            }
            renderParties(doc, invoice, tenant, customer, locale);
            renderLineItems(doc, invoice, locale, modern, accent);
            renderTotals(doc, invoice, locale, modern, accent);
            renderFooter(doc, invoice, tenant, locale);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    /** Parses a #RRGGBB branding color; anything malformed falls back to the default accent. */
    private static DeviceRgb parseAccent(String hex) {
        if (hex == null || !hex.matches("#[0-9A-Fa-f]{6}")) {
            return ACCENT;
        }
        return new DeviceRgb(
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16));
    }

    private static Image logoImage(byte[] logoBytes) {
        Image img = new Image(ImageDataFactory.create(logoBytes));
        img.setMaxHeight(40);
        img.setMaxWidth(160);
        return img;
    }

    // ------------------------------------------------------------------ headers

    private void renderClassicHeader(Document doc, Invoice invoice, Tenant tenant, Locale locale,
                                     byte[] logoBytes) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
        Cell left = bare();
        if (logoBytes != null && logoBytes.length > 0) {
            left.add(logoImage(logoBytes));
        }
        left.add(p(tenant.getName(), 18, true))
                .add(formatTenantAddress(tenant));
        Cell right = bare()
                .add(p(t("pdf.invoice.title", locale).toUpperCase(), 28, true)
                        .setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph(invoice.getInvoiceNumber()).setFontColor(MUTED)
                        .setTextAlignment(TextAlignment.RIGHT));
        header.addCell(left);
        header.addCell(right);
        doc.add(header);
        doc.add(new Paragraph().setFixedLeading(8));
    }

    private void renderModernHeader(Document doc, Invoice invoice, Tenant tenant, Locale locale,
                                    DeviceRgb accent, byte[] logoBytes) {
        Table band = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
        Cell left = bare().setBackgroundColor(accent).setPadding(16)
                .add(coloredP(t("pdf.invoice.title", locale).toUpperCase(), 24, WHITE, true))
                .add(new Paragraph(invoice.getInvoiceNumber())
                        .setFontColor(new DeviceRgb(220, 230, 255)));
        Cell right = bare().setBackgroundColor(accent).setPadding(16)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        if (logoBytes != null && logoBytes.length > 0) {
            right.add(logoImage(logoBytes).setHorizontalAlignment(HorizontalAlignment.RIGHT));
        }
        right.add(coloredP(tenant.getName(), 16, WHITE, true)
                .setTextAlignment(TextAlignment.RIGHT));
        band.addCell(left);
        band.addCell(right);
        doc.add(band);
        doc.add(new Paragraph().setFixedLeading(16));
    }

    // ------------------------------------------------------------------ parties

    private void renderParties(Document doc, Invoice invoice, Tenant tenant, Customer customer, Locale locale) {
        Table parties = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .useAllAvailableWidth().setMarginTop(16);
        parties.addCell(bare()
                .add(label(t("pdf.invoice.billTo", locale).toUpperCase()))
                .add(p(customer.getName(), 11, true))
                .add(formatCustomerAddress(customer)));

        DateTimeFormatter dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale);
        Table meta = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
        addMetaRow(meta, t("pdf.invoice.issueDate", locale), invoice.getIssueDate().format(dateFmt));
        addMetaRow(meta, t("pdf.invoice.dueDate", locale), invoice.getDueDate().format(dateFmt));
        if (tenant.getTaxId() != null) {
            addMetaRow(meta, t("pdf.invoice.taxId", locale), tenant.getTaxId());
        }
        parties.addCell(bare().add(meta));
        doc.add(parties);
    }

    private void addMetaRow(Table meta, String left, String right) {
        meta.addCell(bareSmall(left));
        meta.addCell(bareSmall(right).setTextAlignment(TextAlignment.RIGHT));
    }

    // ------------------------------------------------------------------ line items

    private void renderLineItems(Document doc, Invoice invoice, Locale locale, boolean modern,
                                 DeviceRgb accent) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{45, 12, 16, 11, 16}))
                .useAllAvailableWidth().setMarginTop(24);

        addHeaderCell(table, t("pdf.invoice.description", locale), TextAlignment.LEFT, modern, accent);
        addHeaderCell(table, t("pdf.invoice.qty", locale), TextAlignment.RIGHT, modern, accent);
        addHeaderCell(table, t("pdf.invoice.unitPrice", locale), TextAlignment.RIGHT, modern, accent);
        addHeaderCell(table, t("pdf.invoice.taxRate", locale), TextAlignment.RIGHT, modern, accent);
        addHeaderCell(table, t("pdf.invoice.amount", locale), TextAlignment.RIGHT, modern, accent);

        NumberFormat money = currencyFormat(invoice.getCurrency(), locale);
        int row = 0;
        for (InvoiceLineItem item : invoice.getLineItems()) {
            Color bg = (modern && row % 2 == 1) ? LIGHT_BG : null;
            addBodyCell(table, item.getDescription(), TextAlignment.LEFT, bg);
            addBodyCell(table, item.getQuantity().stripTrailingZeros().toPlainString(), TextAlignment.RIGHT, bg);
            addBodyCell(table, money.format(item.getUnitPrice()), TextAlignment.RIGHT, bg);
            addBodyCell(table, item.getTaxRate().stripTrailingZeros().toPlainString() + "%", TextAlignment.RIGHT, bg);
            addBodyCell(table, money.format(item.getAmount()), TextAlignment.RIGHT, bg);
            row++;
        }
        doc.add(table);
    }

    // ------------------------------------------------------------------ totals

    private void renderTotals(Document doc, Invoice invoice, Locale locale, boolean modern,
                              DeviceRgb accent) {
        NumberFormat money = currencyFormat(invoice.getCurrency(), locale);
        Table totals = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                .useAllAvailableWidth().setMarginTop(16);
        totals.addCell(bare());
        Table right = new Table(UnitValue.createPercentArray(new float[]{60, 40}));
        right.setHorizontalAlignment(HorizontalAlignment.RIGHT);
        addTotalRow(right, t("pdf.invoice.subtotal", locale), money.format(invoice.getSubtotal()), false, false, accent);
        addTotalRow(right, t("pdf.invoice.tax", locale), money.format(invoice.getTaxTotal()), false, false, accent);
        if (invoice.getDiscountAmount().signum() > 0) {
            addTotalRow(right, t("pdf.invoice.discount", locale),
                    "-" + money.format(invoice.getDiscountAmount()), false, false, accent);
        }
        addTotalRow(right, t("pdf.invoice.total", locale), money.format(invoice.getTotal()),
                true, modern, accent);
        if (invoice.getAmountPaid() != null && invoice.getAmountPaid().signum() > 0) {
            BigDecimal balance = invoice.getTotal().subtract(invoice.getAmountPaid());
            addTotalRow(right, t("pdf.invoice.amountPaid", locale), money.format(invoice.getAmountPaid()), false, false, accent);
            addTotalRow(right, t("pdf.invoice.balanceDue", locale), money.format(balance), true, false, accent);
        }
        totals.addCell(bare().add(right));
        doc.add(totals);
    }

    // ------------------------------------------------------------------ footer

    private void renderFooter(Document doc, Invoice invoice, Tenant tenant, Locale locale) {
        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            doc.add(label(t("pdf.invoice.notes", locale).toUpperCase()).setMarginTop(24));
            doc.add(new Paragraph(invoice.getNotes()).setFontColor(MUTED));
        }
        if (invoice.getTerms() != null && !invoice.getTerms().isBlank()) {
            doc.add(label(t("pdf.invoice.terms", locale).toUpperCase()).setMarginTop(12));
            doc.add(new Paragraph(invoice.getTerms()).setFontColor(MUTED));
        }
        if (tenant.getPaymentInfo() != null && !tenant.getPaymentInfo().isBlank()) {
            doc.add(label(t("pdf.invoice.paymentInfo", locale).toUpperCase()).setMarginTop(12));
            doc.add(new Paragraph(tenant.getPaymentInfo()).setFontSize(10));
        }

        if (invoice.getPublicToken() != null) {
            byte[] qr = renderQrPng(publicViewUrl(invoice.getPublicToken()));
            if (qr != null) {
                Table footer = new Table(UnitValue.createPercentArray(new float[]{20, 80}))
                        .useAllAvailableWidth().setMarginTop(24);
                Image qrImg = new Image(ImageDataFactory.create(qr)).setWidth(72).setHeight(72);
                footer.addCell(bare().add(qrImg));
                footer.addCell(bare()
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .add(p(t("pdf.invoice.viewOnline", locale), 11, true))
                        .add(new Paragraph(publicViewUrl(invoice.getPublicToken()))
                                .setFontColor(MUTED).setFontSize(9)));
                doc.add(footer);
            }
        }
        if (tenant.getFooterText() != null && !tenant.getFooterText().isBlank()) {
            doc.add(new Paragraph(tenant.getFooterText())
                    .setFontColor(MUTED).setFontSize(9).setMarginTop(16)
                    .setTextAlignment(TextAlignment.CENTER));
        }
        Paragraph thanks = new Paragraph(t("pdf.invoice.thankYou", locale))
                .setFontColor(MUTED).setMarginTop(8).setTextAlignment(TextAlignment.CENTER);
        thanks.setProperty(Property.ITALIC_SIMULATION, Boolean.TRUE);
        doc.add(thanks);
    }

    // ------------------------------------------------------------------ helpers

    private String t(String key, Locale locale) {
        return messages.getMessage(key, null, key, locale);
    }

    private static NumberFormat currencyFormat(String currencyCode, Locale locale) {
        NumberFormat fmt = NumberFormat.getCurrencyInstance(locale);
        try {
            fmt.setCurrency(Currency.getInstance(currencyCode));
        } catch (IllegalArgumentException ignored) {
            // unknown currency code — fall back to whatever the locale picks
        }
        return fmt;
    }

    private static Cell bare() {
        return new Cell().setBorder(Border.NO_BORDER);
    }

    private static Cell bareSmall(String text) {
        return bare().add(new Paragraph(text).setFontSize(10));
    }

    /**
     * Build a paragraph with optional bold styling. iText 9 dropped the
     * convenience {@code setBold()} on block elements, so we use the
     * Property.BOLD_SIMULATION flag directly.
     */
    private static Paragraph p(String text, float size, boolean bold) {
        Paragraph p = new Paragraph(text).setFontSize(size);
        if (bold) p.setProperty(Property.BOLD_SIMULATION, Boolean.TRUE);
        return p;
    }

    private static Paragraph coloredP(String text, float size, DeviceRgb color, boolean bold) {
        Paragraph p = p(text, size, bold);
        p.setFontColor(color);
        return p;
    }

    private static Paragraph label(String text) {
        return p(text, 8, true).setFontColor(MUTED);
    }

    private static void addHeaderCell(Table table, String text, TextAlignment align, boolean modern,
                                      DeviceRgb accent) {
        DeviceRgb borderColor = modern ? accent : BORDER_COLOR;
        Cell cell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(borderColor, 1))
                .setPadding(8)
                .add(p(text, 9, true).setTextAlignment(align)
                        .setFontColor(modern ? accent : MUTED));
        table.addHeaderCell(cell);
    }

    private static void addBodyCell(Table table, String text, TextAlignment align, Color bg) {
        Cell cell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(BORDER_COLOR, 0.5f))
                .setPadding(8)
                .add(new Paragraph(text).setFontSize(10).setTextAlignment(align));
        if (bg != null) cell.setBackgroundColor(bg);
        table.addCell(cell);
    }

    private static void addTotalRow(Table table, String label, String value, boolean emphasized,
                                    boolean accentBg, DeviceRgb accent) {
        Paragraph labelP = p(label, emphasized ? 11 : 10, true);
        Paragraph valueP = p(value, emphasized ? 13 : 10, true).setTextAlignment(TextAlignment.RIGHT);
        if (accentBg) {
            labelP.setFontColor(WHITE);
            valueP.setFontColor(WHITE);
        } else if (emphasized) {
            labelP.setFontColor(accent);
        } else {
            labelP.setFontColor(MUTED);
        }
        Cell labelCell = new Cell().setBorder(Border.NO_BORDER).setPadding(6).add(labelP);
        Cell valueCell = new Cell().setBorder(Border.NO_BORDER).setPadding(6).add(valueP);
        if (accentBg) {
            labelCell.setBackgroundColor(accent);
            valueCell.setBackgroundColor(accent);
        }
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private static Paragraph formatTenantAddress(Tenant tenant) {
        Paragraph p = new Paragraph().setFontColor(MUTED).setFontSize(10);
        if (tenant.getAddress() != null) {
            appendIfPresent(p, tenant.getAddress().street());
            String cityLine = joinNonBlank(", ",
                    tenant.getAddress().zip(),
                    tenant.getAddress().city(),
                    tenant.getAddress().state());
            appendIfPresent(p, cityLine);
            appendIfPresent(p, tenant.getAddress().country());
        }
        return p;
    }

    private static Paragraph formatCustomerAddress(Customer customer) {
        Paragraph p = new Paragraph().setFontColor(MUTED).setFontSize(10);
        if (customer.getEmail() != null) appendIfPresent(p, customer.getEmail());
        if (customer.getAddress() != null) {
            appendIfPresent(p, customer.getAddress().street());
            String cityLine = joinNonBlank(", ",
                    customer.getAddress().zip(),
                    customer.getAddress().city(),
                    customer.getAddress().state());
            appendIfPresent(p, cityLine);
            appendIfPresent(p, customer.getAddress().country());
        }
        return p;
    }

    private static void appendIfPresent(Paragraph p, String text) {
        if (text != null && !text.isBlank()) {
            if (!p.getChildren().isEmpty()) p.add(new Text("\n"));
            p.add(new Text(text));
        }
    }

    private static String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String s : parts) {
            if (s == null || s.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(s);
        }
        return sb.toString();
    }

    private static byte[] renderQrPng(String url) {
        try {
            var matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 240, 240);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            return png.toByteArray();
        } catch (WriterException | java.io.IOException e) {
            return null;
        }
    }

    private String publicViewUrl(String token) {
        String base = properties.oauth2() != null && properties.oauth2().successRedirectUri() != null
                ? properties.oauth2().successRedirectUri()
                : "http://localhost:5173";
        int idx = base.indexOf("/auth/");
        if (idx > 0) base = base.substring(0, idx);
        return base + "/i/" + token;
    }
}
