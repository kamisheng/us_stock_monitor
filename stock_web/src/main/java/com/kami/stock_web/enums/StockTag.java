package com.kami.stock_web.enums;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public enum StockTag {

    AI("AI", "🤖 AI人工智能"),
    PRIVATE_PLACEMENT("private placement", "💰 私募"),
    MANAGEMENT("management", "👔 公司管理团队"),
    HIGH_SHORT("high short", "📉 短期"),
    ACQUISITION("acquisition", "🏢 收购"),
    MERGER("merger", "🤝 合并"),
    LOW_FLOAT("low float", "⚖️ 低浮动"),
    PENNY_STOCK("penny stock", "💵 低价股"),
    EARNINGS("earnings", "📊 财报盈利"),
    REVENUE("revenue", "💹 收入"),
    GUIDANCE("guidance", "📝 业绩预期"),
    DIVIDEND("dividend", "💸 分红"),
    DIVIDENDS("dividends", "💸 分红"),
    BUYBACK("buyback", "🔄 回购"),
    UPGRADE("upgrade", "⬆️ 上调评级"),
    DOWNGRADE("downgrade", "⬇️ 下调评级"),
    FDA("FDA", "🏥 美国食品药品管理局"),
    PARTNERSHIP("partnership", "🤝 战略合作"),
    FINANCING("financing", "💳 融资"),
    BANKRUPTCY("bankruptcy", "💀 破产"),
    LAWSUIT("lawsuit", "⚖️ 诉讼"),
    INSIDER_TRADING("insider trading", "🔒 内幕交易"),
    VOLATILITY("volatility", "🌪️ 波动性"),
    SENTIMENT("sentiment", "📈 市场情绪"),
    IMPACT("impact", "💥 影响"),
    IPO("IPO", "🚀 首次公开募股"),
    ETF("ETF", "📊 交易型基金"),
    CONFERENCES("conferences", "🏛️ 投资者会议"),
    CLINICAL_TRIAL("clinical trial", "🧪 临床试验"),
    STOCK_SPLIT("stock split", "✂️ 股票拆分"),
    OFFERING("offering", "📢 发行");

    private final String key;
    private final String description;

    StockTag(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public static StockTag fromKey(String key) {
        for (StockTag tag : values()) {
            if (tag.key.equalsIgnoreCase(key)) {
                return tag;
            }
        }
        return null;
    }

    public static String getTagValue(String key) {
        for (StockTag tag : values()) {
            if (tag.key.equalsIgnoreCase(key)) {
                return tag.description;
            }
        }
        return null;
    }

    /**
     * 把一组标签逐个翻译成中文说明，再用 ", " 拼接。
     * <p>
     * 注意不要先拼成整串再调用 {@link #getTagValue(String)}：那样会拿
     * "private placement, penny stock" 这样的组合去和单个 key 做全等比较，
     * 永远匹配不上而返回 null。枚举里没收录的标签保留原文，避免信息丢失。
     *
     * @param keys 原始标签，可为 null 或空
     * @return 中文标签串；没有标签时返回空串（不返回 null）
     */
    public static String getTagValues(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return "";
        }
        return keys.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .map(key -> {
                    StockTag tag = fromKey(key);
                    return tag == null ? key : tag.getDescription();
                })
                .collect(Collectors.joining(", "));
    }

}
