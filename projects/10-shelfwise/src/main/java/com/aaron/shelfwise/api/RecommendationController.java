package com.aaron.shelfwise.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import com.aaron.shelfwise.service.OllamaEmbeddingClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private static final List<Item> ITEMS = List.of(
            new Item("BK1", "Practical Java", Set.of("java", "spring", "backend")),
            new Item("BK2", "Vector Search Basics", Set.of("embedding", "search", "ai")),
            new Item("BK3", "Reliable APIs", Set.of("java", "api", "testing")),
            new Item("BK4", "Visual Computing", Set.of("image", "vision", "machine-learning")));
    private static final List<Set<String>> SYNTHETIC_PROFILES = List.of(Set.of("BK1", "BK3"), Set.of("BK2", "BK4"), Set.of("BK1", "BK2"));
    private final OllamaEmbeddingClient embeddings;

    public RecommendationController() { this(new OllamaEmbeddingClient("http://localhost:11434", "nomic-embed-text", 1000)); }
    @Autowired public RecommendationController(OllamaEmbeddingClient embeddings) { this.embeddings = embeddings; }

    @PostMapping
    public List<Recommendation> recommend(@Valid @RequestBody Request request) {
        Map<String, Double> profileSignals = profileSignals(request, Instant.now());
        Set<String> liked = new HashSet<>(request.likedItemIds());
        Map<String, Double> profileTagWeights = new HashMap<>();
        for (Item item : ITEMS) for (String tag : item.tags())
            profileTagWeights.merge(tag, profileSignals.getOrDefault(item.id(), 0.0), Double::sum);
        List<String> embedInputs = new ArrayList<>();
        boolean hasProfileSignals = !profileSignals.isEmpty();
        if (!hasProfileSignals) embedInputs.add("books and learning");
        ITEMS.forEach(item -> embedInputs.add(item.title() + ". " + String.join(" ", item.tags())));
        List<List<Double>> rawVectors = embeddings.embed(embedInputs).orElse(List.of());
        int catalogueOffset = hasProfileSignals ? 0 : 1;
        boolean embeddingAvailable = rawVectors.size() == ITEMS.size() + catalogueOffset
                && rawVectors.stream().map(List::size).distinct().count() == 1;
        Map<String, List<Double>> itemVectors = new HashMap<>();
        List<Double> computedProfileVector = null;
        if (embeddingAvailable) {
            for (int i = 0; i < ITEMS.size(); i++) itemVectors.put(ITEMS.get(i).id(), rawVectors.get(i + catalogueOffset));
            computedProfileVector = hasProfileSignals ? weightedProfileVector(profileSignals, itemVectors) : rawVectors.getFirst();
        }
        final List<Double> profileVector = computedProfileVector;
        Map<String, Double> coLikes = collaborativeScores(profileSignals);
        double maxCoLikes = Math.max(1.0, coLikes.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0));
        List<ScoredItem> candidates = ITEMS.stream().filter(item -> !liked.contains(item.id())).map(item -> {
            double content = embeddingAvailable ? cosine(profileVector, itemVectors.get(item.id())) : cosine(profileTagWeights, item.tags());
            double collaborative = coLikes.getOrDefault(item.id(), 0.0) / maxCoLikes;
            return new ScoredItem(item, 0.7 * content + 0.3 * collaborative, content, collaborative);
        }).toList();

        List<ScoredItem> remaining = new ArrayList<>(candidates);
        List<Recommendation> ranked = new ArrayList<>();
        while (!remaining.isEmpty() && ranked.size() < 3) {
            ScoredItem best = remaining.stream().map(candidate -> new Penalized(candidate,
                            candidate.baseScore() - 0.2 * maxSimilarity(candidate.item(), ranked, itemVectors)))
                    .max(Comparator.comparingDouble(Penalized::mmrScore).thenComparing(p -> p.item().item().id(), Comparator.reverseOrder()))
                    .orElseThrow().item();
            double penalty = 0.2 * maxSimilarity(best.item(), ranked, itemVectors);
            double score = Math.max(0, best.baseScore() - penalty);
            ranked.add(new Recommendation(best.item().id(), best.item().title(), round(score), round(best.content()), round(best.collaborative()),
                    round(best.baseScore()), round(penalty), embeddingAvailable ? "OLLAMA_EMBEDDING" : "TAG_COSINE_FALLBACK",
                    "Base relevance " + round(best.baseScore()) + " minus diversity penalty " + round(penalty) + "."));
            remaining.remove(best);
        }
        return List.copyOf(ranked);
    }

    private double maxSimilarity(Item candidate, List<Recommendation> selected, Map<String, List<Double>> itemVectors) {
        if (selected.isEmpty()) return 0;
        Map<String, Item> byId = ITEMS.stream().collect(Collectors.toMap(Item::id, item -> item));
        return selected.stream().map(Recommendation::itemId).map(byId::get).filter(Objects::nonNull)
                .mapToDouble(item -> itemVectors.containsKey(item.id())
                        ? cosine(itemVectors.get(candidate.id()), itemVectors.get(item.id()))
                        : cosine(candidate.tags(), item.tags())).max().orElse(0);
    }

    @GetMapping("/evaluation")
    public Evaluation evaluateSample() {
        Set<String> relevant = Set.of("BK2", "BK3");
        List<String> ranked = recommend(new Request(List.of("BK1"))).stream().map(Recommendation::itemId).limit(3).toList();
        double dcg = 0;
        for (int i=0;i<ranked.size();i++) if (relevant.contains(ranked.get(i))) dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        double idcg = 1.0 + 1.0 / (Math.log(3) / Math.log(2));
        return new Evaluation("NDCG@3", round(dcg / idcg), ranked, "Fixed synthetic holdout; not a measure of real user satisfaction.");
    }

    private Map<String, Double> collaborativeScores(Map<String, Double> signals) {
        Map<String, Double> scores = new HashMap<>();
        for (Set<String> sample : SYNTHETIC_PROFILES) {
            double affinity = sample.stream().mapToDouble(id -> signals.getOrDefault(id, 0.0)).sum();
            if (affinity > 0) for (String item : sample) if (!signals.containsKey(item)) scores.merge(item, affinity, Double::sum);
        }
        return scores;
    }
    private Map<String, Double> profileSignals(Request request, Instant now) {
        Map<String, Double> signals = new HashMap<>();
        Set<String> catalogueIds = ITEMS.stream().map(Item::id).collect(Collectors.toSet());
        for (String id : request.likedItemIds()) if (catalogueIds.contains(id)) signals.merge(id, 1.0, Double::sum);
        if (request.interactions() != null) for (Interaction event : request.interactions()) {
            if (event == null || event.itemId() == null || event.type() == null || event.occurredAt() == null
                    || !catalogueIds.contains(event.itemId()) || event.occurredAt().isAfter(now)) continue;
            double eventWeight = switch (event.type()) { case VIEW -> 0.25; case LIKE -> 1.0; case PURCHASE -> 1.5; };
            double ageInDays = Duration.between(event.occurredAt(), now).toSeconds() / 86_400.0;
            double decay = Math.pow(0.5, ageInDays / 30.0);
            signals.merge(event.itemId(), eventWeight * decay, Double::sum);
        }
        return signals;
    }
    private List<Double> weightedProfileVector(Map<String, Double> signals, Map<String, List<Double>> itemVectors) {
        List<Double> profile = new ArrayList<>(Collections.nCopies(itemVectors.values().iterator().next().size(), 0.0));
        double totalWeight = 0;
        for (var signal : signals.entrySet()) {
            List<Double> vector = itemVectors.get(signal.getKey());
            if (vector == null) continue;
            for (int i = 0; i < profile.size(); i++) profile.set(i, profile.get(i) + vector.get(i) * signal.getValue());
            totalWeight += signal.getValue();
        }
        if (totalWeight > 0) for (int i = 0; i < profile.size(); i++) profile.set(i, profile.get(i) / totalWeight);
        return List.copyOf(profile);
    }
    private double cosine(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        long common = a.stream().filter(b::contains).count();
        return common / Math.sqrt((double)a.size() * b.size());
    }
    private double cosine(Map<String, Double> weights, Set<String> tags) {
        if (weights.isEmpty() || tags.isEmpty()) return 0;
        double dot = tags.stream().mapToDouble(tag -> weights.getOrDefault(tag, 0.0)).sum();
        double profileNorm = Math.sqrt(weights.values().stream().mapToDouble(weight -> weight * weight).sum());
        return profileNorm == 0 ? 0 : dot / (profileNorm * Math.sqrt(tags.size()));
    }
    private double cosine(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || a.size() != b.size()) return 0;
        double dot=0, aa=0, bb=0;
        for (int i=0;i<a.size();i++) { dot += a.get(i)*b.get(i); aa += a.get(i)*a.get(i); bb += b.get(i)*b.get(i); }
        return aa == 0 || bb == 0 ? 0 : Math.max(0, dot / Math.sqrt(aa*bb));
    }
    private double round(double x) { return Math.round(x * 1000.0) / 1000.0; }

    public record Request(@NotNull @Size(max=20) List<@NotBlank String> likedItemIds,
                          @Size(max=500) List<@NotNull @Valid Interaction> interactions) {
        public Request(List<String> likedItemIds) { this(likedItemIds, List.of()); }
        public Request {
            likedItemIds = likedItemIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(likedItemIds));
            interactions = interactions == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(interactions));
        }
    }
    public record Interaction(@NotBlank String itemId, @NotNull InteractionType type,
                              @NotNull @PastOrPresent Instant occurredAt) { }
    public enum InteractionType { VIEW, LIKE, PURCHASE }
    public record Recommendation(String itemId, String title, double score, double contentSimilarity,
                                 double collaborativeSignal, double baseRelevance, double diversityPenalty,
                                 String contentMethod, String explanation) { }
    public record Evaluation(String metric, double value, List<String> ranking, String notice) { }
    private record Item(String id, String title, Set<String> tags) { }
    private record ScoredItem(Item item, double baseScore, double content, double collaborative) { }
    private record Penalized(ScoredItem item, double mmrScore) { }
}
