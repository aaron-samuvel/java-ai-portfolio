package com.aaron.documind.api;

import com.aaron.documind.domain.CollectionRepository;
import com.aaron.documind.domain.DocumentStatus;
import com.aaron.documind.domain.StoredDocument;
import com.aaron.documind.domain.StoredDocumentRepository;
import com.aaron.documind.ingestion.IngestionService;
import com.aaron.documind.storage.UploadStorage;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
public class DocumentController {

    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "md", "text/markdown",
            "markdown", "text/markdown",
            "html", "text/html",
            "htm", "text/html",
            "txt", "text/plain");
    private static final Set<String> PLAIN_TEXT_EXTENSIONS = Set.of("md", "markdown", "html", "htm", "txt");

    private final CollectionRepository collections;
    private final StoredDocumentRepository documents;
    private final UploadStorage storage;
    private final IngestionService ingestion;

    public DocumentController(CollectionRepository collections, StoredDocumentRepository documents,
                              UploadStorage storage, IngestionService ingestion) {
        this.collections = collections;
        this.documents = documents;
        this.storage = storage;
        this.ingestion = ingestion;
    }

    @PostMapping(path = "/api/collections/{collectionId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@PathVariable UUID collectionId, @RequestPart("file") @NotNull MultipartFile file)
            throws IOException {
        if (!collections.existsById(collectionId)) {
            throw new IllegalArgumentException("Collection was not found.");
        }
        if (file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Choose a non-empty file no larger than 25 MB.");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        String extension = extensionOf(originalName);
        String expectedType = ALLOWED_TYPES.get(extension);
        if (expectedType == null) {
            throw new IllegalArgumentException("Supported types are PDF, DOCX, Markdown, HTML and plain text.");
        }
        String declaredType = file.getContentType();
        if (!PLAIN_TEXT_EXTENSIONS.contains(extension) && declaredType != null
                && !declaredType.equalsIgnoreCase(expectedType)
                && !declaredType.equalsIgnoreCase("application/octet-stream")) {
            throw new IllegalArgumentException("The file type does not match its extension.");
        }

        byte[] content = file.getBytes();
        String sha256 = sha256(content);
        var duplicate = documents.findByCollectionIdAndSha256(collectionId, sha256);
        if (duplicate.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "This file is already present in the collection.",
                    "existingDocumentId", duplicate.get().getId()));
        }

        UUID id = UUID.randomUUID();
        String storageKey = storage.save(id, "." + extension, content);
        StoredDocument saved = documents.save(new StoredDocument(id, collectionId,
                originalName, expectedType, sha256, storageKey));
        ingestion.ingest(saved.getId());
        return ResponseEntity.accepted().body(new UploadAccepted(saved.getId(), saved.getStatus(), saved.getCreatedAt()));
    }

    @GetMapping("/api/documents/{documentId}")
    public DocumentView get(@PathVariable UUID documentId) {
        StoredDocument document = documents.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document was not found."));
        return new DocumentView(document.getId(), document.getCollectionId(), document.getFileName(),
                document.getStatus(), document.getErrorMessage(), document.getCreatedAt());
    }

    @GetMapping("/api/collections/{collectionId}/documents")
    public List<DocumentView> list(@PathVariable UUID collectionId) {
        if (!collections.existsById(collectionId)) {
            throw new IllegalArgumentException("Collection was not found.");
        }
        return documents.findAllByCollectionIdOrderByCreatedAtDesc(collectionId).stream()
                .map(d -> new DocumentView(d.getId(), d.getCollectionId(), d.getFileName(),
                        d.getStatus(), d.getErrorMessage(), d.getCreatedAt())).toList();
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record UploadAccepted(UUID id, DocumentStatus status, Instant createdAt) { }
    public record DocumentView(UUID id, UUID collectionId, String fileName, DocumentStatus status,
                               String errorMessage, Instant createdAt) { }
}

