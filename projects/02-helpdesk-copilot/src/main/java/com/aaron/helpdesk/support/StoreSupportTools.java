package com.aaron.helpdesk.support;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

@Component
public class StoreSupportTools {
    private static final Logger log = LoggerFactory.getLogger(StoreSupportTools.class);
    private final DemoAccount account;
    private final JdbcTemplate jdbc;

    public StoreSupportTools(DemoAccount account, JdbcTemplate jdbc) { this.account = account; this.jdbc = jdbc; }

    @Tool("Look up the status and delivery date for an order belonging to the authenticated customer")
    public String lookupOrder(String orderId) {
        Order order = findOrder(orderId);
        if (order == null) return "Order not found for this account.";
        audit("lookup_order", orderId);
        return "Order " + order.id() + " is " + order.status() + "; expected delivery " + order.deliveryDate() + ". Return eligible: " + order.returnEligible() + ".";
    }

    @Tool("Find a Northstar Shop FAQ answer about shipping, returns, or warranty")
    public String searchFaq(String topic) {
        String normalized = topic == null ? "" : topic.toLowerCase();
        if (normalized.contains("shipping")) return "Standard shipping usually takes 3–5 business days. Tracking is available after dispatch.";
        if (normalized.contains("returns")) return "Eligible unused items may be returned within 30 days of delivery. A return request must be approved before shipment.";
        if (normalized.contains("warranty")) return "Northstar Shop demo electronics include a one-year limited warranty. Keep your order number for support.";
        return "I couldn't find a matching FAQ. I can create a support ticket for a person to help.";
    }

    @Tool("Check whether an order belonging to the authenticated customer is eligible for a return; this tool never submits a return")
    public String checkReturnEligibility(String orderId) {
        Order order = findOrder(orderId);
        if (order == null) return "Order not found for this account.";
        return order.returnEligible() ? "Order is eligible to request a return. Ask the customer to submit the separate explicit confirmation action." : "This order is not eligible for a return.";
    }

    public synchronized String submitConfirmedReturn(String orderId) {
        Order order = findOrder(orderId);
        if (order == null) return "Order not found for this account.";
        if (!order.returnEligible()) return "This order is not eligible for a return.";
        var existing = jdbc.query("select request_id from helpdesk_return_request where order_id = ? and customer_id = ?", (rs, n) -> rs.getString(1), orderId, account.customerId());
        String id = existing.isEmpty() ? "RET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() : existing.get(0);
        if (existing.isEmpty()) jdbc.update("insert into helpdesk_return_request(order_id, customer_id, request_id) values (?, ?, ?)", orderId, account.customerId(), id);
        audit("request_return", orderId);
        return "Return request " + id + " is recorded for " + orderId + ". A support agent will review it; this is not a prepaid label or refund.";
    }

    @Tool("Create a support ticket for the authenticated customer when the issue needs a human")
    public synchronized String createSupportTicket(String summary) {
        if (summary == null || summary.isBlank() || summary.length() > 400) return "Ticket not created: summary must be 1–400 characters.";
        String normalized = summary.trim().toLowerCase();
        String key = UUID.nameUUIDFromBytes(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var existing = jdbc.query("select ticket_id from helpdesk_ticket where customer_id = ? and idempotency_key = ?", (rs, n) -> rs.getString(1), account.customerId(), key);
        String id = existing.isEmpty() ? "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() : existing.get(0);
        if (existing.isEmpty()) {
            try { jdbc.update("insert into helpdesk_ticket(ticket_id, customer_id, idempotency_key, summary, status) values (?, ?, ?, ?, 'OPEN')", id, account.customerId(), key, summary.trim()); }
            catch (DuplicateKeyException race) { id = jdbc.queryForObject("select ticket_id from helpdesk_ticket where customer_id = ? and idempotency_key = ?", String.class, account.customerId(), key); }
        }
        audit("create_ticket", id);
        return "Support ticket " + id + " was created for the demo account.";
    }

    public java.util.List<TicketSummary> listOpenTickets(int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return jdbc.query("select ticket_id, customer_id, summary, status, created_at from helpdesk_ticket where status = 'OPEN' order by created_at desc limit ?",
                (rs, n) -> new TicketSummary(rs.getString("ticket_id"), rs.getString("customer_id"), rs.getString("summary"), rs.getString("status"), rs.getTimestamp("created_at").toInstant()), bounded);
    }

    private void audit(String action, String subject) {
        jdbc.update("insert into helpdesk_audit(action, customer_id, subject) values (?, ?, ?)", action, account.customerId(), subject);
        log.info("support_tool action={} customer={} subject={}", action, account.customerId(), subject);
    }
    private Order findOrder(String id) {
        var rows = jdbc.query("select order_id, status, delivery_date, return_eligible from helpdesk_order where order_id = ? and customer_id = ?",
                (rs, n) -> new Order(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)), id, account.customerId());
        return rows.isEmpty() ? null : rows.get(0);
    }
    private record Order(String id, String status, String deliveryDate, boolean returnEligible) {}
    public record TicketSummary(String id, String customerId, String summary, String status, java.time.Instant createdAt) {}
}
