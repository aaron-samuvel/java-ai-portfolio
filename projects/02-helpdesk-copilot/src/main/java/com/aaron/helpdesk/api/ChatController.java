package com.aaron.helpdesk.api;

import com.aaron.helpdesk.ai.SupportAssistant;
import com.aaron.helpdesk.support.DemoAccount;
import com.aaron.helpdesk.support.SupportGuardrails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final SupportAssistant assistant;
    private final DemoAccount account;
    private final SupportGuardrails guardrails;
    public ChatController(SupportAssistant assistant, DemoAccount account, SupportGuardrails guardrails) {
        this.assistant = assistant; this.account = account; this.guardrails = guardrails;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        guardrails.validateInput(request.message());
        var reply = guardrails.validateOutput(assistant.chat(account.memoryId(), request.message()), request.message());
        return new ChatResponse(reply.text(), reply.outputBlocked(), reply.escalationSuggested());
    }

    public record ChatRequest(@NotBlank @Size(max = 3000) String message) {}
    public record ChatResponse(String reply, boolean outputBlocked, boolean escalationSuggested) {}
}
