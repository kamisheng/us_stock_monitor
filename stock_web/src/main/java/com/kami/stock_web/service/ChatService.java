package com.kami.stock_web.service;


import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@Slf4j
public class ChatService {

    private static final String SYSTEM = """
        你是美股异动监控助手。涉及股票数据的问题必须调用工具查询，不要凭记忆回答。
        时间参数一律使用 yyyy-MM-dd HH:mm:ss 格式的北京时间。
        """;


    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder builder,
                       ObjectProvider<ToolCallbackProvider> mcpTools,   // MCP 工具从这里来
                       @Value("${telegram.chat-timeout-ms:60000}") long timeoutMs) {

        ChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();

        ChatClient.Builder b = builder
                .defaultSystem(SYSTEM)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build());

        mcpTools.ifAvailable(t -> {
            b.defaultTools(t);          // 方法签名：defaultToolCallbacks(ToolCallbackProvider...)
            log.info("已挂载 MCP 工具: {}", Arrays.stream(t.getToolCallbacks())
                    .map(c -> c.getToolDefinition().name()).toList());
        });

        this.chatClient = b.build();
    }

    public String chat(String conversationId, String text) {
        return chatClient.prompt()
                .user(text)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))  // 常量名就是这个
                .call()
                .content();
    }
}