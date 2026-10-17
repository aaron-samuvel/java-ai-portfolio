package com.aaron.fraudguard.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class AnalystReviewController {
    private final FraudReviewStore store;
    public AnalystReviewController(FraudReviewStore store) { this.store = store; }

    @GetMapping
    public List<FraudReviewStore.Decision> queue(@RequestParam(defaultValue = "50") int limit) {
        return store.openReviewQueue(limit);
    }

    @PutMapping("/{id}/label")
    public LabelReceipt label(@PathVariable long id, @Valid @RequestBody LabelRequest request) {
        if (!store.label(id, request.label().toUpperCase())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Open review decision was not found.");
        return new LabelReceipt(id, request.label().toUpperCase());
    }

    public record LabelRequest(@NotBlank @Pattern(regexp = "(?i)FRAUD|LEGITIMATE") String label) { }
    public record LabelReceipt(long decisionId, String label) { }
}
