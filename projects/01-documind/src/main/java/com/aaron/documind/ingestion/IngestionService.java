package com.aaron.documind.ingestion;

import com.aaron.documind.domain.StoredDocument;
import com.aaron.documind.domain.StoredDocumentRepository;
import com.aaron.documind.storage.UploadStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int BATCH_SIZE = 50;

    private final StoredDocumentRepository documents;
    private final UploadStorage storage;
    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;
    private final int overlapWords;

    public IngestionService(StoredDocumentRepository documents, UploadStorage storage,
                            VectorStore vectorStore,
                            @Value("${documind.ingestion.chunk-size:700}") int chunkSize,
                            @Value("${documind.ingestion.min-chunk-size-chars:350}") int minChunkSizeChars,
                            @Value("${documind.ingestion.max-chunks:2000}") int maxChunks,
                            @Value("${documind.ingestion.overlap-words:40}") int overlapWords) {
        if (chunkSize < 100 || minChunkSizeChars < 1 || maxChunks < 1 || overlapWords < 0 || overlapWords > 200) {
            throw new IllegalArgumentException("Invalid DocuMind ingestion chunk settings.");
        }
        this.documents = documents;
        this.storage = storage;
        this.vectorStore = vectorStore;
        this.overlapWords = overlapWords;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(minChunkSizeChars)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(maxChunks)
                .withKeepSeparator(true)
                .build();
    }

    @Async("ingestionExecutor")
    public void ingest(UUID documentId) {
        try {
            process(documentId);
        } catch (Exception exception) {
            log.warn("Document ingestion failed for {}: {}", documentId, exception.getMessage());
            markFailed(documentId, "The file could not be extracted or indexed.");
        }
    }

    public void process(UUID documentId) {
        StoredDocument stored = documents.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document was not found."));
        List<Document> extracted = new TikaDocumentReader(
                new FileSystemResource(storage.resolve(stored.getStorageKey()))).read();
        if (extracted.isEmpty() || extracted.stream().allMatch(d -> d.getText() == null || d.getText().isBlank())) {
            throw new IllegalArgumentException("No text could be extracted from this file.");
        }

        List<Document> chunks = new ArrayList<>();
        int index = 0;
        for (Document source : ChunkOverlap.apply(splitter.apply(extracted), overlapWords)) {
            Map<String, Object> metadata = new HashMap<>();
            Object page = source.getMetadata().get("page");
            Object section = source.getMetadata().get("section");
            if (page != null) metadata.put("page", page);
            if (section != null) metadata.put("section", section);
            metadata.put("documentId", stored.getId().toString());
            metadata.put("collectionId", stored.getCollectionId().toString());
            metadata.put("fileName", stored.getFileName());
            metadata.put("chunkIndex", index++);
            chunks.add(new Document(source.getText(), metadata));
        }
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("No searchable text chunks were produced.");
        }

        for (int start = 0; start < chunks.size(); start += BATCH_SIZE) {
            vectorStore.add(chunks.subList(start, Math.min(start + BATCH_SIZE, chunks.size())));
        }
        stored.markIndexed();
        documents.save(stored);
        log.info("Indexed document {} with {} chunks", documentId, chunks.size());
    }

    protected void markFailed(UUID documentId, String message) {
        documents.findById(documentId).ifPresent(document -> {
            document.markFailed(message);
            documents.save(document);
        });
    }
}

