package com.aaron.helpdesk.api;

import com.aaron.helpdesk.support.StoreSupportTools;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/returns")
public class ReturnController {
    private final StoreSupportTools tools;
    public ReturnController(StoreSupportTools tools) { this.tools = tools; }

    /** The explicit confirmation is a separate user API action; the model has no write-capable return tool. */
    @PostMapping
    public ReturnResponse submit(@Valid @RequestBody ReturnRequest request) {
        return new ReturnResponse(tools.submitConfirmedReturn(request.orderId()));
    }

    public record ReturnRequest(@NotBlank String orderId, @AssertTrue(message = "Explicit confirmation is required") Boolean confirm) {}
    public record ReturnResponse(String result) {}
}
