package com.aaron.voicenotes.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class WhisperCppTranscriber implements AudioTranscriptionProvider {
    private static final Duration FFMPEG_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration WHISPER_TIMEOUT = Duration.ofMinutes(2);
    private static final int MAX_SEGMENTS = 12;
    private static final int MAX_TRANSCRIPT_CHARS = 100_000;
    private final String binary;
    private final String model;
    private final String ffmpegBinary;
    private final ProcessRunner processRunner;

    public WhisperCppTranscriber(@Value("${voicenotes.whisper-binary:}") String binary,
                                 @Value("${voicenotes.whisper-model:}") String model,
                                 @Value("${voicenotes.ffmpeg-binary:}") String ffmpegBinary) {
        this(binary, model, ffmpegBinary, WhisperCppTranscriber::runProcess);
    }

    WhisperCppTranscriber(String binary, String model, String ffmpegBinary, ProcessRunner processRunner) {
        this.binary = binary;
        this.model = model;
        this.ffmpegBinary = ffmpegBinary;
        this.processRunner = processRunner;
    }

    @Override
    public AudioTranscriptionProvider.TranscriptionResult transcribe(MultipartFile audio) throws IOException, InterruptedException {
        if (binary.isBlank() || model.isBlank()) {
            return new AudioTranscriptionProvider.TranscriptionResult("BACKEND_NOT_CONFIGURED", "", "Set WHISPER_CPP_BINARY and WHISPER_CPP_MODEL to use local whisper.cpp.");
        }

        Path work = Files.createTempDirectory("voicenotes-");
        try {
            Path input = work.resolve("recording" + extension(audio.getContentType()));
            audio.transferTo(input);
            if (ffmpegBinary == null || ffmpegBinary.isBlank()) return transcribeFile(input, work, "");

            Path segmentPattern = work.resolve("segment-%03d.wav");
            Path ffmpegLog = work.resolve("ffmpeg.log");
            List<String> ffmpeg = List.of(ffmpegBinary, "-hide_banner", "-loglevel", "error", "-i", input.toString(),
                    "-vn", "-t", "3601", "-ac", "1", "-ar", "16000", "-f", "segment", "-segment_time", "300",
                    "-reset_timestamps", "1", "-c:a", "pcm_s16le", segmentPattern.toString());
            ProcessResult conversion = processRunner.run(ffmpeg, ffmpegLog, FFMPEG_TIMEOUT);
            if (conversion.timedOut()) return new AudioTranscriptionProvider.TranscriptionResult("NORMALIZATION_TIMEOUT", "", "Audio normalization exceeded the three-minute limit.");
            if (conversion.exitCode() != 0) return new AudioTranscriptionProvider.TranscriptionResult("NORMALIZATION_FAILED", "", "ffmpeg could not normalize the uploaded audio; inspect its local log and input format.");

            List<Path> segments;
            try (Stream<Path> paths = Files.list(work)) {
                segments = paths.filter(path -> path.getFileName().toString().matches("segment-\\d{3}\\.wav"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
            }
            if (segments.isEmpty()) return new AudioTranscriptionProvider.TranscriptionResult("NO_AUDIO_SEGMENTS", "", "ffmpeg produced no audio segments.");
            if (segments.size() > MAX_SEGMENTS) return new AudioTranscriptionProvider.TranscriptionResult("SEGMENT_LIMIT_EXCEEDED", "",
                    "Recording is longer than the local 60-minute transcription limit; split it into shorter recordings.");

            StringBuilder transcript = new StringBuilder();
            for (int index = 0; index < segments.size(); index++) {
                AudioTranscriptionProvider.TranscriptionResult part = transcribeFile(segments.get(index), work, "part-" + index);
                if (!"TRANSCRIBED".equals(part.status())) return part;
                if (!part.text().isBlank()) {
                    if (!transcript.isEmpty()) transcript.append('\n');
                    transcript.append(part.text());
                }
                if (transcript.length() > MAX_TRANSCRIPT_CHARS) {
                    transcript.setLength(MAX_TRANSCRIPT_CHARS);
                    break;
                }
            }
            return new AudioTranscriptionProvider.TranscriptionResult("TRANSCRIBED", transcript.toString(),
                    "Normalized and transcribed locally in ordered five-minute segments. Verify names, numbers, and decisions against the recording.");
        } finally {
            try (var paths = Files.walk(work)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                });
            }
        }
    }

    private AudioTranscriptionProvider.TranscriptionResult transcribeFile(Path input, Path work, String suffix) throws IOException, InterruptedException {
        Path outputBase = work.resolve(suffix.isEmpty() ? "transcript" : "transcript-" + suffix);
        Path log = work.resolve(suffix.isEmpty() ? "whisper.log" : "whisper-" + suffix + ".log");
        List<String> command = List.of(binary, "-m", model, "-f", input.toString(), "-otxt", "-of", outputBase.toString(), "-np");
        ProcessResult process = processRunner.run(command, log, WHISPER_TIMEOUT);
        if (process.timedOut()) return new AudioTranscriptionProvider.TranscriptionResult("TIMEOUT", "", "Local transcription exceeded the two-minute per-segment limit.");
        if (process.exitCode() != 0) return new AudioTranscriptionProvider.TranscriptionResult("TRANSCRIPTION_FAILED", "", "whisper.cpp exited with an error; inspect its local log and model configuration.");
        Path transcript = Path.of(outputBase + ".txt");
        if (!Files.isRegularFile(transcript)) return new AudioTranscriptionProvider.TranscriptionResult("NO_TRANSCRIPT", "", "The local backend did not produce a transcript file.");
        String text = Files.readString(transcript);
        if (text.length() > MAX_TRANSCRIPT_CHARS) text = text.substring(0, MAX_TRANSCRIPT_CHARS);
        return new AudioTranscriptionProvider.TranscriptionResult("TRANSCRIBED", text, "Transcribed locally with whisper.cpp. Verify names, numbers, and decisions against the recording.");
    }

    private static ProcessResult runProcess(List<String> command, Path log, Duration timeout) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor();
            return new ProcessResult(true, -1);
        }
        return new ProcessResult(false, process.exitValue());
    }

    private String extension(String contentType) {
        if (contentType == null) return ".audio";
        return switch (contentType.toLowerCase()) {
            case "audio/wav", "audio/x-wav", "audio/wave" -> ".wav";
            case "audio/mpeg", "audio/mp3" -> ".mp3";
            case "audio/mp4", "audio/x-m4a" -> ".m4a";
            case "audio/ogg" -> ".ogg";
            case "audio/webm" -> ".webm";
            default -> ".audio";
        };
    }

    record ProcessResult(boolean timedOut, int exitCode) { }
    @FunctionalInterface interface ProcessRunner {
        ProcessResult run(List<String> command, Path log, Duration timeout) throws IOException, InterruptedException;
    }
}
