package com.aaron.voicenotes.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** Pluggable transcription boundary. Implementations must keep audio local unless cloud use is explicitly opted in. */
public interface AudioTranscriptionProvider {
    TranscriptionResult transcribe(MultipartFile audio) throws IOException, InterruptedException;

    record TranscriptionResult(String status, String text, String notice) { }
}
