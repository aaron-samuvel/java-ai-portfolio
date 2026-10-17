package com.aaron.toolbridge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

@Configuration
public class SqlReadOnlyConfiguration {
    @Bean(name = "toolbridgeAdminJdbcTemplate")
    @Primary
    @DependsOnDatabaseInitialization
    JdbcTemplate toolbridgeAdminJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "toolbridgeReadOnlyUserInitializer")
    Object initializeReadOnlyUser(@org.springframework.beans.factory.annotation.Qualifier("toolbridgeAdminJdbcTemplate") JdbcTemplate applicationJdbc,
                                  @Value("${TOOLBRIDGE_SQL_READER_PASSWORD:local-read-only-demo-password}") String readerPassword) {
        if (readerPassword.length() < 16 || readerPassword.length() > 128
                || readerPassword.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("ToolBridge SQL reader password must contain 16–128 printable characters");
        }
        String escapedPassword = readerPassword.replace("'", "''");
        applicationJdbc.execute("CREATE USER IF NOT EXISTS TOOL_READER PASSWORD '" + escapedPassword + "'");
        applicationJdbc.execute("GRANT SELECT ON sample_sales TO TOOL_READER");
        return Boolean.TRUE;
    }

    @Bean(name = "toolbridgeReadOnlyJdbcTemplate")
    @DependsOn("toolbridgeReadOnlyUserInitializer")
    JdbcTemplate toolbridgeReadOnlyJdbcTemplate(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${TOOLBRIDGE_SQL_READER_PASSWORD:local-read-only-demo-password}") String readerPassword) {
        DriverManagerDataSource readOnlyDataSource = new DriverManagerDataSource();
        readOnlyDataSource.setUrl(jdbcUrl);
        readOnlyDataSource.setUsername("TOOL_READER");
        readOnlyDataSource.setPassword(readerPassword);
        return new JdbcTemplate(readOnlyDataSource);
    }
}
