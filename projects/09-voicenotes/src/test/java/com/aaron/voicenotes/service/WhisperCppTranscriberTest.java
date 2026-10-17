package com.aaron.voicenotes.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WhisperCppTranscriberTest {
    @Test
    void normalizesAndTranscribesSegmentsInOrderThenDeletesTemporaryAudio() throws Exception {
        List<Path> transcribedInputs = new ArrayList<>();
        Path[] workingDirectory = new Path[1];
        WhisperCppTranscriber.ProcessRunner fakeProcess = (command, log, timeout) -> {
            if (command.get(0).equals("ffmpeg")) {
                workingDirectory[0] = Path.of(command.get(command.indexOf("-i") + 1)).getParent();
                String pattern = command.getLast();
                Files.write(Path.of(pattern.replace("%03d", "000")), new byte[]{1});
                Files.write(Path.of(pattern.replace("%03d", "001")), new byte[]{1});
                return new WhisperCppTranscriber.ProcessResult(false, 0);
            }
            Path input = Path.of(command.get(command.indexOf("-f") + 1));
            transcribedInputs.add(input);
            Path output = Path.of(command.get(command.indexOf("-of") + 1) + ".txt");
            Files.writeString(output, input.getFileName().toString());
            return new WhisperCppTranscriber.ProcessResult(false, 0);
        };
        var transcriber = new WhisperCppTranscriber("whisper", "model.bin", "ffmpeg", fakeProcess);
        var result = transcriber.transcribe(new MockMultipartFile("audio", "meeting.mp3", "audio/mpeg", new byte[]{1, 2}));

        assertEquals("TRANSCRIBED", result.status());
        assertEquals("segment-000.wav\nsegment-001.wav", result.text());
        assertTrue(result.notice().contains("five-minute segments"));
        assertEquals(List.of("segment-000.wav", "segment-001.wav"),
                transcribedInputs.stream().map(p -> p.getFileName().toString()).toList());
        assertNotNull(workingDirectory[0]);
        assertFalse(Files.exists(workingDirectory[0]), "temporary audio and transcripts must be removed");
    }

    @Test
    void rejectsRecordingsThatExceedTheSixtyMinuteSegmentCap() throws Exception {
        Path[] workingDirectory = new Path[1];
        WhisperCppTranscriber.ProcessRunner fakeProcess = (command, log, timeout) -> {
            if (command.get(0).equals("ffmpeg")) {
                workingDirectory[0] = Path.of(command.get(command.indexOf("-i") + 1)).getParent();
                String pattern = command.getLast();
                for (int i = 0; i < 13; i++) Files.write(Path.of(pattern.replace("%03d", "%03d".formatted(i))), new byte[]{1});
            }
            return new WhisperCppTranscriber.ProcessResult(false, 0);
        };
        var transcriber = new WhisperCppTranscriber("whisper", "model.bin", "ffmpeg", fakeProcess);

        var result = transcriber.transcribe(new MockMultipartFile("audio", "long.wav", "audio/wav", new byte[]{1}));

        assertEquals("SEGMENT_LIMIT_EXCEEDED", result.status());
        assertFalse(Files.exists(workingDirectory[0]));
    }
}
