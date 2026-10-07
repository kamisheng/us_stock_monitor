package com.kami.stock_mcp.utils;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;

/**
 * stocktitan 直播列表页爬虫。
 * <p>
 * 对方对该页面的限额是 {@code ratelimit-policy: 10;w=300}，即 <b>每 300 秒最多 10 次请求</b>，
 * 超限直接返回 429。因此这里做了两件事：
 * <ol>
 *     <li><b>一次请求解析全页</b>：该页面一次就带 50 条新闻的标签，调用方应整轮只调用一次
 *         {@link #getTagsByTitle()}，再按标题查表，而不是每条新闻各请求一次；</li>
 *     <li><b>缓存 + 额度保护</b>：结果缓存 5 分钟（与对方 300 秒的限流窗口对齐），
 *         并参考响应头 {@code ratelimit-remaining} / {@code ratelimit-reset}，
 *         额度见底或请求失败时沿用上一次的缓存，绝不抛异常打断整轮任务；</li>
 *     <li><b>最小重试间隔</b>：失败时缓存时间不会被刷新，若不加护栏，调度频率有多快
 *         就会重试多快；因此无论成功还是失败，两次真正的抓取尝试之间至少间隔
 *         {@link #MIN_RETRY_INTERVAL_MS}。</li>
 * </ol>
 */
@Slf4j
public class StockTitanCrawler {

    private static final String URL = "https://www.stocktitan.net/news/live.html";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    private static final int TIMEOUT_MS = 10_000;

    /** 缓存有效期：5 分钟，与对方 300 秒限流窗口对齐 */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    /**
     * 两次真正抓取尝试之间的最小间隔。
     * <p>
     * 抓取失败（超时、连接异常等拿不到限流响应头的情况）时缓存不会刷新，
     * 没有这个护栏就会跟着调度频率疯狂重试，因此成败都按此间隔节流。
     */
    private static final long MIN_RETRY_INTERVAL_MS = 60 * 1000L;

    private static final Object LOCK = new Object();

    /** 标题 -> 标签列表，最近一次成功抓取的结果 */
    private static volatile Map<String, List<String>> cachedTags = Collections.emptyMap();

    /** 上次成功抓取的时间戳，0 表示从未成功抓取过 */
    private static volatile long cachedAt = 0L;

    /** 上次发起抓取尝试的时间戳（成功失败都算），0 表示从未尝试过 */
    private static volatile long lastAttemptAt = 0L;

    /** 对方响应头 ratelimit-remaining 的值，未获取过时为 Integer.MAX_VALUE */
    private static volatile int quotaRemaining = Integer.MAX_VALUE;

    /** 额度重置时刻（毫秒时间戳） */
    private static volatile long quotaResetAt = 0L;

    private StockTitanCrawler() {
    }

    /**
     * 获取「新闻标题 -> 标签」映射。
     * <p>
     * 整轮任务只需调用一次；命中缓存或额度不足时不会真正发起 HTTP 请求，
     * 任何时候都不会抛异常（失败时返回上一次的缓存，可能为空 Map）。
     */
    public static Map<String, List<String>> getTagsByTitle() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();

            // 1. 缓存未过期，直接复用
            if (cachedAt > 0 && now - cachedAt < CACHE_TTL_MS) {
                return cachedTags;
            }

            // 2. 额度已见底且尚未重置，先不自找 429
            if (quotaRemaining <= 1 && now < quotaResetAt) {
                log.warn("live.html 额度剩余 {}，{} 秒后重置，本轮沿用缓存({} 条)",
                        quotaRemaining, (quotaResetAt - now) / 1000, cachedTags.size());
                return cachedTags;
            }

            // 3. 距上次尝试不足最小间隔，本轮不抓（失败时缓存不会刷新，靠这里节流）
            if (lastAttemptAt > 0 && now - lastAttemptAt < MIN_RETRY_INTERVAL_MS) {
                log.debug("距上次抓取尝试仅 {} 秒（< {} 秒），本轮沿用缓存({} 条)",
                        (now - lastAttemptAt) / 1000, MIN_RETRY_INTERVAL_MS / 1000, cachedTags.size());
                return cachedTags;
            }

            // 4. 真正抓取一次，成败都记录尝试时刻
            lastAttemptAt = now;
            try {
                Connection.Response response = Jsoup.connect(URL)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .ignoreHttpErrors(true)
                        .execute();

                updateQuota(response, now);

                if (response.statusCode() != 200) {
                    // 429 会走到这里：只告警 + 降级，不抛异常
                    log.warn("抓取 live.html 失败，HTTP {}（额度剩余 {}，{} 秒后重置），本轮沿用缓存({} 条)",
                            response.statusCode(), quotaRemaining,
                            Math.max(0, (quotaResetAt - now) / 1000), cachedTags.size());
                    return cachedTags;
                }

                Map<String, List<String>> tags = extractTags(response.parse());
                cachedTags = tags;
                cachedAt = now;
                log.info("live.html 抓取成功，解析出 {} 条标题的标签", tags.size());
                return tags;

            } catch (Exception e) {
                log.warn("抓取 live.html 异常: {}，本轮沿用缓存({} 条)", e.toString(), cachedTags.size());
                return cachedTags;
            }
        }
    }

    /**
     * 根据新闻标题获取标签
     *
     * @param title 新闻标题（不含 "| XXX Stock News" 后缀）
     * @return 标签列表，未匹配到时返回空列表
     */
    public static List<String> getStockTags(String title) {
        List<String> tags = getTagsByTitle().get(title);
        return tags == null ? Collections.emptyList() : tags;
    }

    /**
     * 解析整页，得到「标题 -> 标签」映射
     */
    private static Map<String, List<String>> extractTags(Document doc) {
        Map<String, List<String>> result = new LinkedHashMap<>();

        // 1. 遍历所有新闻标题节点
        Elements titleElements = doc.select("a.feed-link");

        for (Element titleEl : titleElements) {
            // 2. 向上找到整个 news-row
            Element newsRow = titleEl.closest("div.news-row");
            if (newsRow == null) {
                continue;
            }

            // 3. 在该 news-row 中找 tags
            List<String> tags = new ArrayList<>();
            for (Element tag : newsRow.select("div[name=tags] span.badge")) {
                tags.add(tag.text().trim());
            }

            result.put(titleEl.text().trim(), Collections.unmodifiableList(tags));
        }

        return Collections.unmodifiableMap(result);
    }

    /**
     * 记录对方返回的限流信息
     */
    private static void updateQuota(Connection.Response response, long now) {
        String remaining = response.header("ratelimit-remaining");
        if (remaining != null) {
            try {
                quotaRemaining = Integer.parseInt(remaining.trim());
            } catch (NumberFormatException ignored) {
                // 响应头格式异常时忽略
            }
        }

        String reset = response.header("ratelimit-reset");
        if (reset != null) {
            try {
                // ratelimit-reset 是「距离重置还有多少秒」
                quotaResetAt = now + Long.parseLong(reset.trim()) * 1000L;
            } catch (NumberFormatException ignored) {
                // 响应头格式异常时忽略
            }
        }
    }
}
