package com.aaron.papertrail.api;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InvoiceControllerTest {
    @Test void reconcilesInvoiceTotalAndFlagsManualReviewOnMismatch() {
        var controller = new InvoiceController();
        var valid = controller.extractText(new InvoiceController.InvoiceText("Invoice number: INV-123\nDate: 2026-10-17\nWidget | 2 | 10.00 | 20.00\nTotal: 20.00"));
        assertEquals("EXTRACTED_REVIEW_BEFORE_PAYMENT", valid.status());
        assertEquals(1, valid.lineItems().size());
        var mismatch = controller.extractText(new InvoiceController.InvoiceText("Invoice number: INV-123\nDate: 2026-10-17\nWidget | 2 | 10.00 | 20.00\nTotal: 21.00"));
        assertEquals("MANUAL_REVIEW_REQUIRED", mismatch.status());
    }

    @Test void extractsPartyTaxAndGstinFieldsAndRoutesMalformedTaxIdToReview() {
        var controller = new InvoiceController();
        var invoice = controller.extractText(new InvoiceController.InvoiceText("Supplier: Northstar Supplies\nBuyer: Aaron S\nInvoice number: INV-456\nDate: 2026-10-17\nCurrency: INR\nGSTIN: 27AABCU9603R1ZN\nWidget | 2 | 10.00 | 20.00\nTax: 1.60\nTotal: 21.60"));
        assertEquals("Northstar Supplies", invoice.supplier());
        assertEquals("INR", invoice.currency());
        assertEquals(new java.math.BigDecimal("1.60"), invoice.tax());
        assertEquals("FORMAT_AND_CHECKSUM", invoice.fieldConfidence().get("gstin"));
        assertEquals("27AABCU9603R1ZN", invoice.gstin());
        assertEquals("EXTRACTED_REVIEW_BEFORE_PAYMENT", invoice.status());
        var invalid = controller.extractText(new InvoiceController.InvoiceText("Invoice number: INV-457\nDate: 2026-10-17\nGSTIN: invalid\nWidget | 1 | 10.00 | 10.00\nTotal: 10.00"));
        assertEquals("MANUAL_REVIEW_REQUIRED", invalid.status());
        var wrongChecksum = controller.extractText(new InvoiceController.InvoiceText("Invoice number: INV-458\nDate: 2026-10-17\nGSTIN: 27AABCU9603R1ZX\nWidget | 1 | 10.00 | 10.00\nTotal: 10.00"));
        assertNull(wrongChecksum.gstin());
        assertEquals("MISSING_OR_UNVERIFIED", wrongChecksum.fieldConfidence().get("gstin"));
        assertEquals("MANUAL_REVIEW_REQUIRED", wrongChecksum.status());
    }
}
