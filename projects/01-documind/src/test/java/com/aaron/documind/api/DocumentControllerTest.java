package com.aaron.documind.api;
import com.aaron.documind.domain.*;
import com.aaron.documind.ingestion.IngestionService;
import com.aaron.documind.storage.UploadStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DocumentControllerTest {
    @Test void rejectsUnsupportedUploadsBeforeStorageOrIndexing() {
        CollectionRepository collections = mock(CollectionRepository.class);
        when(collections.existsById(any())).thenReturn(true);
        StoredDocumentRepository documents = mock(StoredDocumentRepository.class);
        UploadStorage storage = mock(UploadStorage.class);
        IngestionService ingestion = mock(IngestionService.class);
        var controller = new DocumentController(collections, documents, storage, ingestion);
        var file = new MockMultipartFile("file", "payload.exe", "application/octet-stream", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> controller.upload(UUID.randomUUID(), file));
        verifyNoInteractions(storage, ingestion);
    }
}
