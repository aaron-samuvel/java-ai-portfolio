package com.aaron.documind.api;

import com.aaron.documind.domain.CollectionRepository;
import com.aaron.documind.domain.DocumentCollection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/collections")
public class CollectionController {

    private final CollectionRepository collections;

    public CollectionController(CollectionRepository collections) {
        this.collections = collections;
    }

    @GetMapping
    public List<CollectionView> list() {
        return collections.findAll().stream()
                .map(c -> new CollectionView(c.getId(), c.getName(), c.getCreatedAt())).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionView create(@Valid @RequestBody CreateCollectionRequest request) {
        DocumentCollection collection = collections.save(new DocumentCollection(request.name().trim()));
        return new CollectionView(collection.getId(), collection.getName(), collection.getCreatedAt());
    }

    public record CreateCollectionRequest(@NotBlank @Size(max = 120) String name) { }
    public record CollectionView(UUID id, String name, Instant createdAt) { }
}

