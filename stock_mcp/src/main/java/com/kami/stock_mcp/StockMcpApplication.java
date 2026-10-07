package com.kami.stock_mcp;

import com.kami.stock_mcp.tool.DateTool;
import com.kami.stock_mcp.tool.EmailTool;
import com.kami.stock_mcp.tool.StockTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class StockMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockMcpApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider registerMCPTool(DateTool dateTool,
                                                EmailTool emailTool,
                                                StockTool stockTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(dateTool, emailTool, stockTool)
                .build();
    }
}
