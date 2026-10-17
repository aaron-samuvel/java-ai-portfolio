package com.aaron.papertrail.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {
    private static final Pattern NUMBER = Pattern.compile("(?im)^\\s*(?:invoice\\s*(?:number|no\\.?|#)?)\\s*[:#-]?\\s*([A-Z0-9-]{3,40})\\s*$");
    private static final Pattern DATE = Pattern.compile("(?im)^\\s*date\\s*[:#-]?\\s*(\\d{4}-\\d{2}-\\d{2})\\s*$");
    private static final Pattern TOTAL = Pattern.compile("(?im)^\\s*(?:total|amount due)\\s*[:#-]?\\s*[$€£]?\\s*(\\d{1,9}(?:[.,]\\d{2})?)\\s*$");
    private static final Pattern LINE = Pattern.compile("(?im)^\\s*(.+?)\\s+\\|\\s*(\\d+(?:\\.\\d+)?)\\s+\\|\\s*[$€£]?(\\d{1,9}(?:[.,]\\d{2})?)\\s+\\|\\s*[$€£]?(\\d{1,9}(?:[.,]\\d{2})?)\\s*$");
    private static final Pattern SUPPLIER = Pattern.compile("(?im)^\\s*supplier\\s*[:#-]\\s*(.{1,120})\\s*$");
    private static final Pattern BUYER = Pattern.compile("(?im)^\\s*buyer\\s*[:#-]\\s*(.{1,120})\\s*$");
    private static final Pattern CURRENCY = Pattern.compile("(?im)^\\s*currency\\s*[:#-]\\s*([A-Z]{3})\\s*$");
    private static final Pattern TAX = Pattern.compile("(?im)^\\s*(?:tax|gst amount)\\s*[:#-]?\\s*[$€£]?\\s*(\\d{1,9}(?:[.,]\\d{2})?)\\s*$");
    private static final Pattern GSTIN = Pattern.compile("(?im)^\\s*GSTIN\\s*[:#-]?\\s*([0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z])\\s*$");

    @PostMapping("/extract-text")
    public Extraction extractText(@Valid @RequestBody InvoiceText request) { return extractContent(request.text()); }

    @PostMapping(value="/extract-file", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Extraction extractFile(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 10_000_000) throw new IllegalArgumentException("PDF must be between 1 byte and 10 MB");
        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) throw new IllegalArgumentException("Upload a PDF file");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-') throw new IllegalArgumentException("File content is not a PDF");
            ByteArrayResource resource = new ByteArrayResource(bytes) { @Override public String getFilename() { return "invoice.pdf"; } };
            String text = String.join("\n", new TikaDocumentReader(resource).read().stream().map(document -> document.getText() == null ? "" : document.getText()).toList());
            if (text.length() > 20_000) text = text.substring(0, 20_000);
            return extractContent(text);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("The PDF text could not be extracted locally", e); }
    }

    private Extraction extractContent(String text) {
        String invoice = group(NUMBER, text);
        String dateText = group(DATE, text);
        String totalText = group(TOTAL, text);
        BigDecimal total = parseAmount(totalText);
        String supplier = group(SUPPLIER, text);
        String buyer = group(BUYER, text);
        String currency = group(CURRENCY, text);
        BigDecimal tax = parseAmount(group(TAX, text));
        String extractedGstin = group(GSTIN, text);
        String gstin = GstinValidator.isValid(extractedGstin) ? extractedGstin : null;
        boolean validDate = false;
        try { if (dateText != null) { LocalDate.parse(dateText); validDate = true; } } catch (Exception ignored) { }

        List<LineItem> items = new ArrayList<>();
        Matcher matcher = LINE.matcher(text);
        boolean lineMathValid = true;
        while (matcher.find() && items.size() < 100) {
            BigDecimal quantity = new BigDecimal(matcher.group(2));
            BigDecimal unitPrice = parseAmount(matcher.group(3));
            BigDecimal amount = parseAmount(matcher.group(4));
            if (unitPrice == null || amount == null) { lineMathValid = false; continue; }
            BigDecimal expected = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            if (expected.subtract(amount).abs().compareTo(new BigDecimal("0.01")) > 0) lineMathValid = false;
            items.add(new LineItem(matcher.group(1).trim(), quantity, unitPrice, amount));
        }
        BigDecimal lineItemsTotal = items.stream().map(LineItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedTotal = tax == null ? lineItemsTotal : lineItemsTotal.add(tax);
        boolean totalsReconcile = items.isEmpty() || total != null && expectedTotal.subtract(total).abs().compareTo(new BigDecimal("0.01")) <= 0;
        boolean badGstin = text.toLowerCase(java.util.Locale.ROOT).contains("gstin") && gstin == null;
        boolean reviewRequired = invoice == null || !validDate || total == null || !lineMathValid || !totalsReconcile || badGstin;
        Map<String,String> fieldConfidence = new LinkedHashMap<>();
        fieldConfidence.put("invoiceNumber", confidence(invoice != null));
        fieldConfidence.put("supplier", confidence(supplier != null));
        fieldConfidence.put("buyer", confidence(buyer != null));
        fieldConfidence.put("invoiceDate", confidence(validDate));
        fieldConfidence.put("currency", confidence(currency != null));
        fieldConfidence.put("total", confidence(total != null));
        fieldConfidence.put("tax", confidence(tax != null));
        fieldConfidence.put("gstin", gstin != null ? "FORMAT_AND_CHECKSUM" : "MISSING_OR_UNVERIFIED");
        return new Extraction(invoice, supplier, buyer, validDate ? dateText : null, currency, gstin, total, tax, items, lineItemsTotal,
                Map.copyOf(fieldConfidence),
                reviewRequired ? "LOW" : "MEDIUM", reviewRequired ? "MANUAL_REVIEW_REQUIRED" : "EXTRACTED_REVIEW_BEFORE_PAYMENT",
                "Local text extraction and patterns; verify every field against the original. No file is stored or approved for payment.");
    }

    private String group(Pattern pattern, String text) { Matcher matcher = pattern.matcher(text); return matcher.find() ? matcher.group(1) : null; }
    private String confidence(boolean found) { return found ? "PATTERN_MATCH" : "MISSING_OR_UNVERIFIED"; }
    private BigDecimal parseAmount(String text) {
        if (text == null) return null;
        try { return new BigDecimal(text.replace(',', '.')); } catch (NumberFormatException e) { return null; }
    }

    public record InvoiceText(@NotBlank @Size(max=20_000) String text) { }
    public record LineItem(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount) { }
    public record Extraction(String invoiceNumber, String supplier, String buyer, String invoiceDate, String currency,
                             String gstin, BigDecimal total, BigDecimal tax, List<LineItem> lineItems,
                             BigDecimal lineItemsTotal, Map<String,String> fieldConfidence,
                             String confidence, String status, String notice) { }
}
