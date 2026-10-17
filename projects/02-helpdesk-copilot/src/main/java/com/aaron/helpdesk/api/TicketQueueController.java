package com.aaron.helpdesk.api;

import com.aaron.helpdesk.support.StoreSupportTools;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Queue access is limited to SUPPORT_OPERATOR by the HTTP security configuration. */
@RestController
@RequestMapping("/api/admin/tickets")
public class TicketQueueController {
    private final StoreSupportTools tools;
    public TicketQueueController(StoreSupportTools tools) { this.tools = tools; }

    @GetMapping
    public List<StoreSupportTools.TicketSummary> openTickets(@RequestParam(defaultValue = "50") int limit) {
        return tools.listOpenTickets(limit);
    }
}
