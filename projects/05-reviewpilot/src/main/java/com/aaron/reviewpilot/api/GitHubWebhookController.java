package com.aaron.reviewpilot.api;

import com.aaron.reviewpilot.queue.ReviewQueue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.io.IOException;

@RestController
@RequestMapping("/api/webhook")
public class GitHubWebhookController {
    private final String secret;
    private final ReviewQueue queue;
    private final ObjectMapper objectMapper;
    public GitHubWebhookController(@Value("${reviewpilot.webhook-secret:}") String secret, ReviewQueue queue, ObjectMapper objectMapper) {
        this.secret = secret; this.queue = queue; this.objectMapper = objectMapper;
    }

    @PostMapping("/github")
    public Receipt receive(@RequestHeader(value="X-Hub-Signature-256", required=false) String signature,
                           @RequestHeader(value="X-GitHub-Event", required=false) String event,
                           @RequestHeader(value="X-GitHub-Delivery", required=false) String deliveryId,
                           @RequestBody String rawBody) {
        if (secret.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook secret is not configured");
        if (rawBody.length() > 1_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook payload exceeds 1 MB");
        if (event == null || !event.equals("pull_request") || !validDeliveryId(deliveryId) || !validSignature(rawBody, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unsupported event or invalid webhook signature");
        }
        PullRequestMetadata metadata = parseMetadata(rawBody);
        try {
            if (!queue.enqueue(deliveryId, metadata.repository(), metadata.number(), metadata.action())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Webhook delivery was already received");
            }
        } catch (ReviewQueue.QueueFullException full) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Review queue is full; complete pending items before accepting more");
        }
        return new Receipt(deliveryId, event, "QUEUED", "Verified metadata queued locally. The raw payload was discarded; no GitHub API call or review comment was made.");
    }

    @GetMapping("/queue")
    public java.util.List<ReviewQueue.ReviewDelivery> pending(@RequestParam(defaultValue = "50") int limit) {
        return queue.pending(limit);
    }

    @PostMapping("/queue/{deliveryId}/complete")
    public QueueCompletion complete(@PathVariable String deliveryId) {
        if (!deliveryId.matches("[A-Za-z0-9-]{16,100}") || !queue.complete(deliveryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending review delivery not found");
        }
        return new QueueCompletion(deliveryId, "COMPLETED");
    }

    private PullRequestMetadata parseMetadata(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String repository = root.path("repository").path("full_name").asText("");
            int number = root.path("pull_request").path("number").asInt(-1);
            String action = root.path("action").asText("");
            if (!repository.matches("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}") || number < 1 ||
                    !java.util.Set.of("opened", "reopened", "synchronize", "ready_for_review").contains(action)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook does not contain supported pull-request metadata");
            }
            return new PullRequestMetadata(repository, number, action);
        } catch (IOException invalidJson) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook payload is not valid JSON");
        }
    }

    private boolean validDeliveryId(String deliveryId) {
        return deliveryId != null && deliveryId.length() <= 100 && deliveryId.matches("[A-Za-z0-9-]{16,100}");
    }

    private boolean validSignature(String body, String header) {
        if (header == null || !header.matches("sha256=[0-9a-fA-F]{64}")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            byte[] supplied = HexFormat.of().parseHex(header.substring(7));
            return MessageDigest.isEqual(expected, supplied);
        } catch (Exception e) { return false; }
    }

    public record Receipt(String id, String event, String status, String notice) { }
    public record QueueCompletion(String id, String status) { }
    private record PullRequestMetadata(String repository, int number, String action) { }
}
