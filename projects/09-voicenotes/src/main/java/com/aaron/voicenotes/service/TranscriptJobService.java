package com.aaron.voicenotes.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Durable job metadata/results; audio bytes exist only in the bounded process-local work queue. */
@Service
public class TranscriptJobService {
    private static final int MAX_RECORDED_JOBS = 100;
    private final TranscriptJobRepository repository;
    private final AudioTranscriptionProvider transcriber;
    private final TranscriptAnalysis analysis;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(4), task -> {
                Thread thread = new Thread(task, "voicenotes-transcription");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public TranscriptJobService(TranscriptJobRepository repository, AudioTranscriptionProvider transcriber, TranscriptAnalysis analysis) {
        this.repository = repository; this.transcriber = transcriber; this.analysis = analysis;
    }

    public synchronized Job submit(String filename, String contentType, byte[] audioBytes) {
        if (repository.count() >= MAX_RECORDED_JOBS) throw new JobCapacityException("Local job history is full; delete old jobs before submitting more");
        String id = UUID.randomUUID().toString();
        Job queued = new Job(id, "QUEUED", Instant.now(), Instant.now(), null, null);
        repository.insert(queued);
        try {
            byte[] payload = audioBytes.clone();
            executor.execute(() -> run(id, filename, contentType, payload));
        } catch (RuntimeException e) {
            repository.delete(id);
            throw new JobCapacityException("Transcription queue is full; try again later");
        }
        return queued;
    }

    public Job get(String id) {
        return repository.find(id).orElseThrow(JobNotFoundException::new);
    }

    public java.util.List<SearchHit> search(String query, int requestedLimit) {
        if (query == null || query.isBlank() || query.trim().length() > 120) throw new IllegalArgumentException("Search query must contain 1–120 characters");
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        int limit = Math.max(1, Math.min(requestedLimit, 20));
        return repository.all().stream().filter(job -> job.status().equals("COMPLETED") && job.result() != null)
                .filter(job -> job.result().text().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || job.result().summary().toLowerCase(java.util.Locale.ROOT).contains(needle))
                .sorted(java.util.Comparator.comparing(Job::updatedAt).reversed()).limit(limit)
                .map(job -> new SearchHit(job.id(), job.result().summary(), excerpt(job.result().text(), needle), job.updatedAt())).toList();
    }

    public boolean delete(String id) { return repository.delete(id); }

    private String excerpt(String text, String needle) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        int at = lower.indexOf(needle);
        if (at < 0) return text.substring(0, Math.min(180, text.length()));
        int start = Math.max(0, at - 60), end = Math.min(text.length(), at + needle.length() + 120);
        return (start > 0 ? "…" : "") + text.substring(start, end) + (end < text.length() ? "…" : "");
    }

    private void run(String id, String filename, String contentType, byte[] bytes) {
        update(id, job -> new Job(id, "PROCESSING", job.createdAt(), Instant.now(), null, null));
        try {
            var result = transcriber.transcribe(new InMemoryAudio(filename, contentType, bytes));
            var extracted = analysis.analyze(result.text());
            var transcript = new TranscriptResult(result.status(), result.text(), extracted.summary(), extracted.decisions(),
                    extracted.actions(), extracted.method(), result.notice());
            update(id, job -> new Job(id, "COMPLETED", job.createdAt(), Instant.now(), transcript, null));
        } catch (Exception e) {
            update(id, job -> new Job(id, "FAILED", job.createdAt(), Instant.now(), null,
                    "Transcription failed. Verify the local binary and model settings."));
        }
    }

    private void update(String id, java.util.function.UnaryOperator<Job> operation) {
        repository.find(id).map(operation).ifPresent(repository::update);
    }

    public static class JobCapacityException extends RuntimeException { public JobCapacityException(String message) { super(message); } }
    public static class JobNotFoundException extends RuntimeException { }
    public record TranscriptResult(String transcriptionStatus, String text, String summary, java.util.List<String> decisions,
                                   java.util.List<String> actions, String analysisMethod, String notice) { }
    public record SearchHit(String id, String summary, String excerpt, Instant updatedAt) { }
    public record Job(String id, String status, Instant createdAt, Instant updatedAt, TranscriptResult result, String error) { }

    private record InMemoryAudio(String originalFilename, String contentType, byte[] bytes) implements MultipartFile {
        @Override public String getName() { return "audio"; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes.clone(); }
        @Override public InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        @Override public void transferTo(File destination) throws IOException { Files.write(destination.toPath(), bytes); }
        @Override public void transferTo(Path destination) throws IOException { Files.write(destination, bytes); }
    }
}
