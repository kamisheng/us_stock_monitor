package com.kami.stock_web.entity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Data
@ConfigurationProperties(prefix = "telegram")
@Component
public class TelegramProperties {
    private String botToken;
    private String chatId;
    private String proxyHost;
    private int proxyPort;
    private Set<Long> allowedUserIds = Set.of();   // "7646425912" 逗号分隔会自动绑定成 Set<Long>
    private long chatTimeoutMs = 60_000;
}