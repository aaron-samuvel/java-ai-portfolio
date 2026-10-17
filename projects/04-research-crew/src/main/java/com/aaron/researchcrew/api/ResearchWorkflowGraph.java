package com.aaron.researchcrew.api;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.PostgresSaverV2;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/** A deterministic, inspectable LangGraph4j researcher-writer-critic graph. */
@Component
public class ResearchWorkflowGraph {
    private static final String TOPIC = "topic";
    private static final String SOURCES = "sources";
    private static final String FINDINGS = "findings";
    private static final String CRITIC_PASSED = "criticPassed";
    private static final String NOTICE = "notice";
    private static final String REVISION = "revision";
    private static final String COMPLETED = "completed";
    private static final String RUN_ID = "runId";
    private static final List<Evidence> CORPUS = List.of(
            new Evidence("S1", Set.of("rag", "retrieval", "generation", "grounding"), "RAG retrieves relevant source passages and supplies them to a language model so answers can be grounded in those passages.", "local://learning/rag"),
            new Evidence("S2", Set.of("evaluation", "retrieval", "quality", "metrics"), "Retrieval quality and generated-answer quality should be evaluated as separate stages; a correct answer can still be poorly grounded.", "local://learning/evaluation"),
            new Evidence("S3", Set.of("prompt", "injection", "security", "untrusted"), "Retrieved documents are untrusted input. Instructions inside retrieved content should not override the application policy.", "local://learning/prompt-safety"),
            new Evidence("S4", Set.of("mcp", "tool", "authorization", "least-privilege"), "Tool authorization belongs in the host application. A model-generated tool argument is input and should be validated and authorized.", "local://learning/tool-safety"));
    private final CompiledGraph<WorkflowState> graph;
    private final BaseCheckpointSaver checkpointSaver;
    private final Map<String, Consumer<WorkflowEvent>> eventSinks = new ConcurrentHashMap<>();

    public ResearchWorkflowGraph() { this((BaseCheckpointSaver) null); }

    @Autowired
    public ResearchWorkflowGraph(
            @Value("${research-crew.checkpoints.enabled:false}") boolean checkpointsEnabled,
            @Value("${research-crew.checkpoints.host:127.0.0.1}") String host,
            @Value("${research-crew.checkpoints.port:5432}") int port,
            @Value("${research-crew.checkpoints.database:research_crew}") String database,
            @Value("${research-crew.checkpoints.username:research_crew}") String username,
            @Value("${research-crew.checkpoints.password:}") String password) {
        this(checkpointsEnabled ? postgresSaver(host, port, database, username, password) : null);
    }

    public ResearchWorkflowGraph(BaseCheckpointSaver checkpointSaver) {
        this.checkpointSaver = checkpointSaver;
        try {
            StateGraph<WorkflowState> builder = new StateGraph<>(WorkflowState.SCHEMA, WorkflowState::new)
                    .addNode("researcher", node_async(this::research))
                    .addNode("writer", node_async(this::write))
                    .addNode("critic", node_async(this::critic))
                    .addEdge(START, "researcher")
                    .addEdge("researcher", "writer")
                    .addEdge("writer", "critic")
                    .addEdge("critic", END);
            this.graph = checkpointSaver == null ? builder.compile() : builder.compile(
                    CompileConfig.builder().checkpointSaver(checkpointSaver).releaseThread(false).build());
        } catch (GraphStateException e) {
            throw new IllegalStateException("Could not compile the Research Crew graph", e);
        }
    }

    public Result execute(String runId, String topic, int revisions) {
        return execute(runId, topic, revisions, ignored -> { });
    }

    public Result execute(String runId, String topic, int revisions, Consumer<WorkflowEvent> events) {
        RunnableConfig config = RunnableConfig.builder().threadId(runId).build();
        eventSinks.put(runId, events);
        try {
            WorkflowState finalState = graph.invoke(Map.of(TOPIC, topic, REVISION, revisions, RUN_ID, runId), config).orElseThrow(
                    () -> new IllegalStateException("Research Crew graph returned no final state"));
            return result(finalState);
        } finally {
            eventSinks.remove(runId);
        }
    }

    private void emit(WorkflowState state, String stage, String status, String detail) {
        String runId = state.<String>value(RUN_ID).orElse("");
        Consumer<WorkflowEvent> sink = eventSinks.get(runId);
        if (sink != null) sink.accept(new WorkflowEvent(stage, status, detail));
    }

    public Optional<Result> restoredResult(String runId) {
        if (checkpointSaver == null) return Optional.empty();
        RunnableConfig config = RunnableConfig.builder().threadId(runId).build();
        return graph.stateOf(config)
                .filter(snapshot -> snapshot.state().<Boolean>value(COMPLETED).orElse(false))
                .map(snapshot -> result((WorkflowState) snapshot.state()));
    }

    private Result result(WorkflowState finalState) {
        return new Result(finalState.<List<ResearchController.Finding>>value(FINDINGS).orElse(List.of()),
                finalState.<List<Evidence>>value(SOURCES).orElse(List.of()),
                finalState.<Boolean>value(CRITIC_PASSED).orElse(false),
                finalState.<String>value(NOTICE).orElse(""),
                finalState.<String>value(TOPIC).orElse(""),
                finalState.<Integer>value(REVISION).orElse(0));
    }

    private static BaseCheckpointSaver postgresSaver(String host, int port, String database,
                                                       String username, String password) {
        try {
            PostgresSaverV2.Builder builder = PostgresSaverV2.builder();
            builder.host(host).port(port).database(database).user(username).password(password)
                    .stateSerializer(new ObjectStreamStateSerializer<>(WorkflowState::new))
                    .createTables(true);
            return builder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize PostgreSQL graph checkpoints", e);
        }
    }

    private Map<String, Object> research(WorkflowState state) {
        emit(state, "researcher", "started", "Matching the approved topic against the local source cards.");
        Set<String> terms = normalize(state.<String>value(TOPIC).orElse(""));
        List<Evidence> selected = CORPUS.stream().map(card -> new Ranked(card, overlap(terms, card.tags())))
                .filter(r -> r.score() > 0).sorted(Comparator.comparingInt(Ranked::score).reversed())
                .limit(3).map(Ranked::evidence).toList();
        String notice = selected.isEmpty()
                ? "No matching local source cards. The workflow will not invent findings."
                : "Local learning corpus only; verify sources and review this draft before relying on it.";
        emit(state, "researcher", "completed", "Selected " + selected.size() + " matching source card(s).");
        return Map.of(SOURCES, selected, NOTICE, notice);
    }

    private Map<String, Object> write(WorkflowState state) {
        emit(state, "writer", "started", "Drafting findings from the selected source cards.");
        List<Evidence> selected = state.<List<Evidence>>value(SOURCES).orElse(List.of());
        List<ResearchController.Finding> findings = selected.stream()
                .map(source -> new ResearchController.Finding(source.excerpt(), "[" + source.id() + "]"))
                .toList();
        emit(state, "writer", "completed", "Drafted " + findings.size() + " cited finding(s).");
        return Map.of(FINDINGS, findings);
    }

    private Map<String, Object> critic(WorkflowState state) {
        emit(state, "critic", "started", "Checking that every finding cites a selected source.");
        List<Evidence> selected = state.<List<Evidence>>value(SOURCES).orElse(List.of());
        List<ResearchController.Finding> findings = state.<List<ResearchController.Finding>>value(FINDINGS).orElse(List.of());
        boolean passed = findings.stream().allMatch(f -> selected.stream()
                .anyMatch(s -> f.citation().equals("[" + s.id() + "]")));
        emit(state, "critic", "completed", passed ? "Citation integrity check passed." : "Citation integrity check failed; findings were withheld.");
        return Map.of(CRITIC_PASSED, passed, FINDINGS, passed ? findings : List.of(), COMPLETED, true);
    }

    private Set<String> normalize(String text) {
        Set<String> ignored = Set.of("what", "about", "does", "with", "from", "this", "that", "and", "the", "for", "how", "into");
        Set<String> terms = new HashSet<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^a-z0-9-]+"))
            if (word.length() > 2 && !ignored.contains(word)) terms.add(word);
        return terms;
    }

    private int overlap(Set<String> terms, Set<String> tags) {
        return (int) terms.stream().filter(tags::contains).count();
    }

    static final class WorkflowState extends AgentState {
        private static final Map<String, Channel<?>> SCHEMA = Map.of(
                TOPIC, Channels.<String>base((java.util.function.Supplier<String>) () -> ""),
                SOURCES, Channels.<List<Evidence>>base((java.util.function.Supplier<List<Evidence>>) List::of),
                FINDINGS, Channels.<List<ResearchController.Finding>>base((java.util.function.Supplier<List<ResearchController.Finding>>) List::of),
                CRITIC_PASSED, Channels.<Boolean>base((java.util.function.Supplier<Boolean>) () -> false),
                NOTICE, Channels.<String>base((java.util.function.Supplier<String>) () -> ""),
                REVISION, Channels.<Integer>base((java.util.function.Supplier<Integer>) () -> 0),
                RUN_ID, Channels.<String>base((java.util.function.Supplier<String>) () -> ""),
                COMPLETED, Channels.<Boolean>base((java.util.function.Supplier<Boolean>) () -> false));

        WorkflowState(Map<String, Object> initData) { super(initData); }
    }

    public record Evidence(String id, Set<String> tags, String excerpt, String uri) implements Serializable { }
    public record Result(List<ResearchController.Finding> findings, List<Evidence> sources,
                         boolean criticPassed, String notice, String topic, int revisions) { }
    public record WorkflowEvent(String stage, String status, String detail) { }
    private record Ranked(Evidence evidence, int score) { }
}
