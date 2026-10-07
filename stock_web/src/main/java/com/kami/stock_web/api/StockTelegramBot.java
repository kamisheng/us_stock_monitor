package com.kami.stock_web.api;

import com.kami.stock_web.entity.TelegramProperties;
import com.kami.stock_web.service.ChatService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@Slf4j
public class StockTelegramBot implements SpringLongPollingBot {

    @Resource
    private TelegramProperties props;
    @Resource
    private ChatService chatService;
    @Resource
    private TelegramApi telegramApi;

    @Override public String getBotToken() { return props.getBotToken(); }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return updates -> updates.forEach(this::handle);       // ← 每个 update 走这里
    }

    private void handle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;
        var msg  = update.getMessage();
        long chatId  = msg.getChatId();
        long userId  = msg.getFrom().getId();
        boolean isPrivate = "private".equals(msg.getChat().getType());

        // 白名单：校验 user id
        if (isPrivate && !props.getAllowedUserIds().contains(userId)) {
            log.warn("拒绝未授权私聊 userId={}", userId);
            telegramApi.send(String.valueOf(chatId), "抱歉，你无权使用本机器人。");
            return;
        }

        try {
            // 会话隔离键：私聊天然一人一会话；群里想按人分就用 g:chatId:u:userId
            String conversationId = isPrivate ? "u:" + userId : "g:" + chatId + ":u:" + userId;
            String answer = chatService.chat(conversationId, msg.getText());
            telegramApi.send(String.valueOf(chatId), answer);   //  回到原会话
        } catch (Exception e) {
            log.warn("处理消息失败: {}", e.toString());
            telegramApi.send(String.valueOf(chatId), "服务暂时不可用，请稍后再试。");
        }
    }
}