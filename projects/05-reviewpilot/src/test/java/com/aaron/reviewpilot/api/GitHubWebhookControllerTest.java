package com.aaron.reviewpilot.api;

import org.junit.jupiter.api.Test;
import com.aaron.reviewpilot.queue.ReviewQueue;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class GitHubWebhookControllerTest {
    private static final String SECRET = "test-only-webhook-secret";
    private static final String BODY = "{\"action\":\"opened\",\"repository\":{\"full_name\":\"aaron/demo\"},\"pull_request\":{\"number\":7}}";

    @Test void acceptsValidDeliveryOnceAndRejectsReplay() throws Exception {
        ReviewQueue queue = mock(ReviewQueue.class);
        when(queue.enqueue(anyString(), eq("aaron/demo"), eq(7), eq("opened"))).thenReturn(true, false);
        var controller = new GitHubWebhookController(SECRET, queue, new ObjectMapper());
        String signature = sign(BODY);
        var first = controller.receive(signature, "pull_request", "a1b2c3d4-0000-4000-8000-000000000001", BODY);
        assertEquals("QUEUED", first.status());
        assertThrows(ResponseStatusException.class, () -> controller.receive(signature, "pull_request",
                "a1b2c3d4-0000-4000-8000-000000000001", BODY));
        verify(queue, times(2)).enqueue(anyString(), eq("aaron/demo"), eq(7), eq("opened"));
    }

    @Test void rejectsInvalidSignaturesAndDeliveryIds() throws Exception {
        var controller = new GitHubWebhookController(SECRET, mock(ReviewQueue.class), new ObjectMapper());
        assertThrows(ResponseStatusException.class, () -> controller.receive("sha256=" + "0".repeat(64),
                "pull_request", "a1b2c3d4-0000-4000-8000-000000000002", BODY));
        assertThrows(ResponseStatusException.class, () -> controller.receive(sign(BODY),
                "pull_request", "../../payload", BODY));
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
