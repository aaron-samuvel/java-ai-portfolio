package com.aaron.voicenotes.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TranscriptJobServiceTest {
    @Test void movesQueuedAudioToCompletedJobWithExtractiveAnalysis() throws Exception {
        var transcriber = mock(AudioTranscriptionProvider.class);
        when(transcriber.transcribe(any())).thenReturn(new AudioTranscriptionProvider.TranscriptionResult(
                "TRANSCRIBED", "We agreed on the plan. Alex will send the draft.", "local test"));
        var source = new DriverManagerDataSource("jdbc:h2:mem:voice_test;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        var repository = new TranscriptJobRepository(jdbc, new ObjectMapper());
        var service = new TranscriptJobService(repository, transcriber, new TranscriptAnalysis());
        var created = service.submit("meeting.wav", "audio/wav", new byte[]{1,2,3});
        assertEquals("QUEUED", created.status());
        Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
        TranscriptJobService.Job current;
        do {
            current = service.get(created.id());
            if (!current.status().equals("COMPLETED") && !current.status().equals("FAILED")) Thread.sleep(10);
        } while (!current.status().equals("COMPLETED") && !current.status().equals("FAILED") && Instant.now().isBefore(deadline));
        assertEquals("COMPLETED", current.status());
        assertEquals(1, current.result().decisions().size());
        assertEquals(1, current.result().actions().size());
        assertEquals(1, service.search("draft", 50).size());
        assertTrue(service.search("draft", 50).getFirst().excerpt().contains("draft"));
        var restarted = new TranscriptJobService(new TranscriptJobRepository(jdbc, new ObjectMapper()), transcriber, new TranscriptAnalysis());
        assertEquals(current.result(), restarted.get(created.id()).result());
        assertTrue(service.delete(created.id()));
        assertThrows(TranscriptJobService.JobNotFoundException.class, () -> restarted.get(created.id()));
        verify(transcriber).transcribe(any());
    }
}
