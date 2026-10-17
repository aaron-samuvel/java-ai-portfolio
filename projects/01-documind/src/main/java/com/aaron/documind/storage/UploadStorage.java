package com.aaron.documind.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Component
public class UploadStorage {

    private final Path root;

    public UploadStorage(@Value("${documind.storage.directory}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String save(UUID id, String extension, byte[] content) throws IOException {
        Files.createDirectories(root);
        String key = id + extension;
        Path destination = root.resolve(key).normalize();
        if (!destination.getParent().equals(root)) {
            throw new IOException("Invalid generated storage path.");
        }
        Files.write(destination, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return key;
    }

    public Path resolve(String key) {
        Path destination = root.resolve(key).normalize();
        if (!destination.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid storage key.");
        }
        return destination;
    }
}

