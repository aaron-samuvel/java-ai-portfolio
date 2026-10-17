package com.aaron.papertrail.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaperTrailPdfIntegrationTest {
    @Autowired MockMvc mockMvc;

    @Test void extractsAndReconcilesTheCheckedInSyntheticInvoicePdf() throws Exception {
        var fixture = new ClassPathResource("fixtures/invoice-extraction-fixture.pdf");
        var upload = new MockMultipartFile("file", "invoice-extraction-fixture.pdf", "application/pdf",
                fixture.getInputStream().readAllBytes());

        mockMvc.perform(multipart("/api/invoices/extract-file").file(upload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("INV-PT-2026-001"))
                .andExpect(jsonPath("$.supplier").value("Northstar Supplies"))
                .andExpect(jsonPath("$.buyer").value("Aaron S"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.gstin").value("27AABCU9603R1ZN"))
                .andExpect(jsonPath("$.lineItems[0].description").value("Widget"))
                .andExpect(jsonPath("$.lineItemsTotal").value(20.00))
                .andExpect(jsonPath("$.tax").value(1.60))
                .andExpect(jsonPath("$.total").value(21.60))
                .andExpect(jsonPath("$.status").value("EXTRACTED_REVIEW_BEFORE_PAYMENT"));
    }
}
