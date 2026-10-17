package com.aaron.voicenotes.api;
import com.aaron.voicenotes.service.AudioTranscriptionProvider;
import com.aaron.voicenotes.service.TranscriptAnalysis;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TranscriptControllerTest {
    @Test void requiresExplicitConsentBeforeTranscription() throws Exception {
        AudioTranscriptionProvider transcriber = mock(AudioTranscriptionProvider.class);
        var audio = new MockMultipartFile("audio", "meeting.wav", "audio/wav", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> new TranscriptController(transcriber, new TranscriptAnalysis()).transcribe(audio, "false"));
        verifyNoInteractions(transcriber);
    }
}
