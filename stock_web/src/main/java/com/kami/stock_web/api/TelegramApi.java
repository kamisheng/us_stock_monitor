package com.kami.stock_web.api;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

/**
 * Telegram 消息发送器（只发不收）。
 * <p>
 * 只需要 {@code telegrambots-client}，不要引 springboot-longpolling-starter ——
 * 那个是收指令用的，而且 token 配错会让整个 Spring 上下文启动失败。
 * <p>
 * 配置示例（application-dev.yaml）：
 * <pre>
 * telegram:
 *   enabled: true
 *   bot-token: 123456789:AA...
 *   chat-id: "123456789"
 * </pre>
 */
@Slf4j
@Component
public class TelegramApi {

    /** Telegram 单条消息上限 4096 字符 */
    private static final int MAX_LENGTH = 4096;

    private final TelegramClient client;
    private final String chatId;
    private final boolean available;

    public TelegramApi(@Value("${telegram.bot-token:}") String botToken,
                         @Value("${telegram.chat-id:}") String chatId,
                         @Value("${telegram.enabled:true}") boolean enabled,
                         @Value("${telegram.proxy-host:}") String proxyHost,
                         @Value("${telegram.proxy-port:0}") int proxyPort) {
        this.chatId = chatId;
        this.available = enabled && StringUtils.hasText(botToken) && StringUtils.hasText(chatId);

        if (!this.available) {
            this.client = null;
            log.warn("Telegram 未启用：请配置 telegram.bot-token 与 telegram.chat-id（或把 telegram.enabled 设为 false）");
            return;
        }

        OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(20));
        // 显式走 HTTP 代理，不依赖系统 TUN，换节点后立即生效
        if (StringUtils.hasText(proxyHost) && proxyPort > 0) {
            httpClient.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        }
        // OkHttpClient 是线程安全的，构造一次全局复用
        this.client = new OkHttpTelegramClient(httpClient.build(), botToken);
        log.info("Telegram 已启用，chatId={}，代理={}", chatId,
                StringUtils.hasText(proxyHost) && proxyPort > 0 ? proxyHost + ":" + proxyPort : "无");
    }

    /**
     * 发送纯文本消息到配置里的 chatId。
     * <p>
     * 通知失败只记日志并返回 false，不抛异常、不影响调用方主流程。
     *
     * @param text 消息内容，为空则跳过；超过 4096 字符会自动截断
     */
    public void send(String text) {
        this.send(this.chatId, text);
    }

    /**
     * 发送纯文本消息到指定 chatId。
     *
     * @param targetChatId 目标会话 id，为空时用配置里的 chatId
     * @param text         消息内容
     */
    public void send(String targetChatId, String text) {
        if (!available || !StringUtils.hasText(text)) {
            return;
        }
        String chat = StringUtils.hasText(targetChatId) ? targetChatId : this.chatId;
        if (!StringUtils.hasText(chat)) {
            log.warn("Telegram 发送失败：chatId 为空");
            return;
        }
        try {
            client.execute(SendMessage.builder()
                    .chatId(chat)
                    .text(truncate(text))
                    .build());
        } catch (Exception e) {
            // 401=token 错，429=发太快，网络异常也走这里
            log.warn("Telegram 发送失败: {}", e.toString());
        }
    }

    private static String truncate(String text) {
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH - 3) + "...";
    }
}
