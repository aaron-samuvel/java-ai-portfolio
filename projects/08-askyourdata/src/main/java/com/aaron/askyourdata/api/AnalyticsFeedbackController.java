package com.aaron.askyourdata.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics/feedback")
public class AnalyticsFeedbackController {
    private final JdbcTemplate jdbc;
    public AnalyticsFeedbackController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping
    public FeedbackReceipt submit(@Valid @RequestBody Feedback feedback) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("insert into analytics_feedback(report_id, rating, comment) values (?, ?, ?)", new String[]{"id"});
            statement.setString(1, feedback.reportId());
            statement.setString(2, feedback.rating().toUpperCase());
            statement.setString(3, normalizeComment(feedback.comment()));
            return statement;
        }, holder);
        return new FeedbackReceipt(holder.getKey().longValue(), "Feedback recorded.");
    }

    private String normalizeComment(String comment) { return comment == null || comment.isBlank() ? null : comment.trim(); }
    public record Feedback(@NotBlank @Size(max=80) String reportId,
                           @NotBlank @Pattern(regexp="(?i)UP|DOWN") String rating,
                           @Size(max=500) String comment) { }
    public record FeedbackReceipt(long id, String message) { }
}
