package com.aaron.researchcrew.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/research")
public class ResearchController {
    private static final int MAX_ACTIVE_PLANS = 100;
    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    private final AtomicLong eventSequence = new AtomicLong();
    private final ResearchWorkflowGraph workflow;
    private final ResearchPlanStore planStore;

    public ResearchController() { this(new ResearchWorkflowGraph(), new LocalPlanStore()); }
    public ResearchController(ResearchWorkflowGraph workflow) { this(workflow, new LocalPlanStore()); }
    @Autowired public ResearchController(ResearchWorkflowGraph workflow, ResearchPlanStore planStore) {
        this.workflow = workflow;
        this.planStore = planStore;
        for (ResearchPlanStore.Draft draft : planStore.findPending()) {
            Run run = new Run(draft.id(), draft.topic());
            run.revisions = draft.revisions();
            runs.put(draft.id(), run);
            append(run, "planner", "restored", "Restored a pending plan for human review.");
        }
    }

    @PostMapping("/plans")
    public Plan plan(@Valid @RequestBody PlanRequest request) {
        if (runs.size() >= MAX_ACTIVE_PLANS) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Local plan capacity reached; restart the demo to clear completed runs");
        String id = UUID.randomUUID().toString();
        String topic = request.topic().trim();
        Run run = new Run(id, topic);
        planStore.savePending(new ResearchPlanStore.Draft(id, topic, 0));
        runs.put(id, run);
        append(run, "planner", "completed", "Created a draft plan for human review.");
        return new Plan(id, topic, List.of("Planner: scope the question", "Human: approve the plan", "Researcher: retrieve matching local source cards", "Writer: draft cited findings", "Critic: check every finding has a source"), "PENDING_APPROVAL");
    }

    @PutMapping("/{id}/plan")
    public Plan editPlan(@PathVariable String id, @Valid @RequestBody PlanRequest request) {
        Run run = runs.get(id);
        if (run == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Research plan not found");
        synchronized (run) {
            if (run.report != null || run.running || run.revisions >= 3) throw new ResponseStatusException(HttpStatus.CONFLICT, "Plan is no longer editable or has reached its revision limit");
            run.topic = request.topic().trim();
            run.revisions++;
            planStore.savePending(new ResearchPlanStore.Draft(id, run.topic, run.revisions));
            return new Plan(id, run.topic, List.of("Planner: scope the question", "Human: approve the plan", "Researcher: retrieve matching local source cards", "Writer: draft cited findings", "Critic: check every finding has a source"), "PENDING_APPROVAL");
        }
    }

    @PostMapping("/{id}/approve")
    public Report approve(@PathVariable String id, @Valid @RequestBody Approval approval) {
        if (!approval.approved()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Human approval is required to run research");
        Run run = runs.get(id);
        if (run == null) {
            ResearchWorkflowGraph.Result restored = workflow.restoredResult(id).orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Research plan or saved report not found"));
            return report(id, restored);
        }
        final String topic;
        final int revisions;
        synchronized (run) {
            if (run.report != null) return run.report;
            if (run.running) throw new ResponseStatusException(HttpStatus.CONFLICT, "Research is already running for this plan");
            run.running = true;
            topic = run.topic;
            revisions = run.revisions;
            planStore.markApproved(id);
            append(run, "human-approval", "completed", "The plan was approved; research may run.");
        }
        try {
            ResearchWorkflowGraph.Result result = workflow.execute(id, topic, revisions,
                    event -> append(run, event.stage(), event.status(), event.detail()));
            synchronized (run) {
                run.report = report(id, result);
                run.running = false;
                append(run, "workflow", "completed", "The report is ready.");
                completeEmitters(run);
                return run.report;
            }
        } catch (RuntimeException ex) {
            synchronized (run) {
                run.running = false;
                append(run, "workflow", "failed", "The research workflow failed before producing a report.");
                completeEmittersWithError(run, ex);
            }
            throw ex;
        }
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id) {
        Run run = runs.get(id);
        if (run == null) {
            ResearchWorkflowGraph.Result restored = workflow.restoredResult(id).orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Research plan or saved report not found"));
            SseEmitter restoredStream = new SseEmitter(0L);
            send(restoredStream, new WorkflowEvent(eventSequence.incrementAndGet(), "workflow", "restored",
                    restored.criticPassed() ? "Loaded a completed report from the saved graph checkpoint." : "Loaded a saved graph result."));
            restoredStream.complete();
            return restoredStream;
        }
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> run.emitters.remove(emitter));
        emitter.onTimeout(() -> run.emitters.remove(emitter));
        emitter.onError(error -> run.emitters.remove(emitter));
        synchronized (run) {
            for (WorkflowEvent event : run.events) {
                if (!send(emitter, event)) return emitter;
            }
            if (run.report != null || run.failed) emitter.complete();
            else run.emitters.add(emitter);
        }
        return emitter;
    }

    private void append(Run run, String stage, String status, String detail) {
        synchronized (run) {
            WorkflowEvent event = new WorkflowEvent(eventSequence.incrementAndGet(), stage, status, detail);
            run.events.add(event);
            for (SseEmitter emitter : run.emitters) {
                if (!send(emitter, event)) run.emitters.remove(emitter);
            }
            if (status.equals("failed")) run.failed = true;
        }
    }

    private boolean send(SseEmitter emitter, WorkflowEvent event) {
        try {
            emitter.send(SseEmitter.event().id(Long.toString(event.sequence())).name("research-progress").data(event));
            return true;
        } catch (Exception ignored) {
            emitter.completeWithError(ignored);
            return false;
        }
    }

    private void completeEmitters(Run run) {
        run.emitters.forEach(SseEmitter::complete);
        run.emitters.clear();
    }

    private void completeEmittersWithError(Run run, RuntimeException error) {
        run.emitters.forEach(emitter -> emitter.completeWithError(error));
        run.emitters.clear();
    }

    @GetMapping(value = "/{id}/report.md", produces = "text/markdown;charset=UTF-8")
    public ResponseEntity<String> exportMarkdown(@PathVariable String id) {
        Report report = completedReport(id);
        StringBuilder markdown = new StringBuilder("# Research draft\n\n")
                .append("**Topic:** ").append(escapeInline(report.title().replaceFirst("^Research draft: ", ""))).append("\n\n")
                .append("## Findings\n\n");
        for (Finding finding : report.findings()) {
            markdown.append("- ").append(escapeInline(finding.statement())).append(" ")
                    .append(escapeInline(finding.citation())).append("\n");
        }
        if (report.findings().isEmpty()) markdown.append("No findings matched the approved local research plan.\n");
        markdown.append("\n## Sources\n\n");
        for (Source source : report.sources()) {
            markdown.append("- ").append(escapeInline("[" + source.id() + "] " + source.excerpt()))
                    .append(" (").append(escapeInline(source.uri())).append(")\n");
        }
        markdown.append("\n## Review\n\n").append(escapeInline(report.notice()))
                .append("\n\nCritic citation check: ").append(report.criticPassed() ? "passed" : "failed")
                .append(". This draft must be reviewed against its sources.\n");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"research-report.md\"")
                .body(markdown.toString());
    }

    private Report completedReport(String id) {
        Run run = runs.get(id);
        if (run != null) {
            synchronized (run) {
                if (run.report != null) return run.report;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approve the plan before exporting its report");
        }
        ResearchWorkflowGraph.Result restored = workflow.restoredResult(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved report not found"));
        return report(id, restored);
    }

    private String escapeInline(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '\n' || c == '\r') { escaped.append(' '); continue; }
            if ("\\`*_{}[]()#+.!|>~-".indexOf(c) >= 0) escaped.append('\\');
            escaped.append(c);
        }
        return escaped.toString();
    }

    private Report report(String id, ResearchWorkflowGraph.Result result) {
        return new Report(id, "Research draft: " + result.topic(), result.findings(),
                result.sources().stream().map(s -> new Source(s.id(), s.excerpt(), s.uri())).toList(),
                List.of("planner", "human-approval", "researcher", "writer", "critic"),
                result.criticPassed(), result.revisions(), result.notice());
    }

    private static final class Run {
        final String id;
        String topic;
        int revisions;
        Report report;
        boolean running;
        boolean failed;
        final List<WorkflowEvent> events = new ArrayList<>();
        final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
        Run(String id, String topic) { this.id=id; this.topic=topic; }
    }
    private static final class LocalPlanStore implements ResearchPlanStore {
        private final Map<String, Draft> drafts = new ConcurrentHashMap<>();
        @Override public List<Draft> findPending() { return List.copyOf(drafts.values()); }
        @Override public void savePending(Draft draft) { drafts.put(draft.id(), draft); }
        @Override public void markApproved(String id) { drafts.remove(id); }
    }
    public record WorkflowEvent(long sequence, String stage, String status, String detail) { }
    public record PlanRequest(@NotBlank @Size(max=300) String topic) { }
    public record Approval(boolean approved) { }
    public record Plan(String id, String topic, List<String> steps, String status) { }
    public record Source(String id, String excerpt, String uri) { }
    public record Finding(String statement, String citation) implements Serializable { }
    public record Report(String id, String title, List<Finding> findings, List<Source> sources,
                         List<String> stages, boolean criticPassed, int revisions, String notice) { }
}
