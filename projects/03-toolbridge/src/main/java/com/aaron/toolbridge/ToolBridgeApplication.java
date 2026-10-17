package com.aaron.toolbridge;

import com.aaron.toolbridge.tools.CatalogTools;
import com.aaron.toolbridge.tools.MavenCentralTools;
import com.aaron.toolbridge.tools.SqlAnalyticsTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ToolBridgeApplication {
    @Bean
    ToolCallbackProvider catalogToolCallbacks(CatalogTools catalogTools, MavenCentralTools mavenCentralTools,
                                              SqlAnalyticsTools sqlAnalyticsTools) {
        return MethodToolCallbackProvider.builder().toolObjects(catalogTools, mavenCentralTools, sqlAnalyticsTools).build();
    }

    public static void main(String[] args) { SpringApplication.run(ToolBridgeApplication.class, args); }
}
