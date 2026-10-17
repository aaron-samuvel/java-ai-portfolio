package com.aaron.helpdesk.support;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:helpdesk-test;DB_CLOSE_DELAY=-1", "helpdesk.memory.redis.enabled=false", "helpdesk.security.customer-1001.password=test-customer-1001", "helpdesk.security.customer-2002.password=test-customer-2002", "helpdesk.security.operator.password=test-support-operator"})
@AutoConfigureMockMvc
@WithMockUser(username = "customer-1001", roles = "CUSTOMER")
class StoreSupportToolsTest {
    @Autowired StoreSupportTools tools;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Test void scopesOrdersToDemoCustomerAndKeepsReturnIdempotent() {
        assertTrue(tools.lookupOrder("NS-1043").contains("not found"));
        assertTrue(tools.lookupOrder("NS-1042").contains("Shipped"));
        assertTrue(tools.checkReturnEligibility("NS-1042").contains("separate explicit confirmation"));
        String first = tools.submitConfirmedReturn("NS-1042");
        String second = tools.submitConfirmedReturn("NS-1042");
        assertEquals(first, second);
        assertEquals(1, jdbc.queryForObject("select count(*) from helpdesk_return_request where order_id='NS-1042'", Integer.class));
    }

    @Test
    @WithMockUser(username = "customer-2002", roles = "CUSTOMER")
    void derivesOrderAndMemoryScopeFromTheAuthenticatedCustomer() {
        assertTrue(tools.lookupOrder("NS-1043").contains("Delivered"));
        assertTrue(tools.lookupOrder("NS-1042").contains("not found"));
        assertTrue(tools.submitConfirmedReturn("NS-1042").contains("not found"));
        assertEquals("customer-2002", new DemoAccount().memoryId());
    }

    @Test
    void basicCredentialsAndSpoofedCustomerFieldCannotCrossAccountBoundary() throws Exception {
        mvc.perform(post("/api/returns")
                        .contentType("application/json")
                        .content("{\"orderId\":\"NS-1042\",\"confirm\":true,\"customerId\":\"customer-1001\"}")
                        .with(httpBasic("customer-2002", "test-customer-2002")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.result", org.hamcrest.Matchers.containsString("not found")));
    }
    @Test void persistsIdempotentTicketsAndAuditEvents() {
        String first = tools.createSupportTicket("Package arrived damaged");
        String second = tools.createSupportTicket("  PACKAGE ARRIVED DAMAGED ");
        assertEquals(first, second);
        assertEquals(1, jdbc.queryForObject("select count(*) from helpdesk_ticket where ticket_id = ?", Integer.class, first.substring("Support ticket ".length(), "Support ticket ".length()+12)));
        assertTrue(jdbc.queryForObject("select count(*) from helpdesk_audit where action='create_ticket'", Integer.class) >= 1);
        assertEquals(1, tools.listOpenTickets(500).size());
    }
    @Test void requiresOperatorRoleForBoundedQueueAndCustomerRoleForActions() throws Exception {
        mvc.perform(get("/api/admin/tickets").param("limit", "500"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/tickets").param("limit", "20")
                        .with(user("support-operator").roles("SUPPORT_OPERATOR")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/returns").contentType("application/json")
                        .content("{\"orderId\":\"NS-1042\",\"confirm\":true}")
                        .with(user("support-operator").roles("SUPPORT_OPERATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/tickets").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()))
                .andExpect(status().isUnauthorized());
    }
}
