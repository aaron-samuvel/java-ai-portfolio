package com.aaron.voicenotes.api;

import com.aaron.voicenotes.service.AudioTranscriptionProvider;
import com.aaron.voicenotes.service.TranscriptAnalysis;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transcripts")
public class TranscriptController {
    private final AudioTranscriptionProvider transcriber;
    private final TranscriptAnalysis analysis;
    public TranscriptController(AudioTranscriptionProvider transcriber, TranscriptAnalysis analysis) { this.transcriber = transcriber; this.analysis = analysis; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Transcript transcribe(@RequestPart("audio") MultipartFile audio,
                                 @RequestPart("consent") String consent) throws IOException, InterruptedException {
        if (!"true".equalsIgnoreCase(consent)) throw new IllegalArgumentException("Recording consent is required");
        if (audio.isEmpty() || audio.getSize() > 25_000_000) throw new IllegalArgumentException("Audio must be between 1 byte and 25 MB");
        String type = audio.getContentType() == null ? "" : audio.getContentType().toLowerCase();
        if (!List.of("audio/wav", "audio/x-wav", "audio/wave", "audio/mpeg", "audio/mp3", "audio/mp4", "audio/x-m4a", "audio/ogg", "audio/webm").contains(type)) {
            throw new IllegalArgumentException("Use WAV, MP3, M4A, OGG, or WEBM audio");
        }
        var result = transcriber.transcribe(audio);
        String transcript = result.text();
        var extracted = analysis.analyze(transcript);
        return new Transcript(UUID.randomUUID().toString(), transcript, result.status(), extracted.summary(),
                extracted.decisions(), extracted.actions(), extracted.method(), result.notice());
    }

    public record Transcript(String id, String text, String status, String summary, List<String> decisions,
                             List<String> actions, String analysisMethod, String notice) { }
}
