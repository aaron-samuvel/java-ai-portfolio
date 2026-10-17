package com.aaron.helpdesk.support;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

/** Small deterministic input/output checks for the local learning prototype. */
@Component
public class SupportGuardrails {
    private static final Pattern OVERRIDE = Pattern.compile("(?i)(ignore|override|disregard)\\s+(all\\s+)?(previous|above|system|developer)\\s+(instructions|rules|messages)");
    private static final Pattern SECRET = Pattern.compile("(?i)(api[_-]?key|password|secret)\\s*[:=]\\s*['\\\"]?[A-Za-z0-9_\\-]{12,}");
    private static final Pattern TOOL_ARTIFACT = Pattern.compile("(?is)^\\s*\\{\\s*\\\"(?:name|tool|arguments|parameters)\\\"\\s*:");
    private static final Pattern UNSUPPORTED_ACTION = Pattern.compile("(?i)\\b(refund (was|has been) issued|prepaid label (was|has been) created|return (was|has been) approved)\\b");
    private static final Pattern ESCALATION = Pattern.compile("(?i)\\b(human|agent|manager|supervisor|complaint|frustrated|angry|unacceptable|still not fixed)\\b");

    public void validateInput(String message) {
        if (OVERRIDE.matcher(message).find()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This demo blocks requests that try to override support instructions.");
        }
    }

    public GuardedReply validateOutput(String reply, String input) {
        if (reply == null || reply.isBlank()) return new GuardedReply("I could not produce a safe answer. I can create a support ticket for a person to help.", true, true);
        if (SECRET.matcher(reply).find()) return new GuardedReply("I removed sensitive-looking text from the draft. A support agent can review your request.", true, true);
        if (TOOL_ARTIFACT.matcher(reply).find()) return new GuardedReply("I could not safely complete that request. Please try again, or I can create a support ticket for a person to help.", true, true);
        if (UNSUPPORTED_ACTION.matcher(reply).find()) return new GuardedReply("I cannot confirm that a refund, label, or return approval was issued. A support agent can review the request.", true, true);
        return new GuardedReply(reply, false, ESCALATION.matcher(input).find());
    }

    public record GuardedReply(String text, boolean outputBlocked, boolean escalationSuggested) { }
}
