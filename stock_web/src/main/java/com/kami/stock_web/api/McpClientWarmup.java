package com.kami.stock_web.api;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Slf4j
public class McpClientWarmup {
    @Bean
    ApplicationRunner mcpWarmup(ObjectProvider<List<McpSyncClient>> clients) {
        return args -> clients.ifAvailable(list -> list.forEach(c -> {
            try {
                if (!c.isInitialized()) c.initialize();
                log.info("MCP 已连接: {}", c.getServerInfo());
            } catch (Exception e) {
                log.warn("MCP 暂不可用（工具调用会失败，但不影响定时推送）: {}", e.toString());
            }
        }));
    }
}