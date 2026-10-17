package com.aaron.voicenotes.api;

import com.aaron.voicenotes.service.TranscriptJobService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/transcripts")
public class TranscriptSearchController {
    private final TranscriptJobService jobs;
    public TranscriptSearchController(TranscriptJobService jobs) { this.jobs = jobs; }

    @GetMapping("/search")
    public List<TranscriptJobService.SearchHit> search(@RequestParam String q,
                                                        @RequestParam(defaultValue = "10") int limit) {
        try { return jobs.search(q, limit); }
        catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage()); }
    }

    @DeleteMapping("/jobs/{id}")
    public void delete(@PathVariable String id) {
        if (!jobs.delete(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transcript job was not found.");
    }
}
