package com.kami.stock_web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.kami.stock_web.api.TelegramApi;
import com.kami.stock_web.entity.StockCounts;
import com.kami.stock_web.entity.USStockMsg;
import com.kami.stock_web.entity.USStockMsgZh;
import com.kami.stock_web.entity.USStockRss;
import com.kami.stock_web.enums.StockTag;
import com.kami.stock_web.service.RssService;
import com.kami.stock_web.service.StockService;
import com.kami.stock_web.utils.StockTitanCrawler;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static com.kami.stock_web.utils.GMTDateConverter.*;

@Service
@Slf4j
public class RssServiceImpl implements RssService {

    @Resource
    private StockService stockService;
    @Resource
    private TelegramApi telegramApi;

    public static final String RSS_URL = "https://www.stocktitan.net/rss";

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0 Safari/537.36";

    /** 一条消息最多列几条新闻，其余只报数量 */
    private static final int MAX_ITEMS_PER_MESSAGE = 15;

    /** 英文消息：tags 用英文原文，时间用纽约时间 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void displayRss() throws Exception {
        this.syncAndNotify(false);
    }

    /** 中文消息：tags 用中文，标题用 titleZh，时间用北京时间 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void displayRssZh() throws Exception {
        this.syncAndNotify(true);
    }

    /**
     * 抓取 → 判重 → 入库 → 组装消息 → 发送。
     * <p>
     * 抓取和入库一轮只做一次（RSS 与列表页都有限流，且 link 是唯一键），
     * 语言只影响最后一步发出去的消息内容。
     *
     * @param zh true 发中文（USStockMsgZh），false 发英文（USStockMsg）
     */
    private void syncAndNotify(boolean zh) throws Exception {
        List<USStockRss> all = this.collectEntries();

        List<USStockRss> newOnes = this.filterNewOnes(all);
        if (newOnes.isEmpty()) {
            log.info("本轮 RSS {} 条，均已入库，无新增", all.size());
            return;
        }

        // 先入库，再算 counts，这样本轮的数据也会被算进去
        stockService.saveBatch(newOnes, 500);
        log.info("本轮 RSS {} 条，新增入库 {} 条", all.size(), newOnes.size());

        if (zh) {
            this.sendChinese(this.toChineseMessages(newOnes));
        } else {
            this.sendEnglish(this.toEnglishMessages(newOnes));
        }
    }

    /**
     * 抓取 RSS + 标签页，组装成待入库的实体列表。
     *
     * 两个标签列都会填：tags 存中文、tags_en 存英文原文，与发哪种语言的消息无关。
     */
    private List<USStockRss> collectEntries() throws Exception {
        List<SyndEntry> syndEntries = this.fetchRssReed(RSS_URL);

        // 直播列表页限额为 10 次 / 300 秒，整轮只抓一次，循环内按标题查表
        Map<String, List<String>> tagsByTitle = StockTitanCrawler.getTagsByTitle();

        List<USStockRss> result = new ArrayList<>(syndEntries.size());
        for (SyndEntry syndEntry : syndEntries) {
            String title = this.getStockTitle(syndEntry.getTitle());
            List<String> tags = tagsByTitle.get(title);
            if (tags == null) {
                tags = List.of();
            }

            Date date = syndEntry.getPublishedDate();
            LocalDateTime gmt = convertGmt(date);

            USStockRss usStockRss = new USStockRss();
            usStockRss.setLink(syndEntry.getLink());
            usStockRss.setTitle(title);
            usStockRss.setStockCode(this.getStockCode(syndEntry.getTitle()));
            usStockRss.setPubDateGmt(gmt);
            usStockRss.setPubDateBj(convertGmtToBeijing(date));
            usStockRss.setTags(StockTag.getTagValues(tags));      // 中文标签
            usStockRss.setTagsEn(String.join(", ", tags));        // 英文原文标签
            usStockRss.setTitleZh("");
            result.add(usStockRss);
        }
        return result;
    }

    /**
     * 按 link 判重，返回库里还不存在的那些。
     * <p>
     * 不能用主键判重：id 是插入时才由 MyBatis-Plus 生成的，插入前恒为 null。
     */
    private List<USStockRss> filterNewOnes(List<USStockRss> entries) {
        Map<String, USStockRss> distinct = new LinkedHashMap<>();
        for (USStockRss entry : entries) {
            if (StringUtils.hasText(entry.getLink())) {
                distinct.putIfAbsent(entry.getLink(), entry);
            }
        }
        if (distinct.isEmpty()) {
            return List.of();
        }

        // 一次查询取回已存在的 link，避免逐条 getById 的 N+1
        Set<String> existingLinks = stockService.lambdaQuery()
                .select(USStockRss::getLink)
                .in(USStockRss::getLink, distinct.keySet())
                .list()
                .stream()
                .map(USStockRss::getLink)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return distinct.values().stream()
                .filter(entry -> !existingLinks.contains(entry.getLink()))
                .toList();
    }

    /** 组装英文 DTO：标题用原始英文 title，时间用纽约时间，标签用英文原文 */
    private List<USStockMsg> toEnglishMessages(List<USStockRss> entries) {
        List<USStockMsg> list = new ArrayList<>(entries.size());
        for (USStockRss entry : entries) {
            USStockMsg msg = new USStockMsg();
            BeanUtil.copyProperties(entry, msg);          // stockCode/title/link 同名，能拷过来
            msg.setTags(entry.getTagsEn());
            msg.setPubDateNy(formatNewYork(entry.getPubDateGmt()));

            StockCounts counts = stockService.getStockCount(entry.getStockCode(), entry.getPubDateGmt());
            msg.setCounts24Hour(counts.getCounts24Hour());
            msg.setCounts3Day(counts.getCounts3Day());
            msg.setCounts1Week(counts.getCounts1Week());
            list.add(msg);
        }
        return list;
    }

    /** 组装中文 DTO：标题用 titleZh，时间用北京时间，标签用中文 */
    private List<USStockMsgZh> toChineseMessages(List<USStockRss> entries) {
        List<USStockMsgZh> list = new ArrayList<>(entries.size());
        for (USStockRss entry : entries) {
            USStockMsgZh msg = new USStockMsgZh();
            BeanUtil.copyProperties(entry, msg);
            // title → titleZh 名字不同，BeanUtil 拷不过去，必须手动设置
            msg.setTitleZh(StringUtils.hasText(entry.getTitleZh()) ? entry.getTitleZh() : entry.getTitle());
            msg.setTags(entry.getTags());
            msg.setPubDateBj(formatBeijing(entry.getPubDateGmt()));

            StockCounts counts = stockService.getStockCount(entry.getStockCode(), entry.getPubDateGmt());
            msg.setCounts24Hour(counts.getCounts24Hour());
            msg.setCounts3Day(counts.getCounts3Day());
            msg.setCounts1Week(counts.getCounts1Week());
            list.add(msg);
        }
        return list;
    }

    private void sendEnglish(List<USStockMsg> messages) {
        List<String> blocks = messages.stream()
                .limit(MAX_ITEMS_PER_MESSAGE)
                .map(m ->"📈 [" + m.getStockCode() + "] " + m.getTitle()
                        + "\n🏷️ Tags: " + orDash(m.getTags())
                        + "\n🔔 24H: " + m.getCounts24Hour()
                        + " | 3D: " + m.getCounts3Day()
                        + " | 1W: " + m.getCounts1Week()
                        + "\n🕒 NY Time: " + m.getPubDateNy()
                        + "\n🔗 " + m.getLink())
                .toList();
        telegramApi.send(buildText("📈 " + messages.size() + " new US stock news", messages.size(), blocks));
    }

    private void sendChinese(List<USStockMsgZh> messages) {
        List<String> blocks = messages.stream()
                .limit(MAX_ITEMS_PER_MESSAGE)
                .map(m ->"📈 [" + m.getStockCode() + "] " + m.getTitleZh()
                        + "\n🏷️ 标签：" + orDash(m.getTags())
                        + "\n🔔 异动：24H " + m.getCounts24Hour() + "次"
                        + " | 3天 " + m.getCounts3Day() + "次"
                        + " | 1周 " + m.getCounts1Week() + "次"
                        + "\n🕒 北京时间：" + m.getPubDateBj()
                        + "\n🔗 " + m.getLink())
                .toList();
        telegramApi.send(buildText("📈 新增 " + messages.size() + " 条美股消息", messages.size(), blocks));
    }

    private static String buildText(String headline, int total, List<String> blocks) {
        StringBuilder sb = new StringBuilder(headline).append("\n\n").append(String.join("\n\n", blocks));
        if (total > blocks.size()) {
            sb.append("\n\n…另有 ").append(total - blocks.size()).append(" 条");
        }
        return sb.toString();   // 超长由 TelegramApi.send 内部按 4096 截断
    }

    private static String orDash(String text) {
        return StringUtils.hasText(text) ? text : "-";
    }

    @Override
    public List<SyndEntry> fetchRssReed(String rssUrl) throws Exception {
        byte[] body = this.download(rssUrl);
        // XmlReader 会依据 BOM / XML 声明识别编码，这里传入已解压的原始字节
        SyndFeed syndFeed = new SyndFeedInput().build(new XmlReader(new ByteArrayInputStream(body)));
        return syndFeed.getEntries();
    }

    /**
     * 下载订阅源原始内容。
     * <p>
     * 注意：stocktitan.net 在客户端未发送 {@code Accept-Encoding} 时依然会返回
     * {@code Content-Encoding: gzip}，而 {@link HttpURLConnection} 不会自动解压，
     * 因此必须手动判断并解压，否则 gzip 字节流会被当成 XML 解析，
     * 抛出 {@code Content is not allowed in prolog}（前言中不允许有内容）。
     */
    private byte[] download(String rssUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(rssUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
        // 声明支持 gzip，使服务端行为可预期；仍按响应头实际取值解压
        conn.setRequestProperty("Accept-Encoding", "gzip");
        try {
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException(
                        "拉取 RSS 失败，HTTP " + status + " (" + conn.getResponseMessage() + "): " + rssUrl
                                + ", ratelimit-remaining=" + conn.getHeaderField("ratelimit-remaining"));
            }
            InputStream raw = conn.getInputStream();
            String encoding = conn.getHeaderField("Content-Encoding");
            try (InputStream in = isGzip(encoding) ? new GZIPInputStream(raw) : raw) {
                return in.readAllBytes();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static boolean isGzip(String contentEncoding) {
        return contentEncoding != null && contentEncoding.toLowerCase().contains("gzip");
    }

    /**
     * 获取标题
     * @param title
     * @return
     */
    private String getStockTitle(String title) {
        String[] split = title.split("\\|");
        return split[0].trim();
    }

    /**
     * 获取股票代码
     * @param title
     * @return
     */
    private String getStockCode(String title) {
        String[] arr = title.split("\\|");
        String stockCodeStr = arr[arr.length - 1];
        String[] code = stockCodeStr.split("Stock News");
        return code[0].trim();
    }
}
