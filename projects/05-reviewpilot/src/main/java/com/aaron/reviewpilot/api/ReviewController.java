package com.aaron.reviewpilot.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private static final Pattern SECRET = Pattern.compile("(?i)(api[_-]?key|password|secret)\\s*[:=]\\s*['\\\"]?([A-Za-z0-9_\\-]{12,})");
    private static final Pattern HUNK = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*$");

    @PostMapping
    public Review review(@Valid @RequestBody DiffRequest request) {
        List<Finding> findings = new ArrayList<>();
        String[] lines = request.diff().split("\\R", -1);
        String file = "<unknown>";
        int newLine = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("+++ ")) {
                file = line.substring(4).replaceFirst("^[ab]/", "");
                continue;
            }
            Matcher hunk = HUNK.matcher(line);
            if (hunk.matches()) {
                newLine = Integer.parseInt(hunk.group(1));
                continue;
            }
            if (newLine < 0 || line.isEmpty() || line.startsWith("\\")) continue;
            if (line.startsWith("+" ) && !line.startsWith("+++")) {
                String code = line.substring(1);
                if (code.contains("TODO") || code.contains("FIXME")) findings.add(new Finding("P2", "TODO left in changed code", file, newLine, i + 1, "Review whether this unfinished work is intentional."));
                Matcher secret = SECRET.matcher(code);
                if (secret.find()) findings.add(new Finding("P1", "Possible hard-coded secret", file, newLine, i + 1, "Remove the credential and rotate it if it was real."));
                newLine++;
            } else if (line.startsWith(" ")) newLine++;
            // Removed lines don't move the new-file cursor.
        }
        return new Review("LOCAL_STATIC_SCAN", findings,
                "Findings include the changed file and new-file line; heuristics require human review. No GitHub API was called.");
    }

    public record DiffRequest(@NotBlank @Size(max = 50_000) String diff) { }
    public record Finding(String severity, String title, String file, int newLine, int diffLine, String suggestion) { }
    public record Review(String source, List<Finding> findings, String notice) { }
}
