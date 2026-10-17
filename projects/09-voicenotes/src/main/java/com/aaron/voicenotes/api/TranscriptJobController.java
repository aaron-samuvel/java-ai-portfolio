package com.aaron.voicenotes.api;

import com.aaron.voicenotes.service.TranscriptJobService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/transcript-jobs")
public class TranscriptJobController {
    private static final List<String> SUPPORTED_TYPES = List.of("audio/wav", "audio/x-wav", "audio/wave", "audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a", "audio/ogg", "audio/webm");
    private final TranscriptJobService jobs;
    public TranscriptJobController(TranscriptJobService jobs) { this.jobs = jobs; }

    @PostMapping(consumes="multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobReceipt submit(@RequestPart("audio") MultipartFile audio, @RequestPart("consent") String consent) throws IOException {
        if (!"true".equalsIgnoreCase(consent)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recording consent is required");
        if (audio.isEmpty() || audio.getSize() > 25_000_000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio must be between 1 byte and 25 MB");
        String type = audio.getContentType() == null ? "" : audio.getContentType().toLowerCase();
        if (!SUPPORTED_TYPES.contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use WAV, MP3, M4A, OGG, or WEBM audio");
        try {
            var job = jobs.submit(audio.getOriginalFilename(), type, audio.getBytes());
            return new JobReceipt(job.id(), job.status(), "/api/transcript-jobs/" + job.id(),
                    "Job metadata and completed transcript are stored locally; audio bytes are held only while queued or processing and are not persisted.");
        } catch (TranscriptJobService.JobCapacityException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public TranscriptJobService.Job get(@PathVariable String id) {
        try { return jobs.get(id); }
        catch (TranscriptJobService.JobNotFoundException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transcript job not found"); }
    }

    public record JobReceipt(String id, String status, String statusUrl, String notice) { }
}
