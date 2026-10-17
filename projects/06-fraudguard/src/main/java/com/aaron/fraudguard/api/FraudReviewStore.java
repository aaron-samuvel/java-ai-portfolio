package com.aaron.fraudguard.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class FraudReviewStore {
    private final JdbcTemplate jdbc;
    public FraudReviewStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long record(ScoringController.Transaction tx, int riskScore, String band) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("insert into fraud_decision(amount,recent_transactions,account_age_days,country_mismatch,risk_score,risk_band) values (?,?,?,?,?,?)", new String[]{"id"});
            ps.setDouble(1, tx.amount()); ps.setInt(2, tx.recentTransactions()); ps.setInt(3, tx.accountAgeDays());
            ps.setBoolean(4, tx.countryMismatch()); ps.setInt(5, riskScore); ps.setString(6, band); return ps;
        }, key);
        return key.getKey().longValue();
    }

    public List<Decision> openReviewQueue(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return jdbc.query("select id, amount, risk_score, risk_band, created_at from fraud_decision where analyst_label is null and risk_band in ('REVIEW','HIGH') order by risk_score desc, created_at asc limit ?",
                (rs, row) -> new Decision(rs.getLong("id"), rs.getDouble("amount"), rs.getInt("risk_score"), rs.getString("risk_band"), rs.getTimestamp("created_at").toInstant()), limit);
    }

    public boolean label(long id, String label) {
        return jdbc.update("update fraud_decision set analyst_label=?, labeled_at=current_timestamp where id=? and analyst_label is null", label, id) == 1;
    }

    public record Decision(long id, double amount, int riskScore, String band, Instant createdAt) { }
}
