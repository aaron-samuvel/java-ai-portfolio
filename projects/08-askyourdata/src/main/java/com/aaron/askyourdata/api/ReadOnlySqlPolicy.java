package com.aaron.askyourdata.api;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.Locale;
import java.util.Set;

/** A strict local demo policy: only the reviewed report statements may reach JDBC. */
public final class ReadOnlySqlPolicy {
    private final Set<String> approvedSql;

    public ReadOnlySqlPolicy(Set<String> approvedSql) {
        this.approvedSql = approvedSql.stream().map(ReadOnlySqlPolicy::normalize).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean allows(String sql) {
        if (sql == null || !approvedSql.contains(normalize(sql))) return false;
        try {
            var statement = CCJSqlParserUtil.parse(sql);
            if (!(statement instanceof Select)) return false;
            var tables = TablesNamesFinder.findTables(sql).stream()
                    .map(name -> name.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
            return tables.equals(Set.of("sample_sales"));
        } catch (Exception invalidSql) {
            return false;
        }
    }

    private static String normalize(String sql) { return sql.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
}
