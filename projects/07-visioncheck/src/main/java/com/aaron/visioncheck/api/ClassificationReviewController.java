package com.aaron.visioncheck.api;

import com.aaron.visioncheck.service.ReviewQueue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/classification-reviews")
public class ClassificationReviewController {
    private final ReviewQueue queue;
    public ClassificationReviewController(ReviewQueue queue) { this.queue = queue; }

    @GetMapping
    public List<ReviewQueue.ReviewItem> pending() { return queue.pending(); }

    @PutMapping("/{id}")
    public ReviewQueue.ReviewItem correct(@PathVariable String id, @Valid @RequestBody ReviewRequest request) {
        try { return queue.correct(id, request.correctedLabel().trim()); }
        catch (ReviewQueue.ReviewNotFoundException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review item not found"); }
        catch (ReviewQueue.ReviewAlreadyCompletedException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Review item was already completed"); }
    }

    public record ReviewRequest(@NotBlank @Size(max=80) String correctedLabel) { }
}
